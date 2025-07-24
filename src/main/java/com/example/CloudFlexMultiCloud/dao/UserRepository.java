package com.example.CloudFlexMultiCloud.dao;

import com.example.CloudFlexMultiCloud.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);
}
