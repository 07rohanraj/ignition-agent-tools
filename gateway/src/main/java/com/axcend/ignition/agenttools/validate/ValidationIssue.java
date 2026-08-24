package com.axcend.ignition.agenttools.validate;

import java.util.LinkedHashMap;
import java.util.Map;

/** One problem found while validating a Perspective view document. */
public record ValidationIssue(String path, String code, Severity severity, String message) {

    public enum Severity { ERROR, WARNING, INFO }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", path);
        map.put("code", code);
        map.put("severity", severity.name());
        map.put("message", message);
        return map;
    }
}
