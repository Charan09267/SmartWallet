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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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


    //Repositories
    private final UserRepository userRepository;
    private final WalletRepo walletRepo;

    //Logger
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginRequest){
        logger.info("User is getting logged in with {}",loginRequest.getEmail());

        try{
            authenticate(loginRequest.getEmail(), loginRequest.getPassword());

            final UserDetails userDetails = appUserDetailService.loadUserByUsername(loginRequest.getEmail());

            final String jwtToken = jwtUtil.generateToken(userDetails);

            ResponseCookie cookie = ResponseCookie.from("jwt" , jwtToken)
                    .httpOnly(true)
                    .path("/")
                    .secure(true)
                    .maxAge(Duration.ofDays(1))
                    .sameSite("Strict")
                    .build();

            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE , cookie.toString()).body(Map.of("message" , "Account verification is successfull..."));
        }
        catch (BadCredentialsException e){
            Map<String , Object> error = new HashMap<>();
            error.put("error" , true);
            error.put("message" , "Email or Password is incorrect");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }catch (DisabledException e){
            Map<String , Object> error = new HashMap<>();
            error.put("error" , true);
            error.put("message" , "Account is disabled");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } catch (Exception e) {
            Map<String , Object> error = new HashMap<>();
            error.put("error",true);
            e.printStackTrace();
            error.put("message" , "Authentication failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }


    }

    private void authenticate(String email , String password){
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email , password));
    }


    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response){
        Cookie cookie = new Cookie("jwt" , null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);

        return ResponseEntity.ok("Logged out Successfully...");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request){
        Map<String , Object> response = new HashMap<>();

        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            response.put("status", "error");
            response.put("message", "Email already exists!");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // Create new user
        UserEntity user = new UserEntity();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword())); // encode password
        user.setRole(Role.USER); //default role
        user.setProvider(AuthProvider.LOCAL);



        logger.info("User has been created with email:- {}", user.getEmail());

        WalletEntity wallet = new WalletEntity();
        wallet.setUser(user);
        user.setWallet(wallet);

        userRepository.save(user);

        // Success response
        response.put("status", "success");
        response.put("message", "User registered successfully!");

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }


}
