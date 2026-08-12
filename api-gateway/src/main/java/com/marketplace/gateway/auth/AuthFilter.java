package com.marketplace.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
    public static final String IDENTITY = AuthFilter.class.getName() + ".identity";
    private final DemoAuthService auth;
    private final ObjectMapper json;
    private final Clock clock;
    private final int limit;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthFilter(DemoAuthService auth, ObjectMapper json, Clock clock,
            @Value("${gateway.requests-per-minute:120}") int limit) {
        this.auth = auth; this.json = json; this.clock = clock; this.limit = limit;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/")
                || request.getRequestURI().startsWith("/ws/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            if (request.getRequestURI().equals("/auth/login")) {
                if (!allow("login:" + request.getRemoteAddr())) { error(response, 429, "RATE_LIMITED", "Retry in one minute"); return; }
                chain.doFilter(request, response);
                return;
            }
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) throw new DemoAuthService.UnauthorizedException("Authentication required");
            String token = header.substring(7);
            if (!allow(token)) { error(response, 429, "RATE_LIMITED", "Retry in one minute"); return; }
            request.setAttribute(IDENTITY, auth.verify(token));
            chain.doFilter(request, response);
        } catch (DemoAuthService.UnauthorizedException exception) {
            error(response, 401, "UNAUTHORIZED", exception.getMessage());
        }
    }

    private boolean allow(String key) {
        long minute = clock.instant().getEpochSecond() / 60;
        Bucket bucket = buckets.compute(key, (k, current) -> current == null || current.minute != minute
                ? new Bucket(minute, 1) : new Bucket(minute, current.count + 1));
        if (buckets.size() > 10_000) buckets.entrySet().removeIf(entry -> entry.getValue().minute < minute);
        return bucket.count <= limit;
    }

    private void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), Map.of("code", code, "message", message));
    }

    private record Bucket(long minute, int count) {}
}
