package com.golfbeta.config;

import com.golfbeta.auth.FirebaseAuthFilter;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public FirebaseAuthFilter firebaseAuthFilter(FirebaseAuth firebaseAuth) { return new FirebaseAuthFilter(firebaseAuth); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, FirebaseAuthFilter filter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {}) // enable CORS with the bean below
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // preflight
                        .requestMatchers(
                                "/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // add more public endpoints as needed
                        .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new org.springframework.web.cors.CorsConfiguration();
        // allow your RN packager/emulator + browser
        config.setAllowedOrigins(java.util.List.of(
                "http://localhost:3000",  // web dev
                "http://localhost:19006", // Expo web (if used)
                "http://localhost:8081",  // RN Metro
                "http://localhost:4200",  // other
                "capacitor://localhost"   // if relevant
        ));
        config.setAllowedMethods(java.util.List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("Authorization","Content-Type","X-Requested-With"));
        config.setAllowCredentials(true);

        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
