package com.MyApp.PayFlow.config;

import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.entity.enums.AuthProvider;
import com.MyApp.PayFlow.entity.enums.Role;
import com.MyApp.PayFlow.repo.UserRepository;
import com.MyApp.PayFlow.repo.WalletRepo;
import com.MyApp.PayFlow.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final WalletRepo walletRepo;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        UserEntity user = userRepository.findByEmail(email).orElse(null);

        // If user not exists → register
        if(user == null){
            user = new UserEntity();
            user.setEmail(email);
            user.setName(name);
            user.setRole(Role.USER);
            user.setProvider(AuthProvider.GOOGLE);
            user.setPasswordHash(null);

            userRepository.save(user);

            WalletEntity wallet = new WalletEntity();
            wallet.setUser(user);
            walletRepo.save(wallet);
        }

        // Generate YOUR JWT
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password("")  // not needed
                .authorities("ROLE_" + user.getRole().name())
                .build();

        String jwt = jwtUtil.generateToken(userDetails);

        ResponseCookie cookie = ResponseCookie.from("jwt", jwt)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // redirect to frontend
//        response.sendRedirect("http://localhost:5173/dashboard");
    }
}
