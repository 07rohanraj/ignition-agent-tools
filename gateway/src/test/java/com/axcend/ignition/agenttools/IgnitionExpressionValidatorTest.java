package com.axcend.ignition.agenttools;

import com.axcend.ignition.agenttools.diagnostic.IgnitionExpressionValidator;
import com.axcend.ignition.agenttools.diagnostic.IgnitionExpressionValidator.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgnitionExpressionValidatorTest {

    private final IgnitionExpressionValidator validator = new IgnitionExpressionValidator();

    @Test
    void validSimpleArithmetic() {
        var result = validator.validateExpression("1 + 1", "$.root", "props.value");
        assertTrue(result.valid());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void validTagReference() {
        var result = validator.validateExpression("{[default]Motor/Speed}", "$.root", "props.value");
        assertTrue(result.valid());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void validPropertyReference() {
        var result = validator.validateExpression("{view.params.myParam}", "$.root", "props.value");
        assertTrue(result.valid());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void validRunScriptCall() {
        var result = validator.validateExpression("runScript('Templates.Charts.BarChart.bar_dataset', 0)", "$.root", "props.value");
        assertTrue(result.valid());
    }

    @Test
    void validComplexExpression() {
        var result = validator.validateExpression("if({[default]Motor/Speed} > 100, 'High', 'Low')", "$.root", "props.value");
        assertTrue(result.valid());
    }

    @Test
    void missingExpression() {
        var result = validator.validateExpression("", "$.root", "props.value");
        assertFalse(result.valid());
        assertEquals("MISSING_EXPRESSION", result.errorCode());
        assertTrue(result.suggestions().stream().anyMatch(s -> s.contains("1 + 1")));
        assertTrue(result.suggestions().stream().anyMatch(s -> s.contains("runScript")));
    }

    @Test
    void syntaxErrorUnmatchedParenthesis() {
        var result = validator.validateExpression("1 + ", "$.root", "props.value");
        assertFalse(result.valid());
        assertEquals("EXPRESSION_PARSE_ERROR", result.errorCode());
        assertTrue(result.suggestions().stream().anyMatch(s -> s.contains("Complete the expression")));
    }

    @Test
    void syntaxErrorIllegalToken() {
        var result = validator.validateExpression("1 + + 2", "$.root", "props.value");
        assertFalse(result.valid());
        assertEquals("EXPRESSION_PARSE_ERROR", result.errorCode());
    }

    @Test
    void pythonImportDetected() {
        var result = validator.validateExpression("from math import sqrt\nsqrt(4)", "$.root", "props.value");
        assertFalse(result.valid());
        assertEquals("PYTHON_IMPORT_IN_EXPRESSION", result.errorCode());
        assertTrue(result.suggestions().stream().anyMatch(s -> s.contains("runScript")));
    }

    @Test
    void clientScopeFunctionWarning() {
        var result = validator.validateExpression("system.perspective.openPopup('test')", "$.root", "props.value");
        assertTrue(result.valid());
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code().equals("CLIENT_SCOPE_FUNCTION_IN_EXPRESSION") && i.severity() == Severity.WARNING));
        assertTrue(result.suggestions().stream().anyMatch(s -> s.contains("gateway-scope")));
    }

    @Test
    void unknownPerspectiveFunctionIsWarningNotError() {
        // Perspective voices a few gateway functions (property, isAuthorized) that a static
        // function set can't know; these must not be false-flagged as hard parse errors.
        var result = validator.validateExpression("property('view.params.x')", "$.root", "props.value");
        assertTrue(result.valid(),
                "Unauthenticated Perspective function should be a warning, not an error: " + result.issues());
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code().equals("EXPRESSION_PARSE_ERROR") && i.severity() == Severity.WARNING));
    }

    @Test
    void runScriptInvalidModulePath() {
        var result = validator.validateExpression("runScript('InvalidModule', 0)", "$.root", "props.value");
        assertTrue(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("INVALID_RUNSCRIPT_PATH")));
    }

    @Test
    void runScriptNegativePollRate() {
        var result = validator.validateExpression("runScript('Module.function', -1)", "$.root", "props.value");
        assertTrue(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("NEGATIVE_RUNSCRIPT_POLL_RATE")));
    }

    @Test
    void runScriptInvalidPollRate() {
        var result = validator.validateExpression("runScript('Module.function', 'invalid')", "$.root", "props.value");
        assertTrue(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("INVALID_RUNSCRIPT_POLL_RATE")));
    }

    @Test
    void emptyExpressionSuggestions() {
        var result = validator.validateExpression("", "$.root", "props.value");
        List<String> suggestions = result.suggestions();
        assertTrue(suggestions.stream().anyMatch(s -> s.contains("1 + 1")));
        assertTrue(suggestions.stream().anyMatch(s -> s.contains("tag")));
        assertTrue(suggestions.stream().anyMatch(s -> s.contains("property")));
        assertTrue(suggestions.stream().anyMatch(s -> s.contains("runScript")));
    }
}
