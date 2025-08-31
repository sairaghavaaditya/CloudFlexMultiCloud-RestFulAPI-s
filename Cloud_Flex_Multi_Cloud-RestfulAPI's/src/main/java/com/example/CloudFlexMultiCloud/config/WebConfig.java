package com.example.CloudFlexMultiCloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    //Applied Only for Controller level
//    @Override
//    public void addCorsMappings(CorsRegistry registry) {
//        // Allow CORS for all /api endpoints
//        registry.addMapping("/api/**")
//                .allowedOrigins("http://localhost:3000")  // React frontend URL
//                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // Allow methods
//                .allowedHeaders("*")  // Allow all headers
//                .allowCredentials(true);  // Allow credentials (cookies, etc.)
//    }
}
