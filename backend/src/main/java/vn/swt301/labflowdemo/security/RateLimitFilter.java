package vn.swt301.labflowdemo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.swt301.labflowdemo.common.ApiResponse;
import vn.swt301.labflowdemo.common.ErrorCode;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Day 20 (security review): at most 20 calls per minute per IP to the login / password endpoints.
 * In-memory - fine for one backend instance (ghi chú: nhiều instance thì cần Redis).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    static final int LIMIT_PER_MINUTE = 20;
    private static final Set<String> LIMITED = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password", "/api/v1/auth/register");

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + " " + request.getRequestURI();
        long now = clock.millis();
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() <= now - 60_000) {
                window.pollFirst();
            }
            allowed = window.size() < LIMIT_PER_MINUTE;
            if (allowed) {
                window.addLast(now);
            }
        }
        if (!allowed) {
            log.warn("[{}] {} {}", ErrorCode.TOO_MANY_REQUESTS, request.getRemoteAddr(), request.getRequestURI());
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.fail(ErrorCode.TOO_MANY_REQUESTS.name(), ErrorCode.TOO_MANY_REQUESTS.defaultMessage(), null));
            return;
        }
        chain.doFilter(request, response);
    }
}
