package com.example.CloudFlexMultiCloud.dao;

import com.example.CloudFlexMultiCloud.model.CloudFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CloudFileRepository extends JpaRepository<CloudFile,Long> {

    List<CloudFile> findByCloudAccountIdAndCloudAccountUserId(Long accountId, Long userId);
    void deleteByCloudAccountIdAndCloudAccountUserId(Long accountId, Long userId);

    void deleteByFileIdAndCloudAccountUserId(String fileId,Long userId);
    void deleteByFileId(String fileId);

    Optional<CloudFile> findByFileId(String fileId);

    List<CloudFile> findByCloudAccountId(Long cloudAccountId);

    List<CloudFile> findByParentIdAndCloudAccountId(String parentId, Long cloudAccountId);
}
