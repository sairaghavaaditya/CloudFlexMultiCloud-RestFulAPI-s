package com.example.CloudFlexMultiCloud.service.common;

import com.example.CloudFlexMultiCloud.dao.CloudAccountRepository;
import com.example.CloudFlexMultiCloud.dao.CloudFileRepository;
import com.example.CloudFlexMultiCloud.model.CloudAccount;
import com.example.CloudFlexMultiCloud.model.CloudFile;
import com.example.CloudFlexMultiCloud.model.CloudProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class FileTransferServiceImpl implements FileTransferService {

    @Autowired
    private CloudAccountRepository cloudAccountRepository;

    @Autowired
    private CloudFileRepository cloudFileRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @Transactional
    public void transferFile(Long userId, String sourceProvider, String destinationProvider, String fileId, String destinationParentId) {
        // Fetch source file metadata from DB

        try {
            CloudFile sourceFile = cloudFileRepository.findByFileId(fileId)
                    .orElseThrow(() -> new RuntimeException("File not found with ID: " + fileId));

            byte[] fileContent;

            // Step 1: Download file from Source Provider
            if (sourceProvider.equalsIgnoreCase(String.valueOf(CloudProvider.GOOGLE_DRIVE))) {
                fileContent = downloadFromGoogleDrive(userId, fileId);
            } else if (sourceProvider.equalsIgnoreCase(String.valueOf(CloudProvider.ONEDRIVE))) {
                fileContent = downloadFromOneDrive(userId, fileId);
            } else {
                throw new RuntimeException("Unsupported Source Provider: " + sourceProvider);
            }

            // Step 2: Upload file to Destination Provider
            if (destinationProvider.equalsIgnoreCase("GOOGLE_DRIVE")) {
                uploadToGoogleDrive(userId, sourceFile.getFileName(), fileContent, destinationParentId);

            } else if (destinationProvider.equalsIgnoreCase("ONEDRIVE")) {
                uploadToOneDrive(userId, sourceFile.getFileName(), fileContent, destinationParentId);

            } else {
                throw new RuntimeException("Unsupported Destination Provider: " + destinationProvider);
            }

            deleteFromSource(userId, sourceProvider, fileId);
        } catch (IOException e) {
            throw new RuntimeException("File transfer failed: " + e.getMessage(), e);
        }
    }

    private byte[] downloadFromGoogleDrive(Long userId, String fileId) {
        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found"));

        String url = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + googleAccount.getAccessToken());

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
        return response.getBody();
    }

    private byte[] downloadFromOneDrive(Long userId, String fileId) {
        CloudAccount oneDriveAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.ONEDRIVE)
                .orElseThrow(() -> new RuntimeException("OneDrive account not found"));

        String url = "https://graph.microsoft.com/v1.0/me/drive/items/" + fileId + "/content";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + oneDriveAccount.getAccessToken());

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
        return response.getBody();
    }


    private void uploadToGoogleDrive(Long userId, String fileName, byte[] fileContent, String parentId) throws IOException {
        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found"));

        String uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";

        // Metadata for Google Drive file
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", fileName);
        if (parentId != null && !parentId.isEmpty()) {
            metadata.put("parents", Collections.singletonList(parentId));
        }

        // Convert metadata to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        String metadataJson = objectMapper.writeValueAsString(metadata);

        // Set headers for multipart
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + googleAccount.getAccessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Part 1: Metadata (JSON)
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> metadataPart = new HttpEntity<>(metadataJson, jsonHeaders);

        // Part 2: File Content (Binary)
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<byte[]> filePart = new HttpEntity<>(fileContent, fileHeaders);

        // Build multipart request
        MultiValueMap<String, Object> multipartRequest = new LinkedMultiValueMap<>();
        multipartRequest.add("metadata", metadataPart);
        multipartRequest.add("file", filePart);

        // Final HTTP entity

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(multipartRequest, headers);

        // Send request
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("File uploaded to Google Drive successfully!");

                try {
                    // Parse response and save to DB
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(response.getBody());

                    String fileId = root.get("id").asText();

                    String mimeType = root.get("mimeType").asText();
                    long size = root.has("size") ? root.get("size").asLong() : 0L;

                    CloudFile cloudFile = new CloudFile();
                    cloudFile.setFileId(fileId);
                    cloudFile.setCloudAccount(googleAccount);
                    cloudFile.setFileName(fileName);
                    cloudFile.setMimeType(mimeType);
                    cloudFile.setSize(size);
                    cloudFile.setParentId(parentId);
                    cloudFile.setIsFolder(mimeType.equals("application/vnd.google-apps.folder"));
                    cloudFile.setIsTrashed(false);
                    cloudFile.setModifiedTime(LocalDateTime.now()); // Optional: Google Drive doesn't return modifiedTime directly here

                    cloudFileRepository.save(cloudFile);
                } catch (Exception e) {
                    System.out.println("Failed to sync metadata with google drive upload");
                }

            } else {
                throw new RuntimeException("Upload failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    private void uploadToOneDrive(Long userId, String fileName, byte[] fileContent, String parentId) {
        CloudAccount oneDriveAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.ONEDRIVE)
                .orElseThrow(() -> new RuntimeException("OneDrive account not found"));

        String uploadUrl;

        if (parentId == null || parentId.isEmpty()) {
            uploadUrl = "https://graph.microsoft.com/v1.0/me/drive/root:/" + fileName + ":/content";
        } else {
            uploadUrl = "https://graph.microsoft.com/v1.0/me/drive/items/" + parentId + ":/" + fileName + ":/content";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + oneDriveAccount.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileContent, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.PUT, requestEntity, String.class);
            System.out.println(" OneDrive upload successful!");
            try {
                // Parse OneDrive response JSON to get metadata
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonResponse = mapper.readTree(response.getBody());

                String fileId = jsonResponse.get("id").asText();
                String returnedFileName = jsonResponse.get("name").asText();


                long size = jsonResponse.has("size") ? jsonResponse.get("size").asLong() : 0L;
                String mimeType = jsonResponse.has("file") && jsonResponse.get("file").has("mimeType") ?
                        jsonResponse.get("file").get("mimeType").asText() : "application/octet-stream";

                // Save to cloud_files table
                CloudFile cloudFile = new CloudFile();
                cloudFile.setCloudAccount(oneDriveAccount);
                cloudFile.setFileId(fileId);
                cloudFile.setFileName(returnedFileName);
                cloudFile.setIsFolder(false);  // Since this is a file upload
                cloudFile.setParentId(parentId);
                cloudFile.setMimeType(mimeType);
                cloudFile.setSize(size);
                cloudFile.setIsTrashed(false);
                cloudFile.setModifiedTime(LocalDateTime.now());

                cloudFileRepository.save(cloudFile);
            } catch (Exception e) {
                System.out.println("Failed to sync metadata with One Drive upload");
            }
        } catch (Exception e) {
            System.out.println("Failed to upload file to one drive" + e.getMessage());
        }
    }


    private void deleteFromSource(Long userId, String sourceProvider, String fileId) {
        if (sourceProvider.equalsIgnoreCase("GOOGLE_DRIVE")) {
            deleteFromGoogleDrive(userId, fileId);
        } else if (sourceProvider.equalsIgnoreCase("ONEDRIVE")) {
            deleteFromOneDrive(userId, fileId);
        } else {
            throw new RuntimeException("Unsupported provider for deletion: " + sourceProvider);
        }
    }

    private void deleteFromGoogleDrive(Long userId, String fileId) {
        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found"));

        String url = "https://www.googleapis.com/drive/v3/files/" + fileId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + googleAccount.getAccessToken());

        HttpEntity<Void> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);

            System.out.println("File moved to trash successfully.");

            try {
                CloudFile file = cloudFileRepository.findByFileId(fileId)
                        .orElseThrow(() -> new RuntimeException("File metadata not found"));
                file.setIsTrashed(true);
                cloudFileRepository.save(file);
            } catch (Exception e) {
                System.out.println("Failed to sync metadata with Google Drive deletion");
            }


        } catch (Exception e) {
            System.out.println("Failed to delete file from google drive" + e.getMessage());
        }

        // Optionally remove from DB
        //cloudFileRepository.deleteByFileId(fileId);
    }


    private void deleteFromOneDrive(Long userId, String fileId) {
        CloudAccount oneDriveAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.ONEDRIVE)
                .orElseThrow(() -> new RuntimeException("OneDrive account not found"));
        String url = "https://graph.microsoft.com/v1.0/me/drive/items/" + fileId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + oneDriveAccount.getAccessToken());
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class);

            System.out.println(" File deleted successfully from OneDrive.");
            try {
//                CloudFile file = cloudFileRepository.findByFileId(fileId)
//                        .orElseThrow(() -> new RuntimeException("File metadata not found"));
//                file.setIsTrashed(true);
//                cloudFileRepository.save(file);
                cloudFileRepository.deleteByFileIdAndCloudAccountUserId(fileId,userId);
            } catch (Exception e) {
                System.out.println("Failed to sync metadata with One drive deletion");
            }

        } catch (Exception e) {
            System.out.println("Failed Delete file: " + e.getMessage());
        }


        // Optionally remove from DB
        //cloudFileRepository.deleteByFileId(fileId);
    }
}

