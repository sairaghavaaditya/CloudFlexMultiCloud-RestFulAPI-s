package com.example.CloudFlexMultiCloud.service.oneDrive;

import org.springframework.web.multipart.MultipartFile;

public interface OneDriveFileServices {

    void refreshOneDriveAccessToken(Long userId);
    void syncUserOneDriveMetadata(Long userId);
    void uploadFileToOneDrive(MultipartFile multipartFile,Long userId,String parentFolderId);
    void deleteFileFromOneDrive(String fileId,Long userId);
}
