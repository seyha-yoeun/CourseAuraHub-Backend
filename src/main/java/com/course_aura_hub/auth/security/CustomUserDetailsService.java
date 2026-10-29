package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.trim().toLowerCase()).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // In case OAuth user hasn't set password, pass empty string
        String password = user.getPassword() != null ? user.getPassword() : "";

        return new AuthUserPrincipal(
                user.getId(),
                user.getEmail(),
                password,
                // Pending local registrations are treated as disabled everywhere a principal is checked
                user.isEnabled() && !user.isPendingEmailVerification(),
                !user.isLockedAt(Instant.now()),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name())),
                user.getTokenVersion(),
                null
        );
    }
}
