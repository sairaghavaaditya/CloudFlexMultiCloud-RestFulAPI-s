package com.example.CloudFlexMultiCloud.service.googleDrive;

import org.springframework.web.multipart.MultipartFile;


public interface GoogleFileServices {
    void syncUserGoogleDriveMetadata(Long userId);
    void refreshGoogleAccessToken(Long userId);
    void uploadFileToGoogleDrive(MultipartFile multipartFile,Long userId,String parentFolderId);
    void deleteFileFromGoogleDrive(String fileId,Long userId);
    void moveFileToTrashInGoogleDrive(String fileId,Long userId);
}
