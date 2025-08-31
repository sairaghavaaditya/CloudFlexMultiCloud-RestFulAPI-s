package com.example.CloudFlexMultiCloud.service.common;

import com.example.CloudFlexMultiCloud.dao.UserRepository;
import com.example.CloudFlexMultiCloud.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {



    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public boolean authenticate(String username, String rawPassword){

        Optional<User> dbUser = userRepository.findByEmail(username);

        return dbUser.isPresent() && passwordEncoder.matches(rawPassword,dbUser.get().getPassword());

    }
}
