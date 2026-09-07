package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Role;
import com.denizcelikhalat.katalog.model.User;
import com.denizcelikhalat.katalog.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + email));

        // Rol artık tamamen DB'den (user.getRole()) okunuyor.
        // Eski/eksik kayıtlarda null gelirse güvenli tarafta kalıp USER kabul ediyoruz.
        Role role = (user.getRole() != null) ? user.getRole() : Role.USER;

        // DB'deki şifre BCrypt ile hash'li tutulduğundan hash'i olduğu gibi veriyoruz;
        // karşılaştırmayı PasswordEncoder yapar.
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles(role.name()) // .roles() otomatik "ROLE_" ön ekini ekler -> ROLE_USER / ROLE_ADMIN
                .build();
    }
}
