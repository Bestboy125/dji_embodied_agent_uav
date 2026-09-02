package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONObject;

/** A typed, discoverable capability that can be selected by the agent. */
public interface AgentSkill {
    String name();

    String description();

    JSONObject inputSchema();

    boolean isFlightCritical();

    SkillResult execute(JSONObject parameters) throws Exception;
}
