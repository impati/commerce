package com.impati.commerce.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.ApiContracts.ErrorResponse;
import com.impati.commerce.common.DomainException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

/** 서비스 간 HTTP 실패를 호출자의 언어로 옮기는 명시적인 실행 경계 (ADR-0021). */
public final class ServiceCallExecutor {
    private static final Log log = LogFactory.getLog(ServiceCallExecutor.class);
    private static final ExpectedErrorHandler NO_EXPECTED_ERROR = ignored -> { };

    private final ObjectMapper objectMapper;

    public ServiceCallExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T query(String operation, Supplier<T> request) {
        return query(operation, request, NO_EXPECTED_ERROR);
    }

    public <T> T query(String operation, Supplier<T> request, ExpectedErrorHandler expectedError) {
        return requireResponse(operation, execute(CallKind.QUERY, operation, request, expectedError));
    }

    /** 구조화된 {@code not_found}만 정상적인 빈 조회 결과로 인정한다. */
    public <T> Optional<T> optionalQuery(String operation, Supplier<T> request) {
        try {
            return Optional.of(requireResponse(operation, request.get()));
        } catch (RestClientResponseException exception) {
            var error = downstreamError(exception);
            if (exception.getStatusCode().value() == 404 && error.hasCode("not_found")) {
                logExpectedResponse(operation, error);
                return Optional.empty();
            }
            logUnexpectedResponse(operation, error);
            throw translate(CallKind.QUERY, operation, exception);
        } catch (ResourceAccessException exception) {
            log.warn("downstream transport failure operation=" + operation
                    + " message=" + logValue(exception.getMessage()));
            throw DomainException.unavailable(operation + " is unavailable");
        } catch (RestClientException exception) {
            log.warn("downstream contract failure operation=" + operation
                    + " message=" + logValue(exception.getMessage()));
            throw DomainException.downstreamError(operation + " returned an invalid response");
        }
    }

    public <T> T command(String operation, Supplier<T> request) {
        return command(operation, request, NO_EXPECTED_ERROR);
    }

    public <T> T command(String operation, Supplier<T> request, ExpectedErrorHandler expectedError) {
        return requireResponse(operation, execute(CallKind.COMMAND, operation, request, expectedError));
    }

    public void command(String operation, Runnable request) {
        command(operation, request, NO_EXPECTED_ERROR);
    }

    public void command(String operation, Runnable request, ExpectedErrorHandler expectedError) {
        execute(CallKind.COMMAND, operation, () -> {
            request.run();
            return null;
        }, expectedError);
    }

    private <T> T execute(
            CallKind kind,
            String operation,
            Supplier<T> request,
            ExpectedErrorHandler expectedError
    ) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            var error = downstreamError(exception);
            if (exception.getStatusCode().is4xxClientError()) {
                try {
                    expectedError.handle(error);
                } catch (DomainException expected) {
                    logExpectedResponse(operation, error);
                    throw expected;
                }
            }
            logUnexpectedResponse(operation, error);
            throw translate(kind, operation, exception);
        } catch (ResourceAccessException exception) {
            log.warn("downstream transport failure operation=" + operation
                    + " message=" + logValue(exception.getMessage()));
            throw kind == CallKind.QUERY
                    ? DomainException.unavailable(operation + " is unavailable")
                    : DomainException.outcomeUnknown(operation + " outcome is unknown");
        } catch (RestClientException exception) {
            log.warn("downstream contract failure operation=" + operation
                    + " message=" + logValue(exception.getMessage()));
            throw DomainException.downstreamError(operation + " returned an invalid response");
        }
    }

    private DomainException translate(
            CallKind kind,
            String operation,
            RestClientResponseException exception
    ) {
        if (exception.getStatusCode().is5xxServerError()) {
            return kind == CallKind.QUERY
                    ? DomainException.unavailable(operation + " is unavailable")
                    : DomainException.outcomeUnknown(operation + " outcome is unknown");
        }
        return DomainException.downstreamError(operation + " returned an unexpected response");
    }

    private DownstreamError downstreamError(RestClientResponseException exception) {
        try {
            var response = objectMapper.readValue(exception.getResponseBodyAsByteArray(), ErrorResponse.class);
            return new DownstreamError(exception.getStatusCode().value(), response.code(), response.message());
        } catch (IOException | RuntimeException ignored) {
            return new DownstreamError(exception.getStatusCode().value(), null, null);
        }
    }

    private static <T> T requireResponse(String operation, T response) {
        if (response != null) return response;
        log.warn("downstream contract failure operation=" + operation + " message=empty response");
        throw DomainException.downstreamError(operation + " returned an invalid response");
    }

    private static void logExpectedResponse(String operation, DownstreamError error) {
        log.info("downstream response operation=" + operation
                + " status=" + error.status()
                + " code=" + logValue(error.code())
                + " message=" + logValue(error.message()));
    }

    private static void logUnexpectedResponse(String operation, DownstreamError error) {
        log.warn("downstream response operation=" + operation
                + " status=" + error.status()
                + " code=" + logValue(error.code())
                + " message=" + logValue(error.message()));
    }

    private static String logValue(String value) {
        if (value == null) return "-";
        var singleLine = value.replace('\r', ' ').replace('\n', ' ');
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500);
    }

    @FunctionalInterface
    public interface ExpectedErrorHandler {
        /** 인정하는 오류라면 호출자 소유 예외를 던지고, 아니면 그대로 반환한다. */
        void handle(DownstreamError error);
    }

    private enum CallKind {
        QUERY,
        COMMAND
    }
}
