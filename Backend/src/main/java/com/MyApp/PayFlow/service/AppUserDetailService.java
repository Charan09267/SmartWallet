package com.MyApp.PayFlow.service;

import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.entity.enums.AuthProvider;
import com.MyApp.PayFlow.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppUserDetailService implements UserDetailsService {
    private final UserRepository userRepository;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity existingUser = userRepository.findByEmail(username).orElseThrow(()-> new UsernameNotFoundException("Email id is not found for email : "+ username));

        if(existingUser.getProvider() == AuthProvider.GOOGLE){
            return new User(
                    existingUser.getEmail(),
                    "",
                    List.of(new SimpleGrantedAuthority("ROLE_"+ existingUser.getRole().name()))
            );
        }
        return new User(existingUser.getEmail(), existingUser.getPasswordHash(), List.of(new SimpleGrantedAuthority("ROLE_"+existingUser.getRole())));
    }
}
