package com.axcend.ignition.agenttools.diagnostic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Statistics for a view diagnostic scan.
 */
public record ViewStats(
        int componentCount,
        int bindingCount,
        int errorCount,
        int warningCount
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("componentCount", componentCount);
        map.put("bindingCount", bindingCount);
        map.put("errorCount", errorCount);
        map.put("warningCount", warningCount);
        return map;
    }
}
