package com.axcend.ignition.agenttools.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostics for a specific binding within a Perspective view component.
 */
public record BindingDiagnostics(
        String viewPath,
        String componentPath,
        String propertyPath,
        String bindingType,
        boolean valid,
        String quality,
        String message,
        List<DiagnosticIssue> errors
) {
    public static BindingDiagnostics empty(String viewPath, String componentPath, String propertyPath) {
        return new BindingDiagnostics(
                viewPath,
                componentPath,
                propertyPath,
                null,
                true,
                "Good",
                null,
                List.of()
        );
    }

    public static Builder builder(String viewPath, String componentPath, String propertyPath) {
        return new Builder(viewPath, componentPath, propertyPath);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("viewPath", viewPath);
        map.put("componentPath", componentPath);
        map.put("propertyPath", propertyPath);
        map.put("bindingType", bindingType);
        map.put("valid", valid);
        map.put("quality", quality);
        map.put("message", message);
        map.put("errors", errors.stream().map(DiagnosticIssue::toMap).toList());
        return map;
    }

    public static class Builder implements DiagnosticCollector {
        private final String viewPath;
        private final String componentPath;
        private final String propertyPath;
        private String bindingType;
        private String quality = "Good";
        private String message;
        private final List<DiagnosticIssue> errors = new ArrayList<>();

        public Builder(String viewPath, String componentPath, String propertyPath) {
            this.viewPath = viewPath;
            this.componentPath = componentPath;
            this.propertyPath = propertyPath;
        }

        public Builder bindingType(String type) {
            this.bindingType = type;
            return this;
        }

        public Builder quality(String quality) {
            this.quality = quality;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder addError(DiagnosticIssue issue) {
            errors.add(issue);
            return this;
        }

        public DiagnosticCollector addWarning(DiagnosticIssue issue) {
            errors.add(issue);
            return this;
        }

        public BindingDiagnostics build() {
            return new BindingDiagnostics(
                    viewPath,
                    componentPath,
                    propertyPath,
                    bindingType,
                    errors.isEmpty(),
                    quality,
                    message,
                    List.copyOf(errors)
            );
        }
    }
}
