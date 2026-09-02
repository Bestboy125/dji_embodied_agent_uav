package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONObject;

/** Deterministic safety checks applied after LLM planning and before device actuation. */
public final class SafetyPolicy {
    private final int minimumBatteryPercent;
    private final double maximumRelativeMoveMeters;

    public SafetyPolicy(int minimumBatteryPercent, double maximumRelativeMoveMeters) {
        this.minimumBatteryPercent = minimumBatteryPercent;
        this.maximumRelativeMoveMeters = maximumRelativeMoveMeters;
    }

    public String rejectReason(AgentDecision decision, AgentSkill skill, AgentObservation observation) {
        if (decision.getType() != AgentDecision.Type.ACT) return null;
        if (skill == null) return "未注册技能: " + decision.getSkill();

        String name = skill.name();
        if (skill.isFlightCritical() && !observation.isConnected()) {
            return "飞行器未连接，禁止执行飞行关键技能";
        }
        int battery = observation.batteryPercent();
        if (skill.isFlightCritical() && battery >= 0 && battery < minimumBatteryPercent
                && !"land".equals(name) && !"hover".equals(name)) {
            return "电量低于安全阈值 " + minimumBatteryPercent + "%";
        }
        if ("takeoff".equals(name) && observation.isFlying()) return "无人机已在空中";
        if (("move_relative".equals(name) || "rotate".equals(name)) && !observation.isFlying()) {
            return "无人机不在空中";
        }
        if ("move_relative".equals(name)) {
            JSONObject parameters = decision.getParameters();
            double distance = parameters.optDouble("distance_m", 0);
            if (distance <= 0 || distance > maximumRelativeMoveMeters) {
                return "单步相对位移必须在 0 到 " + maximumRelativeMoveMeters + " 米之间";
            }
        }
        if ("rotate".equals(name)) {
            double degrees = decision.getParameters().optDouble("degrees", 0);
            if (degrees <= 0 || degrees > 180) return "单次旋转角度必须在 0 到 180 度之间";
        }
        return null;
    }
}
