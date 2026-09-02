package com.dji.sdk.voice_control.internal.controller.openclaw;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded working memory used in the next observe-reason-act turn. */
public final class AgentMemory {
    private final int capacity;
    private final Deque<JSONObject> history = new ArrayDeque<>();

    public AgentMemory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void record(int iteration, AgentDecision decision, SkillResult result) {
        JSONObject item = new JSONObject();
        try {
            item.put("iteration", iteration);
            item.put("decision", decision.getType().name().toLowerCase());
            item.put("skill", decision.getSkill());
            item.put("parameters", decision.getParameters());
            item.put("thinking", decision.getThinking());
            item.put("result", result == null ? JSONObject.NULL : result.toJson());
        } catch (Exception ignored) {
        }
        history.addLast(item);
        while (history.size() > capacity) history.removeFirst();
    }

    public synchronized JSONArray snapshot() {
        JSONArray array = new JSONArray();
        for (JSONObject item : history) array.put(item);
        return array;
    }

    public synchronized int consecutiveSkillCount(String skillName) {
        int count = 0;
        java.util.Iterator<JSONObject> iterator = history.descendingIterator();
        while (iterator.hasNext()) {
            JSONObject item = iterator.next();
            if (!skillName.equals(item.optString("skill"))) break;
            count++;
        }
        return count;
    }
}
