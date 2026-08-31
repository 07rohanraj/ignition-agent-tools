package com.axcend.ignition.agenttools.diagnostic;

import com.inductiveautomation.ignition.gateway.config.ValidationErrors;
import com.inductiveautomation.ignition.gateway.config.ValidationException;

import java.util.List;

/**
 * Bridges the module's AI-facing {@link DiagnosticIssue} records to Ignition's native validation
 * container {@link ValidationErrors}.
 *
 * <p>Ignition's native container ({@code com.inductiveautomation.ignition.gateway.config}) is the
 * canonical, gateway-safe way to represent validation findings, and {@link ValidationException} is
 * the standard carrier thrown/returned across gateway module boundaries. The native model,
 * however, is message/field-oriented: it has no {@code code}, ERROR-vs-WARNING {@code severity},
 * {@code category}, or {@code suggestions} fields that the module's {@link DiagnosticIssue} wire
 * contract depends on for AI-actionable diagnostics (see AGENTS.md).</p>
 *
 * <p>Rather than replace {@link DiagnosticIssue} (which would drop those fields), this mapper
 * converts a list of issues into a native {@link ValidationErrors} so the module can interop with
 * gateway consumers that expect native validation shape — e.g. writing via
 * {@code ValidationErrors.write(HttpServletResponse)} or throwing a {@link ValidationException} —
 * while preserving the richer record for the AI-facing JSON.</p>
 *
 * <p><strong>Mapping rules</strong> — each issue becomes a field-level message keyed on its
 * {@code path}, prefixed with its compact diagnostic code for traceability:</p>
 * <ul>
 *   <li>{@code ERROR} issues are added as validation error field messages.</li>
 *   <li>{@code WARNING} issues are appended to the top-level message list (the native model has no
 *       severity, so warnings are surfaced as messages rather than errors).</li>
 * </ul>
 */
public final class ValidationErrorsMapper {

    private ValidationErrorsMapper() {
    }

    /**
     * Converts a list of {@link DiagnosticIssue}s into a native {@link ValidationErrors}.
     *
     * <p>Only ERROR-severity issues become native error field messages; WARNING issues are surfaced
     * as top-level messages (the native model cannot represent severity).</p>
     *
     * @param issues the AI-facing issues to convert
     * @return a native {@link ValidationErrors}; never {@code null}
     */
    public static ValidationErrors toValidationErrors(List<DiagnosticIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return ValidationErrors.newBuilder().build();
        }

        ValidationErrors.Builder builder = ValidationErrors.newBuilder();
        for (DiagnosticIssue issue : issues) {
            String field = issue.path() == null ? "$" : issue.path();
            String message = issue.code() + ": " + issue.message();
            if (DiagnosticIssue.Severity.ERROR.name().equals(issue.severity())) {
                builder.addFieldMessage(field, message);
            } else {
                builder.addMessage(message);
            }
        }
        return builder.build();
    }

    /**
     * Builds a native {@link ValidationException} carrying the given {@code errors}, or {@code null}
     * when there are no errors. Convenient for throwing from gateway module boundaries that expect
     * the standard native validation signaling.
     *
     * @param errors the native validation errors
     * @return a {@link ValidationException}, or {@code null} if {@code errors} is empty
     */
    public static ValidationException toValidationException(ValidationErrors errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return new ValidationException(errors);
    }
}
