package com.aeroroster.web.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Same-origin API access. This is a browser-origin policy, with no authentication guarantees. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiCorsFilter extends OncePerRequestFilter {
    private final ApiErrors errors;
    private final JsonMapper json;

    public ApiCorsFilter(ApiErrors errors, JsonMapper json) {
        this.errors = errors;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/api/v1") && !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean crossOrigin;
        try {
            crossOrigin = CorsUtils.isCorsRequest(request);
        } catch (IllegalArgumentException exception) {
            crossOrigin = true;
        }
        if (crossOrigin) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            json.writeValue(response.getOutputStream(), errors.body("ORIGIN_NOT_ALLOWED",
                    "Use the API from the same origin", request, List.of(), null));
            return;
        }
        chain.doFilter(request, response);
    }
}
