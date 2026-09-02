package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-aircraft skill registry and prompt catalog. */
public final class SkillRegistry {
    private final Map<String, AgentSkill> skills = new LinkedHashMap<>();

    public synchronized SkillRegistry register(AgentSkill skill) {
        if (skill == null || skill.name() == null || skill.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Skill name must not be empty");
        }
        if (skills.containsKey(skill.name())) {
            throw new IllegalArgumentException("Duplicate skill: " + skill.name());
        }
        skills.put(skill.name(), skill);
        return this;
    }

    public synchronized AgentSkill get(String name) {
        return skills.get(name);
    }

    public synchronized Collection<AgentSkill> all() {
        return new java.util.ArrayList<>(skills.values());
    }

    public synchronized JSONArray catalog() {
        JSONArray result = new JSONArray();
        for (AgentSkill skill : skills.values()) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", skill.name());
                item.put("description", skill.description());
                item.put("input_schema", skill.inputSchema());
                item.put("flight_critical", skill.isFlightCritical());
                result.put(item);
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
