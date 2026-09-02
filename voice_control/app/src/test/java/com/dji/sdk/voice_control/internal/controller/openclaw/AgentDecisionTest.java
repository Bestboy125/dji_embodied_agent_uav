package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AgentDecisionTest {
    @Test
    public void parsesFencedSingleAction() throws Exception {
        AgentDecision decision = AgentDecision.parse(
                "分析\n```json\n{\"thinking\":\"先观察\",\"decision\":\"act\","
                        + "\"action\":{\"skill\":\"observe\",\"parameters\":{}},"
                        + "\"goal_progress\":\"0%\"}\n```"
        );
        assertEquals(AgentDecision.Type.ACT, decision.getType());
        assertEquals("observe", decision.getSkill());
    }

    @Test(expected = JSONException.class)
    public void rejectsActionWithoutSkill() throws Exception {
        AgentDecision.parse("{\"decision\":\"act\",\"action\":{\"parameters\":{}}}");
    }

    @Test(expected = JSONException.class)
    public void neverTreatsUnparseableOutputAsSuccess() throws Exception {
        AgentDecision.parse("任务应该已经完成了");
    }
}
