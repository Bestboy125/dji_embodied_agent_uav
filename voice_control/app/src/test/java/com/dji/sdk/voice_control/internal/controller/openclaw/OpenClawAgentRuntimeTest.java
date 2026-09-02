package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OpenClawAgentRuntimeTest {
    @Test
    public void completesOnlyOnExplicitDoneDecision() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        SkillRegistry registry = new SkillRegistry();
        OpenClawAgentRuntime runtime = new OpenClawAgentRuntime(
                registry,
                (goal, iteration) -> new AgentObservation(new JSONObject().put("connected", true), null),
                (prompt, image) -> "{\"decision\":\"done\",\"goal_progress\":\"完成\"}",
                new SafetyPolicy(25, 3),
                new NoOpListener() {
                    @Override public void onFinished(boolean ok, String summary) {
                        success.set(ok);
                        finished.countDown();
                    }
                },
                () -> { }, 3, 0
        );

        assertTrue(runtime.start("test goal"));
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        assertTrue(success.get());
        assertFalse(runtime.isRunning());
        runtime.shutdown();
    }

    private static class NoOpListener implements OpenClawAgentRuntime.Listener {
        @Override public void onState(String state, String detail) { }
        @Override public void onIteration(int iteration, AgentDecision decision) { }
        @Override public void onSkillResult(int iteration, String skill, SkillResult result) { }
        @Override public void onFinished(boolean success, String summary) { }
    }
}
