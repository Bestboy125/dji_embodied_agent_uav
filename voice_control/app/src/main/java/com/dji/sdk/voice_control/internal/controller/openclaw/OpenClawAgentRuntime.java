package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenClaw-style closed loop: observe -> decide one skill -> execute -> feed result back.
 * The LLM never talks to DJI MSDK directly; all physical actions cross the registry and policy.
 */
public final class OpenClawAgentRuntime {
    public interface ObservationProvider {
        AgentObservation observe(String goal, int iteration) throws Exception;
    }

    public interface DecisionProvider {
        String decide(String prompt, File image) throws Exception;
    }

    public interface Listener {
        void onState(String state, String detail);
        void onIteration(int iteration, AgentDecision decision);
        void onSkillResult(int iteration, String skill, SkillResult result);
        void onFinished(boolean success, String summary);
    }

    public interface EmergencyStop {
        void stopMotion();
    }

    private final SkillRegistry registry;
    private final ObservationProvider observationProvider;
    private final DecisionProvider decisionProvider;
    private final SafetyPolicy safetyPolicy;
    private final Listener listener;
    private final EmergencyStop emergencyStop;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final int maxIterations;
    private final long settleDelayMs;
    private volatile AgentMemory memory;

    public OpenClawAgentRuntime(SkillRegistry registry,
                                ObservationProvider observationProvider,
                                DecisionProvider decisionProvider,
                                SafetyPolicy safetyPolicy,
                                Listener listener,
                                EmergencyStop emergencyStop,
                                int maxIterations,
                                long settleDelayMs) {
        this.registry = registry;
        this.observationProvider = observationProvider;
        this.decisionProvider = decisionProvider;
        this.safetyPolicy = safetyPolicy;
        this.listener = listener;
        this.emergencyStop = emergencyStop;
        this.maxIterations = maxIterations;
        this.settleDelayMs = settleDelayMs;
    }

    public boolean start(String goal) {
        if (closed.get() || goal == null || goal.trim().isEmpty()
                || !running.compareAndSet(false, true)) return false;
        stopRequested.set(false);
        memory = new AgentMemory(8);
        executor.execute(() -> runLoop(goal.trim()));
        return true;
    }

    public void stop(String reason) {
        if (closed.get()) return;
        stopRequested.set(true);
        emergencyStop.stopMotion();
        listener.onState("stopping", reason == null ? "操作员请求停止" : reason);
    }

    public boolean isRunning() {
        return running.get();
    }

    public void shutdown() {
        if (!closed.compareAndSet(false, true)) return;
        stopRequested.set(true);
        if (running.get()) emergencyStop.stopMotion();
        running.set(false);
        executor.shutdownNow();
    }

    private void runLoop(String goal) {
        int parseFailures = 0;
        try {
            listener.onState("running", "目标: " + goal);
            for (int iteration = 1; iteration <= maxIterations; iteration++) {
                if (closed.get()) return;
                if (stopRequested.get() || Thread.currentThread().isInterrupted()) {
                    finish(false, "任务已由操作员停止");
                    return;
                }

                AgentObservation observation = observationProvider.observe(goal, iteration);
                String prompt = buildPrompt(goal, iteration, observation);
                AgentDecision decision;
                try {
                    decision = AgentDecision.parse(decisionProvider.decide(prompt, observation.getImage()));
                    parseFailures = 0;
                } catch (Exception error) {
                    parseFailures++;
                    listener.onState("retrying", "决策解析失败 " + parseFailures + "/3: " + error.getMessage());
                    if (parseFailures >= 3) {
                        emergencyStop.stopMotion();
                        finish(false, "LLM 连续三次无法返回合法决策，已安全悬停");
                        return;
                    }
                    continue;
                }

                listener.onIteration(iteration, decision);
                if (decision.getType() == AgentDecision.Type.DONE) {
                    emergencyStop.stopMotion();
                    finish(true, decision.getProgress().isEmpty() ? "目标已完成" : decision.getProgress());
                    return;
                }
                if (decision.getType() == AgentDecision.Type.STUCK) {
                    emergencyStop.stopMotion();
                    finish(false, decision.getProgress().isEmpty() ? "Agent 无法继续，已悬停" : decision.getProgress());
                    return;
                }

                AgentSkill skill = registry.get(decision.getSkill());
                String rejected = safetyPolicy.rejectReason(decision, skill, observation);
                if (rejected == null && memory.consecutiveSkillCount(decision.getSkill()) >= 2
                        && !"observe".equals(decision.getSkill())) {
                    rejected = "同一动作已连续执行两次；请先 observe 并根据反馈调整策略";
                }

                SkillResult result;
                if (rejected != null) {
                    result = SkillResult.failure("安全策略拒绝: " + rejected);
                } else {
                    try {
                        result = skill.execute(decision.getParameters());
                    } catch (Exception error) {
                        result = SkillResult.failure("技能执行异常: " + error.getMessage());
                    }
                }
                memory.record(iteration, decision, result);
                listener.onSkillResult(iteration, decision.getSkill(), result);
                if (settleDelayMs > 0) Thread.sleep(settleDelayMs);
            }
            emergencyStop.stopMotion();
            finish(false, "达到最大迭代次数，已悬停等待人工接管");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            emergencyStop.stopMotion();
            if (!closed.get()) finish(false, "任务线程已中断");
        } catch (Exception error) {
            emergencyStop.stopMotion();
            finish(false, "Agent 运行异常: " + error.getMessage());
        }
    }

    private String buildPrompt(String goal, int iteration, AgentObservation observation) {
        JSONObject context = new JSONObject();
        try {
            context.put("goal", goal);
            context.put("iteration", iteration);
            context.put("observation", observation.getState());
            context.put("recent_history", memory.snapshot());
            context.put("available_skills", registry.catalog());
        } catch (Exception ignored) {
        }
        return "你是 DJI 无人机闭环控制 Agent。严格遵守以下规则：\n"
                + "1. 每轮只能选择一个 available_skills 中的技能；不得声称未实际执行的动作。\n"
                + "2. 执行后必须依据下一轮 observation 和 result 重新规划。\n"
                + "3. 优先保证人员、飞行器和环境安全；不确定时选择 hover 或 stuck。\n"
                + "4. 搜索时在移动动作之间穿插 observe，禁止无反馈地重复同一动作。\n"
                + "5. 只有目标客观完成时才返回 done。\n"
                + "仅输出一个 JSON 对象："
                + "{\"thinking\":\"简短判断\",\"decision\":\"act|done|stuck\","
                + "\"action\":{\"skill\":\"技能名\",\"parameters\":{}},"
                + "\"goal_progress\":\"进度\"}\n\n当前上下文：\n" + context.toString();
    }

    private void finish(boolean success, String summary) {
        running.set(false);
        if (!closed.get()) listener.onFinished(success, summary);
    }
}
