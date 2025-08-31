package com.example.CloudFlexMultiCloud.dao;

import com.example.CloudFlexMultiCloud.model.BasicUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface BasicUserRepository extends JpaRepository<BasicUser, Long> {
    Optional<BasicUser> findByUsername(String username);
}
