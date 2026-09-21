package com.synapse.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiKeyAuthFilter implements Filter {

    @Value("${synapse.api.key:}")
    private String requiredApiKey;

    private static final String HEALTH_ENDPOINT = "/actuator/health";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestPath = httpRequest.getRequestURI();

        if (requestPath.equals(HEALTH_ENDPOINT)) {
            chain.doFilter(request, response);
            return;
        }

        if (requiredApiKey == null || requiredApiKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = httpRequest.getHeader("X-API-Key");
        
        if (apiKey == null || apiKey.isEmpty()) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\":\"Missing X-API-Key header\"}");
            return;
        }

        if (!requiredApiKey.isEmpty() && !apiKey.equals(requiredApiKey)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\":\"Invalid API key\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
