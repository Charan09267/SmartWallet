package com.MyApp.PayFlow.controller;


import com.MyApp.PayFlow.dto.LoginRequestDTO;
import com.MyApp.PayFlow.dto.RegisterRequest;
import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.entity.enums.AuthProvider;
import com.MyApp.PayFlow.entity.enums.Role;
import com.MyApp.PayFlow.repo.UserRepository;
import com.MyApp.PayFlow.repo.WalletRepo;
import com.MyApp.PayFlow.service.AppUserDetailService;
import com.MyApp.PayFlow.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;


import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final AppUserDetailService appUserDetailService;
    private final PasswordEncoder passwordEncoder;

    // Repositories used to access user and wallet data during registration.
    private final UserRepository userRepository;
    private final WalletRepo walletRepo;

    private static final Logger logger =
            LoggerFactory.getLogger(AuthController.class);

    /**
     * Authenticates a user and issues a JWT through an HTTP-only cookie.
     *
     * @param loginRequest contains the user's email and password
     * @return authentication result and, on success, the JWT cookie
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequestDTO loginRequest) {

        logger.info("User is getting logged in with {}", loginRequest.getEmail());

        try {
            // Delegate credential verification to Spring Security.
            authenticate(
                    loginRequest.getEmail(),
                    loginRequest.getPassword()
            );

            // Load the authenticated user's details to generate the JWT.
            final UserDetails userDetails =
                    appUserDetailService.loadUserByUsername(
                            loginRequest.getEmail()
                    );

            final String jwtToken = jwtUtil.generateToken(userDetails);

            // Store the JWT in a cookie that JavaScript cannot access.
            ResponseCookie cookie = ResponseCookie.from("jwt", jwtToken)
                    .httpOnly(true)
                    .path("/")
                    .secure(true)
                    .maxAge(Duration.ofDays(1))
                    .sameSite("Strict")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(Map.of(
                            "message",
                            "Account verification is successfull..."
                    ));

        } catch (BadCredentialsException e) {
            // Return a generic message to avoid revealing which credential failed.
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", "Email or Password is incorrect");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);

        } catch (DisabledException e) {
            // Reject authentication when the account is disabled.
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", "Account is disabled");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);

        } catch (Exception e) {
            // Handle unexpected authentication failures.
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            e.printStackTrace();
            error.put("message", "Authentication failed");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    /**
     * Authenticates the supplied credentials using Spring Security.
     */
    private void authenticate(String email, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );
    }

    /**
     * Clears the JWT cookie in the client's browser.
     *
     * @param response HTTP response used to expire the cookie
     * @return logout confirmation
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {

        // Expire the cookie so the browser removes its stored JWT.
        Cookie cookie = new Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);

        return ResponseEntity.ok("Logged out Successfully...");
    }

    /**
     * Registers a new local user and creates an associated wallet.
     *
     * @param request contains the new user's registration details
     * @return registration status and message
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request) {

        Map<String, Object> response = new HashMap<>();

        // Prevent registration when the email is already in use.
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            response.put("status", "error");
            response.put("message", "Email already exists!");

            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // Build the new user entity with an encoded password and default role.
        UserEntity user = new UserEntity();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setProvider(AuthProvider.LOCAL);

        logger.info("User has been created with email:- {}", user.getEmail());

        // Associate a wallet with the new user.
        WalletEntity wallet = new WalletEntity();
        wallet.setUser(user);
        user.setWallet(wallet);

        // Persist the user; wallet persistence depends on entity cascade settings.
        userRepository.save(user);

        response.put("status", "success");
        response.put("message", "User registered successfully!");

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Returns basic information about the currently authenticated user.
     *
     * @param authentication provides the authenticated principal
     * @return the user's email, name, and ID
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {

        Map<String, String> map = new HashMap<>();

        // The authentication name is expected to contain the user's email.
        String email = authentication.getName();

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Try to log in again"));

        map.put("email", user.getEmail());
        map.put("Name", user.getName());
        map.put("id", user.getId().toString());

        return ResponseEntity.ok(map);
    }
}
