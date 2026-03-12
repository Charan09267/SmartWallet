package com.MyApp.PayFlow.util;


import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.repo.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final UserRepository userRepository;
    @Value("${jwt.secret.key}")
    private String SECRET_KEY;

    //Generate a JWT Token
    public String generateToken(UserDetails userDetails){
        Map<String , Object> claims = new HashMap<>();

        claims.put(
                "roles" ,
                userDetails.getAuthorities()
                        .stream()
                        .map(authority -> authority.getAuthority())
                        .toList()
        );

        claims.put(
                "userId",
                userRepository.findByEmail(userDetails.getUsername()).get().getId()
        );

        return createToken(claims , userDetails.getUsername());
    }

    //Creation of the jwt Token
    private String createToken(Map<String , Object> claims , String email){
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis()+1000*60*60*10))
                .signWith(SignatureAlgorithm.HS256 , SECRET_KEY)
                .compact();
    }


    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver){

        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);

    }

    private Claims extractAllClaims(String token){
        return Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractEmail(String token){
        return extractClaim(token , Claims::getSubject);
    }

    public Date extractExpiration(String token){
        return extractClaim(token , Claims::getExpiration);
    }
    private Boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token , UserDetails userDetails){

        final String email = extractEmail(token);
        return (email.equals(userDetails.getUsername()) && !isTokenExpired(token));

    }

    public List<String> extractRoles(String token){
        return extractClaim(token , claims -> claims.get("roles" , List.class));
    }

    public UUID extractUserId(String token){
        return extractClaim(token ,
                claims -> UUID.fromString(claims.get("userId").toString()));
    }
}
