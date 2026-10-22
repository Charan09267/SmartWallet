package com.MyApp.PayFlow.filter;

import com.MyApp.PayFlow.service.AppUserDetailService;
import com.MyApp.PayFlow.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    // Utility class responsible for extracting and validating JWT claims.
    private final JwtUtil jwtUtil;

    // Loads user details from the application's user store using the email.
    private final AppUserDetailService appUserDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Allow CORS preflight requests to proceed without JWT authentication.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Retrieve the requested URI to identify endpoints that do not
        // require JWT processing in this filter.
        String path = request.getRequestURI();

        // Skip JWT processing for the login endpoint.
        if (path.equals("/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = null;
        String email = null;

        // First, look for the JWT in the Authorization header.
        // Expected format: Authorization: Bearer <token>
        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }

        // If the Authorization header does not contain a token,
        // try retrieving the JWT from the "jwt" cookie.
        if (jwt == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

        // If no JWT is provided, continue the filter chain.
        // Spring Security can subsequently allow or reject the request
        // according to the endpoint's security configuration.
        if (jwt == null || jwt.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract the user's email (subject) from the JWT.
        // If extraction fails, continue without setting authentication.
        try {
            email = jwtUtil.extractEmail(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authenticate the request only if an email was extracted
        // and the current security context has no existing authentication.
        if (email != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Load the user's current details, including username and authorities,
            // from the application's user store.
            UserDetails userDetails =
                    appUserDetailsService.loadUserByUsername(email);

            // Validate the JWT against the loaded user details.
            if (jwtUtil.validateToken(jwt, userDetails)) {

                // Extract the roles or authorities stored in the JWT.
                List<String> roles = jwtUtil.extractRoles(jwt);

                // Convert each role string into a Spring Security authority.
                // Ensure the role format matches the application's authorization rules.
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                // Create an authenticated token containing the user details
                // and the authorities granted to this user.
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                authorities
                        );

                // Attach request-specific details, such as the remote address
                // and session identifier when available.
                authToken.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                // Store the authenticated user in Spring Security's context
                // so that downstream filters and controllers can identify the user.
                SecurityContextHolder.getContext()
                        .setAuthentication(authToken);
            }
        }

        // Continue processing the request through the remaining filters
        // and eventually to the controller if permitted.
        filterChain.doFilter(request, response);
    }
}
