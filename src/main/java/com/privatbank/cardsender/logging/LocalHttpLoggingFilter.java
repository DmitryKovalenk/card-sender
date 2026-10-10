package com.privatbank.cardsender.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Profile("local")
public class LocalHttpLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(LocalHttpLoggingFilter.class);
    private final HttpLogFormatter formatter = new HttpLogFormatter();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !LOG.isDebugEnabled() || !(path.startsWith("/api/") || path.startsWith("/demo/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var cachedRequest = new ContentCachingRequestWrapper(request, HttpLogFormatter.MAX_BODY_BYTES);
        var cachedResponse = new ContentCachingResponseWrapper(response);
        String exchangeId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(cachedRequest, cachedResponse);
            completed = true;
        } finally {
            try {
                LOG.debug("HTTP request [{}] {} {} body={}", exchangeId, request.getMethod(),
                        formatter.path(request.getRequestURI()),
                        formatter.body(cachedRequest.getContentAsByteArray(), request.getContentType()));
                LOG.debug("HTTP response [{}] status={} durationMs={} body={}", exchangeId,
                        completed ? Integer.toString(cachedResponse.getStatus()) : "unhandled-error",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                        formatter.body(cachedResponse.getContentAsByteArray(), cachedResponse.getContentType()));
            } finally {
                cachedResponse.copyBodyToResponse();
            }
        }
    }
}
