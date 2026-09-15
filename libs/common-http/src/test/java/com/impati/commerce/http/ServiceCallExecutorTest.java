package com.impati.commerce.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCallExecutorTest {
    private final ServiceCallExecutor calls = new ServiceCallExecutor(new ObjectMapper());

    @Test
    void expectedClientErrorIsTranslatedByCaller() {
        var failure = assertThrows(DomainException.class, () -> calls.query(
                "catalog sku lookup",
                () -> { throw clientError(HttpStatus.NOT_FOUND, "not_found", "downstream message"); },
                error -> {
                    if (error.hasCode("not_found")) {
                        throw DomainException.notFound("sku not found");
                    }
                }
        ));

        assertEquals("not_found", failure.code());
        assertEquals("sku not found", failure.getMessage());
    }

    @Test
    void unexpectedClientErrorIsDownstreamError() {
        var failure = assertThrows(DomainException.class, () -> calls.query(
                "catalog products lookup",
                () -> { throw clientError(HttpStatus.BAD_REQUEST, "validation_error", "bad internal request"); }
        ));

        assertEquals("downstream_error", failure.code());
        assertEquals(502, failure.status());
    }

    @Test
    void queryServerAndTransportFailuresAreUnavailable() {
        var serverFailure = assertThrows(DomainException.class, () -> calls.query(
                "catalog products lookup",
                () -> { throw serverError(); }
        ));
        var transportFailure = assertThrows(DomainException.class, () -> calls.query(
                "catalog products lookup",
                () -> { throw new ResourceAccessException("timed out"); }
        ));

        assertEquals("service_unavailable", serverFailure.code());
        assertEquals("service_unavailable", transportFailure.code());
    }

    @Test
    void commandServerAndTransportFailuresHaveUnknownOutcome() {
        var serverFailure = assertThrows(DomainException.class, () -> calls.command(
                "notification request",
                () -> { throw serverError(); }
        ));
        var transportFailure = assertThrows(DomainException.class, () -> calls.command(
                "notification request",
                () -> { throw new ResourceAccessException("connection reset"); }
        ));

        assertEquals("outcome_unknown", serverFailure.code());
        assertEquals("outcome_unknown", transportFailure.code());
    }

    @Test
    void responseConversionFailureIsDownstreamError() {
        var failure = assertThrows(DomainException.class, () -> calls.query(
                "catalog products lookup",
                () -> { throw new RestClientException("cannot decode response"); }
        ));

        assertEquals("downstream_error", failure.code());
    }

    @Test
    void emptySuccessResponseIsDownstreamError() {
        var failure = assertThrows(DomainException.class, () -> calls.query(
                "catalog products lookup",
                () -> null
        ));

        assertEquals("downstream_error", failure.code());
    }

    @Test
    void optionalQueryAcceptsOnlyStructuredNotFound() {
        var missing = calls.optionalQuery(
                "payment lookup",
                () -> { throw clientError(HttpStatus.NOT_FOUND, "not_found", "payment missing"); }
        );

        assertTrue(missing.isEmpty());

        var malformed = assertThrows(DomainException.class, () -> calls.optionalQuery(
                "payment lookup",
                () -> { throw clientError(HttpStatus.NOT_FOUND, "different_error", "not expected"); }
        ));
        assertEquals("downstream_error", malformed.code());
    }

    private static HttpClientErrorException clientError(HttpStatus status, String code, String message) {
        var body = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, body,
                StandardCharsets.UTF_8);
    }

    private static HttpServerErrorException serverError() {
        return HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }
}
