package com.axcend.ignition.agenttools;

import com.axcend.ignition.agenttools.diagnostic.DiagnosticIssue;
import com.axcend.ignition.agenttools.diagnostic.ValidationErrorsMapper;
import com.inductiveautomation.ignition.gateway.config.ValidationErrors;
import com.inductiveautomation.ignition.gateway.config.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationErrorsMapperTest {

    @Test
    void errorIssuesBecomeFieldMessagesKeyedOnPath() {
        DiagnosticIssue issue = DiagnosticIssue.error(
                "EXPRESSION_PARSE_ERROR", DiagnosticIssue.Category.BINDING,
                "Expression parse error", "$.root.props.path");

        ValidationErrors errors = ValidationErrorsMapper.toValidationErrors(List.of(issue));

        assertFalse(errors.isEmpty(), "native errors should not be empty");
        assertEquals(1, errors.fieldMessages().size());
        assertEquals("EXPRESSION_PARSE_ERROR: Expression parse error",
                errors.fieldMessages().get(0).messages().get(0));
        assertEquals("$.root.props.path", errors.fieldMessages().get(0).fieldName());
    }

    @Test
    void warningIssuesBecomeTopLevelMessages() {
        DiagnosticIssue issue = DiagnosticIssue.warning(
                "CLIENT_SCOPE_FUNCTION_IN_EXPRESSION", DiagnosticIssue.Category.BINDING,
                "Client scope", "$.root");

        ValidationErrors errors = ValidationErrorsMapper.toValidationErrors(List.of(issue));

        assertFalse(errors.isEmpty());
        assertTrue(errors.fieldMessages().isEmpty(), "warnings should not be native errors");
        assertEquals(1, errors.messages().size());
        assertTrue(errors.messages().get(0).startsWith("CLIENT_SCOPE_FUNCTION_IN_EXPRESSION: "));
    }

    @Test
    void emptyInputYieldsEmptyErrors() {
        ValidationErrors errors = ValidationErrorsMapper.toValidationErrors(List.of());
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validationExceptionBuiltWhenErrorsPresent() {
        DiagnosticIssue issue = DiagnosticIssue.error(
                "MISSING_EXPRESSION", DiagnosticIssue.Category.BINDING,
                "No expression", "$");
        ValidationErrors errors = ValidationErrorsMapper.toValidationErrors(List.of(issue));
        ValidationException exception = ValidationErrorsMapper.toValidationException(errors);
        assertNotNull(exception);
        assertNotNull(exception.getValidationErrors());
        assertFalse(exception.getValidationErrors().isEmpty());
    }

    @Test
    void validationExceptionNullWhenNoErrors() {
        ValidationErrors errors = ValidationErrorsMapper.toValidationErrors(List.of());
        assertNull(ValidationErrorsMapper.toValidationException(errors));
    }
}
