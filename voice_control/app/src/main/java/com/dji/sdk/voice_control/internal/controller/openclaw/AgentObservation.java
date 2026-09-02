package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONObject;

import java.io.File;

/** Immutable snapshot of the aircraft and its environment for one reasoning turn. */
public final class AgentObservation {
    private final long timestampMs;
    private final JSONObject state;
    private final File image;

    public AgentObservation(JSONObject state, File image) {
        this.timestampMs = System.currentTimeMillis();
        this.state = state == null ? new JSONObject() : state;
        this.image = image;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public JSONObject getState() {
        return state;
    }

    public File getImage() {
        return image;
    }

    public boolean isConnected() {
        return state.optBoolean("connected", false);
    }

    public boolean isFlying() {
        return state.optBoolean("is_flying", false);
    }

    public int batteryPercent() {
        return state.optInt("battery_percent", -1);
    }

    public String toPromptText() {
        return state.toString();
    }
}
