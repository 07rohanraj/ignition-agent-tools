package com.axcend.ignition.agenttools.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostics for a specific component within a Perspective view.
 */
public record ComponentDiagnostics(
        String viewPath,
        String componentPath,
        String componentType,
        String componentName,
        boolean valid,
        List<DiagnosticIssue> errors,
        List<DiagnosticIssue> warnings
) {
    public static ComponentDiagnostics empty(String viewPath, String componentPath) {
        return new ComponentDiagnostics(
                viewPath,
                componentPath,
                null,
                null,
                true,
                List.of(),
                List.of()
        );
    }

    public static Builder builder(String viewPath, String componentPath) {
        return new Builder(viewPath, componentPath);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("viewPath", viewPath);
        map.put("componentPath", componentPath);
        map.put("componentType", componentType);
        map.put("componentName", componentName);
        map.put("valid", valid);
        map.put("errors", errors.stream().map(DiagnosticIssue::toMap).toList());
        map.put("warnings", warnings.stream().map(DiagnosticIssue::toMap).toList());
        return map;
    }

    public static class Builder implements DiagnosticCollector {
        private final String viewPath;
        private final String componentPath;
        private String componentType;
        private String componentName;
        private final List<DiagnosticIssue> errors = new ArrayList<>();
        private final List<DiagnosticIssue> warnings = new ArrayList<>();

        public Builder(String viewPath, String componentPath) {
            this.viewPath = viewPath;
            this.componentPath = componentPath;
        }

        public Builder componentType(String type) {
            this.componentType = type;
            return this;
        }

        public Builder componentName(String name) {
            this.componentName = name;
            return this;
        }

        public Builder addError(DiagnosticIssue issue) {
            if (issue.severity().equals(DiagnosticIssue.Severity.ERROR.name())) {
                errors.add(issue);
            } else {
                warnings.add(issue);
            }
            return this;
        }

        public Builder addWarning(DiagnosticIssue issue) {
            warnings.add(issue);
            return this;
        }

        public ComponentDiagnostics build() {
            return new ComponentDiagnostics(
                    viewPath,
                    componentPath,
                    componentType,
                    componentName,
                    errors.isEmpty(),
                    List.copyOf(errors),
                    List.copyOf(warnings)
            );
        }
    }
}
