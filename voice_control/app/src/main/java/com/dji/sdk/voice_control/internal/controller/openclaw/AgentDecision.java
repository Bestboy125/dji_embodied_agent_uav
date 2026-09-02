package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONException;
import org.json.JSONObject;

/** Strict decision protocol: one turn may execute at most one skill. */
public final class AgentDecision {
    public enum Type { ACT, DONE, STUCK }

    private final Type type;
    private final String thinking;
    private final String progress;
    private final String skill;
    private final JSONObject parameters;

    private AgentDecision(Type type, String thinking, String progress,
                          String skill, JSONObject parameters) {
        this.type = type;
        this.thinking = thinking;
        this.progress = progress;
        this.skill = skill;
        this.parameters = parameters == null ? new JSONObject() : parameters;
    }

    public static AgentDecision parse(String modelOutput) throws JSONException {
        if (modelOutput == null || modelOutput.trim().isEmpty()) {
            throw new JSONException("LLM returned an empty decision");
        }
        String jsonText = extractJson(modelOutput);
        JSONObject json = new JSONObject(jsonText);
        String rawDecision = json.optString("decision", "").trim().toLowerCase();
        Type type;
        if ("act".equals(rawDecision)) {
            type = Type.ACT;
        } else if ("done".equals(rawDecision)) {
            type = Type.DONE;
        } else if ("stuck".equals(rawDecision)) {
            type = Type.STUCK;
        } else {
            throw new JSONException("decision must be act, done, or stuck");
        }

        JSONObject action = json.optJSONObject("action");
        String skill = action == null ? "" : action.optString("skill", "").trim();
        JSONObject parameters = action == null ? null : action.optJSONObject("parameters");
        if (type == Type.ACT && skill.isEmpty()) {
            throw new JSONException("act decision requires action.skill");
        }
        return new AgentDecision(
                type,
                json.optString("thinking", ""),
                json.optString("goal_progress", ""),
                skill,
                parameters
        );
    }

    static String extractJson(String text) throws JSONException {
        String trimmed = text.trim();
        int fenced = trimmed.indexOf("```json");
        if (fenced >= 0) {
            int start = fenced + "```json".length();
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new JSONException("No JSON object found in LLM response");
        }
        return trimmed.substring(start, end + 1);
    }

    public Type getType() { return type; }
    public String getThinking() { return thinking; }
    public String getProgress() { return progress; }
    public String getSkill() { return skill; }
    public JSONObject getParameters() { return parameters; }
}
