package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONObject;

/** Normalized result returned by every hard skill. */
public final class SkillResult {
    private final boolean success;
    private final String message;
    private final JSONObject output;

    private SkillResult(boolean success, String message, JSONObject output) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.output = output == null ? new JSONObject() : output;
    }

    public static SkillResult success(String message) {
        return new SkillResult(true, message, new JSONObject());
    }

    public static SkillResult success(String message, JSONObject output) {
        return new SkillResult(true, message, output);
    }

    public static SkillResult failure(String message) {
        return new SkillResult(false, message, new JSONObject());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public JSONObject getOutput() {
        return output;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("success", success);
            json.put("message", message);
            json.put("output", output);
        } catch (Exception ignored) {
        }
        return json;
    }
}
