package com.marketplace.gateway.api;

import com.marketplace.gateway.auth.AuthFilter;
import com.marketplace.gateway.auth.DemoAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayController {
    private final DemoAuthService auth;
    private final HttpClient http;
    private final String orderUrl, campaignUrl, luckyDrawUrl, analyticsUrl, notificationUrl, rewardUrl;

    public GatewayController(DemoAuthService auth, HttpClient http,
            @Value("${gateway.order-url}") String orderUrl,
            @Value("${gateway.campaign-url}") String campaignUrl,
            @Value("${gateway.lucky-draw-url}") String luckyDrawUrl,
            @Value("${gateway.analytics-url}") String analyticsUrl,
            @Value("${gateway.notification-url}") String notificationUrl,
            @Value("${gateway.reward-url}") String rewardUrl) {
        this.auth = auth; this.http = http; this.orderUrl = orderUrl; this.campaignUrl = campaignUrl;
        this.luckyDrawUrl = luckyDrawUrl; this.analyticsUrl = analyticsUrl;
        this.notificationUrl = notificationUrl; this.rewardUrl = rewardUrl;
    }

    @PostMapping("/auth/login")
    public DemoAuthService.Login login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body)
            throws Exception {
        String path = request.getRequestURI();
        Destination destination = destination(path);
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        var identity = (DemoAuthService.Identity) request.getAttribute(AuthFilter.IDENTITY);
        var upstream = HttpRequest.newBuilder(URI.create(destination.url + destination.path + query))
                .header("Content-Type", request.getContentType() == null ? "application/json" : request.getContentType())
                .header("X-Demo-User", identity.userId()).header("X-Demo-Role", identity.role())
                .header("X-Correlation-Id", correlationId(request))
                .method(request.getMethod(), body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        var response = http.send(upstream, HttpResponse.BodyHandlers.ofByteArray());
        var result = ResponseEntity.status(response.statusCode());
        response.headers().firstValue(HttpHeaders.CONTENT_TYPE).ifPresent(value -> result.header(HttpHeaders.CONTENT_TYPE, value));
        return result.body(response.body());
    }

    private Destination destination(String path) {
        if (path.equals("/api/orders") || path.startsWith("/api/orders/")) return new Destination(orderUrl, path.substring(4));
        if (path.equals("/api/tickets") || path.startsWith("/api/tickets/")) return new Destination(luckyDrawUrl, path.substring(4));
        if (path.startsWith("/api/analytics/")) return new Destination(analyticsUrl, path.substring(14));
        if (path.equals("/api/notifications")) return new Destination(notificationUrl, "/notifications");
        if (path.equals("/api/rewards")) return new Destination(rewardUrl, "/rewards");
        if (path.equals("/api/campaigns") || path.startsWith("/api/campaigns/")) {
            boolean command = path.matches("/api/campaigns/[^/]+/(entries|end|rewards/(pending|cancel))");
            return new Destination(command ? luckyDrawUrl : campaignUrl, path.substring(4));
        }
        throw new NotFoundException("No route for " + path);
    }

    private static String correlationId(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    private record Destination(String url, String path) {}
    static class NotFoundException extends RuntimeException { NotFoundException(String m) { super(m); } }
}
