package com.axcend.ignition.agenttools.diagnostic;

/**
 * Common interface for collecting diagnostic issues.
 * Used by ViewDiagnostics.Builder, ComponentDiagnostics.Builder, and BindingDiagnostics.Builder.
 */
public interface DiagnosticCollector {
    DiagnosticCollector addError(DiagnosticIssue issue);
    DiagnosticCollector addWarning(DiagnosticIssue issue);
}
