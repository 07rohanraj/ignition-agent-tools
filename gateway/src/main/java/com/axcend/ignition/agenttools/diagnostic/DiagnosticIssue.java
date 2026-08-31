package com.axcend.ignition.agenttools.diagnostic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic error or warning for a Perspective view component or binding.
 */
public record DiagnosticIssue(
        String code,
        String severity,
        String category,
        String message,
        String details,
        String path,
        List<String> suggestions
) {
    public enum Severity {
        ERROR,
        WARNING
    }

    public enum Category {
        BINDING,
        COMPONENT,
        PROPERTY,
        CONFIG,
        STRUCTURE
    }

    public static DiagnosticIssue error(String code, Category category, String message, String path) {
        return new DiagnosticIssue(code, Severity.ERROR.name(), category.name(), message, null, path, List.of());
    }

    public static DiagnosticIssue error(String code, Category category, String message, String details, String path) {
        return new DiagnosticIssue(code, Severity.ERROR.name(), category.name(), message, details, path, List.of());
    }

    public static DiagnosticIssue error(String code, Category category, String message, String details, String path, List<String> suggestions) {
        return new DiagnosticIssue(code, Severity.ERROR.name(), category.name(), message, details, path, suggestions);
    }

    public static DiagnosticIssue warning(String code, Category category, String message, String path) {
        return new DiagnosticIssue(code, Severity.WARNING.name(), category.name(), message, null, path, List.of());
    }

    public static DiagnosticIssue warning(String code, Category category, String message, String details, String path) {
        return new DiagnosticIssue(code, Severity.WARNING.name(), category.name(), message, details, path, List.of());
    }

    public static DiagnosticIssue warning(String code, Category category, String message, String details, String path, List<String> suggestions) {
        return new DiagnosticIssue(code, Severity.WARNING.name(), category.name(), message, details, path, suggestions);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("severity", severity);
        map.put("category", category);
        map.put("message", message);
        if (details != null) {
            map.put("details", details);
        }
        map.put("path", path);
        if (suggestions != null && !suggestions.isEmpty()) {
            map.put("suggestions", suggestions);
        }
        return map;
    }
}
