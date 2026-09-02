package com.dji.sdk.voice_control.internal.controller.openclaw;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.djitool.gimbal.gimbalControl;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.interfaces.ControlActivityCallback;

import org.json.JSONObject;

import java.io.File;

import dji.common.flightcontroller.LocationCoordinate3D;

/** Android/DJI adapter for the platform-neutral OpenClawAgentRuntime. */
public final class DjiOpenClawAgent {
    private static final String TAG = "DjiOpenClawAgent";
    private static final String PREFS = "openclaw_mission_memory";
    private static final String LAST_SUMMARY = "last_summary";

    private final CommandInterpreter commandInterpreter;
    private final ControlActivityCallback callback;
    private final gimbalControl gimbal;
    private final SharedPreferences preferences;
    private final OpenClawAgentRuntime runtime;

    public DjiOpenClawAgent(CommandInterpreter commandInterpreter,
                            ControlActivityCallback callback,
                            gimbalControl gimbal) {
        this.commandInterpreter = commandInterpreter;
        this.callback = callback;
        this.gimbal = gimbal;
        this.preferences = callback.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        SkillRegistry registry = buildRegistry();
        runtime = new OpenClawAgentRuntime(
                registry,
                this::observe,
                (prompt, image) -> callback.sendQuestionToGPTSync(prompt, image, false),
                new SafetyPolicy(25, 3.0),
                new UiListener(),
                this::stopMotion,
                30,
                1800
        );
    }

    public boolean start(String goal) {
        boolean started = runtime.start(goal);
        if (!started) {
            callback.addChatMessage(Constant.OWNER_BOT, "OpenClaw Agent 已在运行，或任务目标为空");
        }
        return started;
    }

    public void stop() {
        if (runtime.isRunning()) runtime.stop("操作员请求停止");
        else stopMotion();
    }

    public void shutdown() {
        runtime.shutdown();
    }

    public boolean isRunning() {
        return runtime.isRunning();
    }

    private AgentObservation observe(String goal, int iteration) {
        JSONObject state = new JSONObject();
        try {
            boolean connected = commandInterpreter != null
                    && commandInterpreter.mFlightController != null
                    && commandInterpreter.mFlightController.getState() != null;
            state.put("connected", connected);
            state.put("is_flying", callback.getisFlying());
            state.put("heading_deg", callback.getHeading());
            state.put("altitude_m", callback.gerAltitude());
            state.put("battery_percent", callback.getBatteryPercent());
            state.put("iteration", iteration);
            state.put("previous_mission_summary", preferences.getString(LAST_SUMMARY, "暂无历史任务"));
            LocationCoordinate3D location = callback.getDroneLocation();
            if (location != null) {
                JSONObject position = new JSONObject();
                position.put("latitude", location.getLatitude());
                position.put("longitude", location.getLongitude());
                position.put("altitude", location.getAltitude());
                state.put("position", position);
            }
        } catch (Exception error) {
            Log.w(TAG, "Unable to build full observation", error);
            try { state.put("observation_error", error.getMessage()); } catch (Exception ignored) { }
        }

        File image = callback.CaptureImage();
        try { state.put("camera_frame_available", image != null); } catch (Exception ignored) { }
        return new AgentObservation(state, image);
    }

    private SkillRegistry buildRegistry() {
        SkillRegistry registry = new SkillRegistry();
        registry.register(new SimpleSkill("observe", "观察当前 FPV 画面和飞行状态，不产生运动", false,
                schema(), parameters -> SkillResult.success("已获取最新观测")));
        registry.register(new SimpleSkill("takeoff", "起飞；仅在已连接且位于地面时使用", true,
                schema(), parameters -> {
                    commandInterpreter.mTakeoff();
                    return SkillResult.success("起飞指令已提交，下一轮需观察飞行状态");
                }));
        registry.register(new SimpleSkill("land", "在当前位置降落", true,
                schema(), parameters -> {
                    commandInterpreter.mLand();
                    return SkillResult.success("降落指令已提交");
                }));
        registry.register(new SimpleSkill("hover", "立即停止虚拟摇杆位移并悬停", true,
                schema(), parameters -> {
                    stopMotion();
                    return SkillResult.success("已发送悬停指令");
                }));
        registry.register(new SimpleSkill("move_relative",
                "在机体系向 forward/backward/left/right/up/down 移动 0.5 到 3 米", true,
                schema("direction", "forward|backward|left|right|up|down", "distance_m", "0.5..3.0"),
                this::moveRelative));
        registry.register(new SimpleSkill("rotate", "向 left 或 right 旋转 1 到 180 度", true,
                schema("direction", "left|right", "degrees", "1..180"),
                this::rotate));
        registry.register(new SimpleSkill("gimbal_pitch", "设置云台绝对俯仰角，范围 -90 到 30 度", false,
                schema("pitch_deg", "-90..30"), parameters -> {
                    double pitch = parameters.optDouble("pitch_deg", 0);
                    if (pitch < -90 || pitch > 30) return SkillResult.failure("云台俯仰角超出安全范围");
                    gimbal.pitchGimbalAbsolute((float) pitch);
                    return SkillResult.success("云台俯仰角已设置为 " + pitch + " 度");
                }));
        registry.register(new SimpleSkill("capture_photo", "保存当前 FPV 画面用于任务记录", false,
                schema(), parameters -> {
                    File file = callback.CaptureImage();
                    if (file == null) return SkillResult.failure("当前没有可用 FPV 图像");
                    JSONObject output = new JSONObject();
                    output.put("path", file.getAbsolutePath());
                    return SkillResult.success("图像已保存", output);
                }));
        registry.register(new SimpleSkill("report", "向操作员报告发现、进度或需要人工判断的信息", false,
                schema("message", "string"), parameters -> {
                    String message = parameters.optString("message", "").trim();
                    if (message.isEmpty()) return SkillResult.failure("报告内容不能为空");
                    callback.addChatMessage(Constant.OWNER_BOT, "任务报告：" + message);
                    return SkillResult.success("报告已发送");
                }));
        return registry;
    }

    private SkillResult moveRelative(JSONObject parameters) {
        String direction = parameters.optString("direction", "").toLowerCase();
        double distance = parameters.optDouble("distance_m", 0);
        MyVirtualStickExecutor executor = MyVirtualStickExecutor.getUniqueInstance();
        int roundedDistance = Math.max(1, (int) Math.round(distance));
        switch (direction) {
            case "forward": executor.mGo(301, distance); break;
            case "backward": executor.mGo(302, distance); break;
            case "left": executor.mGo(303, distance); break;
            case "right": executor.mGo(304, distance); break;
            case "up": executor.mUp(roundedDistance); break;
            case "down": executor.mDown(roundedDistance); break;
            default: return SkillResult.failure("未知移动方向: " + direction);
        }
        return SkillResult.success("已提交相对移动: " + direction + " " + distance + "m");
    }

    private SkillResult rotate(JSONObject parameters) {
        String direction = parameters.optString("direction", "").toLowerCase();
        int degrees = (int) Math.round(parameters.optDouble("degrees", 0));
        if (!"left".equals(direction) && !"right".equals(direction)) {
            return SkillResult.failure("旋转方向必须是 left 或 right");
        }
        MyVirtualStickExecutor.getUniqueInstance().mTurn("left".equals(direction) ? 303 : 304, degrees);
        return SkillResult.success("已提交旋转: " + direction + " " + degrees + "°");
    }

    private void stopMotion() {
        try {
            if (commandInterpreter != null && commandInterpreter.mFlightController != null) {
                commandInterpreter.mStop();
            }
        } catch (Exception error) {
            Log.e(TAG, "Emergency stop failed", error);
        }
    }

    private static JSONObject schema(String... pairs) {
        JSONObject schema = new JSONObject();
        try {
            for (int i = 0; i + 1 < pairs.length; i += 2) schema.put(pairs[i], pairs[i + 1]);
        } catch (Exception ignored) { }
        return schema;
    }

    private interface SkillExecutor {
        SkillResult execute(JSONObject parameters) throws Exception;
    }

    private static final class SimpleSkill implements AgentSkill {
        private final String name;
        private final String description;
        private final boolean flightCritical;
        private final JSONObject inputSchema;
        private final SkillExecutor executor;

        private SimpleSkill(String name, String description, boolean flightCritical,
                            JSONObject inputSchema, SkillExecutor executor) {
            this.name = name;
            this.description = description;
            this.flightCritical = flightCritical;
            this.inputSchema = inputSchema;
            this.executor = executor;
        }

        @Override public String name() { return name; }
        @Override public String description() { return description; }
        @Override public JSONObject inputSchema() { return inputSchema; }
        @Override public boolean isFlightCritical() { return flightCritical; }
        @Override public SkillResult execute(JSONObject parameters) throws Exception { return executor.execute(parameters); }
    }

    private final class UiListener implements OpenClawAgentRuntime.Listener {
        @Override
        public void onState(String state, String detail) {
            callback.addChatMessage(Constant.OWNER_BOT_THINK, "[Agent/" + state + "] " + detail);
        }

        @Override
        public void onIteration(int iteration, AgentDecision decision) {
            callback.addChatMessage(Constant.OWNER_BOT_THINK,
                    "第 " + iteration + " 轮：" + decision.getThinking()
                            + (decision.getSkill().isEmpty() ? "" : "\n动作：" + decision.getSkill()));
        }

        @Override
        public void onSkillResult(int iteration, String skill, SkillResult result) {
            callback.addChatMessage(Constant.OWNER_BOT,
                    (result.isSuccess() ? "完成" : "失败") + " [" + skill + "]：" + result.getMessage());
        }

        @Override
        public void onFinished(boolean success, String summary) {
            preferences.edit().putString(LAST_SUMMARY,
                    (success ? "成功: " : "失败: ") + summary).apply();
            callback.addChatMessage(Constant.OWNER_BOT,
                    (success ? "OpenClaw 任务完成：" : "OpenClaw 任务结束：") + summary);
        }
    }
}
