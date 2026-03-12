package com.MyApp.PayFlow.filter;


import com.MyApp.PayFlow.config.SecurityConfig;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AppUserDetailService appUserDetailsService;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        //
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        if (path.equals("/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = null;
        String email = null;

        final String authHeader = request.getHeader("Authorization");
        if(authHeader != null && authHeader.startsWith("Bearer ")){
            jwt = authHeader.substring(7);
        }

        if(jwt == null && request.getCookies() != null){
            for(Cookie cookie : request.getCookies()){
                if("jwt".equals(cookie.getName())){
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

        if(jwt == null || jwt.isBlank()){
            filterChain.doFilter(request , response);
            return;
        }

        try{
            email = jwtUtil.extractEmail(jwt);
        }catch (Exception e){
            filterChain.doFilter(request , response);
            return;
        }

        if(email != null && SecurityContextHolder.getContext().getAuthentication() == null){
            UserDetails userDetails = appUserDetailsService.loadUserByUsername(email);

            if(jwtUtil.validateToken(jwt , userDetails)){

                List<String> roles = jwtUtil.extractRoles(jwt);

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails ,
                        null ,
                        authorities
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);

            }
        }

        filterChain.doFilter(request , response);
    }
}
