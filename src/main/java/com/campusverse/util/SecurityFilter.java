package com.campusverse.util;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Filter that enforces UTF-8 character encoding, sets security headers,
 * and manages/validates per-session CSRF tokens on state-changing requests.
 */
@WebFilter(filterName = "SecurityFilter", urlPatterns = {"/*"})
public class SecurityFilter implements Filter {

    public static final String CSRF_TOKEN_ATTR = "csrfToken";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";
    public static final String CSRF_PARAM_NAME = "csrfToken";

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");

        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            // Set fundamental security headers
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            boolean isSecure = false;
            try {
                isSecure = httpRequest.isSecure();
            } catch (Throwable ignored) {}

            if (isSecure || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto"))) {
                httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }

            // Ensure a session exists with a valid CSRF token on every request
            HttpSession session = httpRequest.getSession(true);
            String sessionToken = (String) session.getAttribute(CSRF_TOKEN_ATTR);
            if (sessionToken == null || sessionToken.trim().isEmpty()) {
                sessionToken = UUID.randomUUID().toString();
                session.setAttribute(CSRF_TOKEN_ATTR, sessionToken);
            }
            // Make token accessible in request scope for JSPs
            httpRequest.setAttribute(CSRF_TOKEN_ATTR, sessionToken);

            String method = httpRequest.getMethod();
            if (STATE_CHANGING_METHODS.contains(method != null ? method.toUpperCase() : "")) {
                String servletPath = httpRequest.getServletPath();
                String action = httpRequest.getParameter("action");

                // Check if this is the initial unauthenticated authentication flow (login or register)
                boolean isInitialAuth = "/student".equals(servletPath)
                        && (action == null || "login".equalsIgnoreCase(action) || "register".equalsIgnoreCase(action))
                        && session.getAttribute("currentStudent") == null;

                if (!isInitialAuth) {
                    // Extract CSRF token from request parameter or header
                    String requestToken = httpRequest.getParameter(CSRF_PARAM_NAME);
                    if (requestToken == null || requestToken.trim().isEmpty()) {
                        requestToken = httpRequest.getHeader(CSRF_HEADER_NAME);
                    }
                    if (requestToken == null || requestToken.trim().isEmpty()) {
                        requestToken = httpRequest.getHeader("X-CSRF-TOKEN");
                    }

                    // Validate against active session token
                    if (requestToken == null || !requestToken.equals(sessionToken)) {
                        String accept = httpRequest.getHeader("Accept");
                        String xrw = httpRequest.getHeader("X-Requested-With");
                        boolean isJsonRequest = (accept != null && accept.contains("application/json"))
                                || "XMLHttpRequest".equalsIgnoreCase(xrw);

                        if (isJsonRequest) {
                            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            httpResponse.setContentType("application/json;charset=UTF-8");
                            httpResponse.getWriter().write("{\"error\":\"Forbidden: Invalid or missing CSRF token\"}");
                        } else {
                            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Invalid or missing CSRF token");
                        }
                        return; // Halt request processing
                    }
                }
            }
        }

        chain.doFilter(request, response);
    }
}
