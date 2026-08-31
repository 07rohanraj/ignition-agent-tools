package com.axcend.ignition.agenttools.diagnostic;

import com.inductiveautomation.ignition.common.expressions.DefaultFunctionFactory;
import com.inductiveautomation.ignition.common.expressions.Expression;
import com.inductiveautomation.ignition.common.expressions.ExpressionParseContext;
import com.inductiveautomation.ignition.common.expressions.FunctionFactory;
import com.inductiveautomation.ignition.common.expressions.parsing.ELParserHarness;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates Perspective expression-binding strings using the exact expression parser Ignition
 * itself uses for Perspective bindings.
 *
 * <p>Perspective expressions are parsed by {@link ELParserHarness} (from
 * {@code com.inductiveautomation.ignition.common.expressions}) with Ignition's standard
 * {@link DefaultFunctionFactory}. This validator drives that same parser directly over a static
 * {@link ExpressionParseContext}, so genuine syntax/token errors (dangling operators, illegal
 * characters, malformed tokens) are caught by the real Ignition grammar rather than by regex or
 * hand-rolled heuristics.</p>
 *
 * <p>Two notes on fidelity:</p>
 * <ul>
 *   <li>Perspective registers a few gateway-scope functions of its own (e.g. {@code property},
 *       {@code isAuthorized}, {@code translate}, {@code runScript}). When the parser reports
 *       {@code Unknown function} we downgrade that to a warning, because the static function set
 *       above cannot know every Perspective function and a false "parse error" would be worse than
 *       no error.</li>
 *   <li>Content-level checks that the parser accepts but that are still problematic (Python imports,
 *       client-scope {@code system.*} calls, malformed {@code runScript} arguments) are produced
 *       here as structured issues.</li>
 * </ul>
 *
 * <p>All expression validation lives here (single source of truth). Callers map the structured
 * {@link Issue} list to their own diagnostic containers and must not re-implement the syntax or
 * content heuristics themselves.</p>
 */
public class IgnitionExpressionValidator {

    public enum Severity {
        ERROR,
        WARNING
    }

    /**
     * A single structured finding produced during expression validation. {@link #code()} maps to
     * the agent-facing diagnostic code (e.g. {@code EXPRESSION_PARSE_ERROR}); {@link #severity()}
     * tells the caller whether it is an error or a warning.
     */
    public record Issue(String code, Severity severity, String message) {}

    /**
     * Validation result for an expression binding.
     *
     * <p>{@link #valid()} is {@code true} when there are no ERROR-severity issues. All findings
     * (errors and warnings) are listed in {@link #issues()}. {@link #errorCode()}/{@link #errorMessage()}
     * and {@link #suggestions()} are kept for convenience/backward compatibility but are derived
     * from {@link #issues()}.</p>
     */
    public record ValidationResult(
            boolean valid,
            String errorCode,
            String errorMessage,
            List<Issue> issues,
            List<String> suggestions
    ) {
        public List<Issue> issues() {
            return issues == null ? List.of() : issues;
        }

        public List<String> suggestions() {
            return suggestions == null ? List.of() : suggestions;
        }

        public static ValidationResult validResult() {
            return new ValidationResult(true, null, null, List.of(), List.of());
        }

        public static ValidationResult result(List<Issue> issues) {
            boolean valid = issues.stream().noneMatch(i -> i.severity() == Severity.ERROR);
            String errorCode = null;
            String errorMessage = null;
            if (!valid) {
                Issue first = issues.stream().filter(i -> i.severity() == Severity.ERROR)
                        .findFirst().orElse(null);
                if (first != null) {
                    errorCode = first.code();
                    errorMessage = first.message();
                }
            }
            List<String> suggestions = issues.stream()
                    .filter(i -> i.severity() == Severity.WARNING)
                    .map(Issue::message)
                    .toList();
            return new ValidationResult(valid, errorCode, errorMessage, issues, suggestions);
        }
    }

    private static final class ParseContext implements ExpressionParseContext {
        private final FunctionFactory factory;

        ParseContext(FunctionFactory factory) {
            this.factory = factory;
        }

        @Override
        public Expression createBoundExpression(String s) throws RuntimeException {
            // Static validation only: we never evaluate, so any property/tag reference is
            // represented by an inert marker expression.
            return new MarkerExpression();
        }

        @Override
        public FunctionFactory getFunctionFactory() {
            return factory;
        }
    }

    /** Inert {@link Expression} returned for bound references; never evaluated during validation. */
    private static final class MarkerExpression implements Expression {
        @Override
        public com.inductiveautomation.ignition.common.model.values.QualifiedValue execute() {
            return null;
        }

        @Override
        public String getOpName() {
            return "ref";
        }

        @Override
        public Expression[] getChildren() {
            return new Expression[0];
        }

        @Override
        public void connect(com.inductiveautomation.ignition.common.model.CommonContext c,
                            com.inductiveautomation.ignition.common.binding.InteractionListener l) {
            // No-op for static validation
        }

        @Override
        public void disconnect() {
            // No-op for static validation
        }

        @Override
        public void startup() {
            // No-op for static validation
        }

        @Override
        public void shutdown() {
            // No-op for static validation
        }
    }

    private final ELParserHarness parser = new ELParserHarness();
    private final FunctionFactory functionFactory = DefaultFunctionFactory.getSharedInstance();

    /**
     * Validates an expression binding using Ignition's actual expression parser.
     *
     * @param expression The expression string to validate
     * @param componentPath The path to the component (for context)
     * @param propertyPath The property path being bound
     * @return ValidationResult with any errors and suggestions
     */
    public ValidationResult validateExpression(String expression, String componentPath, String propertyPath) {
        if (expression == null || expression.trim().isEmpty()) {
            List<Issue> issues = new ArrayList<>();
            issues.add(new Issue("MISSING_EXPRESSION", Severity.ERROR,
                    "Expression binding has no 'expression'"));
            issues.addAll(suggestionIssues(""));
            return ValidationResult.result(issues);
        }

        List<Issue> issues = new ArrayList<>();

        // Content-level checks that the parser accepts but are problematic.
        // Python imports are not valid inside a single Perspective expression.
        if (expression.contains("from ") && expression.contains(" import ")) {
            issues.add(new Issue("PYTHON_IMPORT_IN_EXPRESSION", Severity.ERROR,
                    "Python imports are not valid in expression bindings; use runScript('Module.function', 0) instead"));
            issues.add(new Issue("EXPRESSION_SUGGESTION", Severity.WARNING,
                    "Use runScript('Module.function', 0) to call Python in a gateway module"));
            return ValidationResult.result(issues);
        }

        // Client-scope system functions are not available in a gateway-scope binding. These always
        // contain '.' (which the expression grammar rejects), so report the actionable warning and
        // skip the otherwise-confusing scan-error from the parser.
        if (expression.contains("system.perspective")
                || expression.contains("system.gui")
                || expression.contains("system.nav")) {
            issues.add(new Issue("CLIENT_SCOPE_FUNCTION_IN_EXPRESSION", Severity.WARNING,
                    "Expression uses client-scope system functions; use gateway-scope functions like system.tag.*, system.db.*, system.util.*"));
            return ValidationResult.result(issues);
        }

        // Parse with the real Ignition expression grammar (the same engine Perspective uses).
        try {
            parser.parse(expression, new ParseContext(functionFactory));
        } catch (Throwable t) {
            String message = t.getMessage() == null ? t.toString() : t.getMessage();
            if (isUnknownFunction(message)) {
                // Perspective registers additional gateway functions our static set can't know.
                // Downgrade to a warning so valid Perspective expressions aren't false-flagged.
                issues.add(new Issue("EXPRESSION_PARSE_ERROR", Severity.WARNING,
                        "Expression references a function not in the standard set: " + firstLine(message)));
            } else {
                issues.add(new Issue("EXPRESSION_PARSE_ERROR", Severity.ERROR,
                        "Expression parse error: " + firstLine(message)));
                issues.addAll(suggestionIssues(expression));
                return ValidationResult.result(issues);
            }
        }

        // runScript() argument checks (path dot-separated, non-negative numeric pollRate).
        issues.addAll(validateRunScriptCalls(expression));

        return ValidationResult.result(issues);
    }

    private static boolean isUnknownFunction(String message) {
        return message != null && (message.contains("Unknown function") || message.toLowerCase().contains("unknown function"));
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int nl = message.indexOf('\n');
        return nl >= 0 ? message.substring(0, nl) : message;
    }

    /**
     * Validates runScript() calls in the expression and returns any issues found.
     */
    private List<Issue> validateRunScriptCalls(String expression) {
        List<Issue> issues = new ArrayList<>();
        // Capture the module path (first string arg) and the full poll-rate argument so that
        // numeric, negative, and non-numeric poll rates are all reported precisely.
        Pattern runScriptPattern = Pattern.compile(
                "runScript\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*(.*?)\\s*\\)"
        );
        Matcher matcher = runScriptPattern.matcher(expression);
        while (matcher.find()) {
            String modulePath = matcher.group(1);
            String pollRateStr = matcher.group(2).trim();

            if (!modulePath.contains(".")) {
                issues.add(new Issue("INVALID_RUNSCRIPT_PATH", Severity.WARNING,
                        "runScript() module path should be dot-separated (e.g., 'Templates.Charts.BarChart.bar_dataset')"));
            }

            if (pollRateStr.isEmpty()) {
                issues.add(new Issue("INVALID_RUNSCRIPT_POLL_RATE", Severity.WARNING,
                        "runScript() pollRate must be a number"));
                continue;
            }

            try {
                int pollRate = Integer.parseInt(pollRateStr);
                if (pollRate < 0) {
                    issues.add(new Issue("NEGATIVE_RUNSCRIPT_POLL_RATE", Severity.WARNING,
                            "runScript() pollRate should not be negative"));
                }
            } catch (NumberFormatException e) {
                issues.add(new Issue("INVALID_RUNSCRIPT_POLL_RATE", Severity.WARNING,
                        "runScript() pollRate must be a number"));
            }
        }
        return issues;
    }

    /**
     * Informational suggestions used when an expression is empty or failed to parse.
     */
    private List<Issue> suggestionIssues(String expression) {
        List<Issue> issues = new ArrayList<>();
        if (expression == null || expression.trim().isEmpty()) {
            issues.add(new Issue("EXPRESSION_SUGGESTION", Severity.WARNING, "Add an expression like: 1 + 1"));
            issues.add(new Issue("EXPRESSION_SUGGESTION", Severity.WARNING, "Reference a tag: {[default]Motor/Speed}"));
            issues.add(new Issue("EXPRESSION_SUGGESTION", Severity.WARNING, "Reference a property: {view.params.myParam}"));
            issues.add(new Issue("EXPRESSION_SUGGESTION", Severity.WARNING, "Call a script: runScript('Module.function', 0)"));
        } else if (expression.trim().endsWith("+")) {
            issues.add(new Issue("EXPRESSION_SUGGESTION", Severity.WARNING, "Complete the expression: " + expression.trim() + " 1"));
            issues.add(new Issue("EXPRESSION_SUGGESTION", Severity.WARNING, "Or reference a value: " + expression.trim() + " {view.params.offset}"));
        }
        return issues;
    }
}
