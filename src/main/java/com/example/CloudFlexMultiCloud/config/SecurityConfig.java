package com.example.CloudFlexMultiCloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                //.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())                  // ✅ Disable CSRF for stateless REST API
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/login").permitAll()   // ✅ Allow open access to /api/login
                        .requestMatchers("/google/drive/**").permitAll()   // ✅ Allow open access to all sublevels
                        .requestMatchers("/oneDrive/*").permitAll()   // ✅ Allow open access to one level after oneDrive in path
                        .requestMatchers("/cloud/*").permitAll()   // ✅ Allow open access to one level after oneDrive in path
                        .anyRequest().authenticated()                // 🔐 All other requests need authentication
                );


        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://localhost:3000"); // ✅ Allow React
        configuration.addAllowedMethod("*"); // ✅ Allow all methods
        configuration.addAllowedHeader("*"); // ✅ Allow all headers
        configuration.setAllowCredentials(true); // ✅ Allow credentials

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply to all paths
        return source;
    }
}
