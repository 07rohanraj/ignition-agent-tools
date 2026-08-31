package com.axcend.ignition.agenttools.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive diagnostics for a Perspective view.
 */
public record ViewDiagnostics(
        String viewPath,
        boolean valid,
        List<DiagnosticIssue> errors,
        List<DiagnosticIssue> warnings,
        ViewStats stats
) {
    public static ViewDiagnostics empty(String viewPath) {
        return new ViewDiagnostics(
                viewPath,
                true,
                List.of(),
                List.of(),
                new ViewStats(0, 0, 0, 0)
        );
    }

    public static Builder builder(String viewPath) {
        return new Builder(viewPath);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("viewPath", viewPath);
        map.put("valid", valid);
        map.put("errors", errors.stream().map(DiagnosticIssue::toMap).toList());
        map.put("warnings", warnings.stream().map(DiagnosticIssue::toMap).toList());
        map.put("stats", stats.toMap());
        return map;
    }

    public static class Builder implements DiagnosticCollector {
        private final String viewPath;
        private final List<DiagnosticIssue> errors = new ArrayList<>();
        private final List<DiagnosticIssue> warnings = new ArrayList<>();
        private int componentCount = 0;
        private int bindingCount = 0;

        public Builder(String viewPath) {
            this.viewPath = viewPath;
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

        public Builder componentCount(int count) {
            this.componentCount = count;
            return this;
        }

        public Builder bindingCount(int count) {
            this.bindingCount = count;
            return this;
        }

        public ViewDiagnostics build() {
            ViewStats stats = new ViewStats(
                    componentCount,
                    bindingCount,
                    errors.size(),
                    warnings.size()
            );
            return new ViewDiagnostics(
                    viewPath,
                    errors.isEmpty(),
                    List.copyOf(errors),
                    List.copyOf(warnings),
                    stats
            );
        }
    }
}
