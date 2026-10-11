package com.privatbank.cardsender.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalHttpLoggingFilterTests {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LocalHttpLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final LocalHttpLoggingFilter filter = new LocalHttpLoggingFilter();
    private Level originalLevel;

    @BeforeEach
    void captureLogs() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    @Test
    void logsRedactedExchangeWithoutChangingRequestOrResponse() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/orders/status/4444440000000008");
        request.setContentType("application/json");
        String requestBody = """
                {"firstName":"Dmytro","phone":"380671234567","cardTypeId":1}
                """;
        request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer secret-token");
        request.setQueryString("token=secret-query");
        var response = new MockHttpServletResponse();
        String responseBody = """
                {"CARD_NUMBER":"4444440000000008","STATUS":"GENERATED"}
                """;

        filter.doFilter(request, response, (req, res) -> {
            assertThat(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(requestBody);
            res.setContentType("application/json");
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
            res.getWriter().write(responseBody);
        });

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo(responseBody);
        assertThat(appender.list).hasSize(2);
        String logs = messages();
        assertThat(logs).contains("HTTP request", "POST /api/orders/status/4444********0008",
                        "HTTP response", "status=201", "durationMs=", "GENERATED", "\"cardTypeId\":1")
                .contains("380671234567")
                .doesNotContain("4444440000000008", "secret-token", "secret-query");
        assertThat(appender.list.get(0).getArgumentArray()[0])
                .isEqualTo(appender.list.get(1).getArgumentArray()[0]);
    }

    @Test
    void preservesAndLogsHandledErrorResponse() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/card-orders"), response, (req, res) -> {
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(400);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Invalid request\"}");
        });
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"Invalid request\"}");
        assertThat(messages()).contains("status=400", "Invalid request");
    }

    @Test
    void doesNotReportSuccessWhenAnUnhandledExceptionEscapes() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/card-types"),
                new MockHttpServletResponse(), (req, res) -> {
                    throw new ServletException("secret");
                }))
                .isInstanceOf(ServletException.class);
        assertThat(messages()).contains("status=unhandled-error").doesNotContain("secret", "status=200");
    }

    @Test
    void skipsSwaggerResources() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/v3/api-docs"),
                new MockHttpServletResponse(), (req, res) -> res.getWriter().write("{}"));
        assertThat(appender.list).isEmpty();
    }

    @Test
    void registersOnlyInLocalProfile() {
        var context = new ApplicationContextRunner().withUserConfiguration(LocalHttpLoggingFilter.class);
        context.run(app -> assertThat(app).doesNotHaveBean(LocalHttpLoggingFilter.class));
        context.withPropertyValues("spring.profiles.active=local")
                .run(app -> assertThat(app).hasSingleBean(LocalHttpLoggingFilter.class));
    }

    private String messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
