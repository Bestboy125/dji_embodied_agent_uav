package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SafetyPolicyTest {
    private final SafetyPolicy policy = new SafetyPolicy(25, 3.0);

    @Test
    public void rejectsMovementOnGround() throws Exception {
        AgentDecision decision = AgentDecision.parse(
                "{\"decision\":\"act\",\"action\":{\"skill\":\"move_relative\","
                        + "\"parameters\":{\"direction\":\"forward\",\"distance_m\":1}}}"
        );
        String reason = policy.rejectReason(decision, skill("move_relative", true), observation(true, false, 80));
        assertTrue(reason.contains("不在空中"));
    }

    @Test
    public void allowsBoundedMovementInFlight() throws Exception {
        AgentDecision decision = AgentDecision.parse(
                "{\"decision\":\"act\",\"action\":{\"skill\":\"move_relative\","
                        + "\"parameters\":{\"direction\":\"forward\",\"distance_m\":2}}}"
        );
        assertNull(policy.rejectReason(decision, skill("move_relative", true), observation(true, true, 80)));
    }

    @Test
    public void rejectsCriticalActionWithLowBattery() throws Exception {
        AgentDecision decision = AgentDecision.parse(
                "{\"decision\":\"act\",\"action\":{\"skill\":\"takeoff\",\"parameters\":{}}}"
        );
        String reason = policy.rejectReason(decision, skill("takeoff", true), observation(true, false, 10));
        assertTrue(reason.contains("电量"));
    }

    private static AgentObservation observation(boolean connected, boolean flying, int battery) throws Exception {
        JSONObject state = new JSONObject();
        state.put("connected", connected);
        state.put("is_flying", flying);
        state.put("battery_percent", battery);
        return new AgentObservation(state, null);
    }

    private static AgentSkill skill(String name, boolean critical) {
        return new AgentSkill() {
            @Override public String name() { return name; }
            @Override public String description() { return "test"; }
            @Override public JSONObject inputSchema() { return new JSONObject(); }
            @Override public boolean isFlightCritical() { return critical; }
            @Override public SkillResult execute(JSONObject parameters) { return SkillResult.success("ok"); }
        };
    }
}
