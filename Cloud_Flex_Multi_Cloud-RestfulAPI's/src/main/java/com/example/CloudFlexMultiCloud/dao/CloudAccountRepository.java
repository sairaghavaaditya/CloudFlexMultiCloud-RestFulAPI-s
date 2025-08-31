package com.example.CloudFlexMultiCloud.dao;

import com.example.CloudFlexMultiCloud.model.CloudAccount;
import com.example.CloudFlexMultiCloud.model.CloudProvider;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

public interface CloudAccountRepository extends JpaRepository<CloudAccount,Long> {
        Optional<CloudAccount> findByUserIdAndProvider(Long userId, CloudProvider provider);

}
