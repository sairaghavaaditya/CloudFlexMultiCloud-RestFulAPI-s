package com.example.CloudFlexMultiCloud.service.googleDrive;

import com.example.CloudFlexMultiCloud.dao.CloudAccountRepository;
import com.example.CloudFlexMultiCloud.dao.CloudFileRepository;
import com.example.CloudFlexMultiCloud.model.CloudAccount;
import com.example.CloudFlexMultiCloud.model.CloudFile;
import com.example.CloudFlexMultiCloud.model.CloudProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GoogleFileServicesImpl implements GoogleFileServices {

    @Autowired
    private CloudAccountRepository cloudAccountRepository;

    @Autowired
    private CloudFileRepository cloudFileRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @Transactional
    public void syncUserGoogleDriveMetadata(Long userId) {
        // Get the user's Google Drive account

        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found for user: " + userId));

        String accessToken = googleAccount.getAccessToken();


        String GOOGLE_DRIVE_API_URL =
                "https://www.googleapis.com/drive/v3/files?pageSize=1000&fields=files(id,name,mimeType,modifiedTime,parents,size,trashed)";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                GOOGLE_DRIVE_API_URL,
                HttpMethod.GET,
                entity,
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            List<CloudFile> files = new ArrayList<>();
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                JsonNode filesNode = root.path("files");

                for (JsonNode fileNode : filesNode) {


                    CloudFile file = new CloudFile();
                    file.setCloudAccount(googleAccount);
                    file.setFileId(fileNode.get("id").asText());
                    file.setFileName(fileNode.get("name").asText());
                    file.setMimeType(fileNode.get("mimeType").asText());
                    file.setModifiedTime(fileNode.has("modifiedTime")
                            ? LocalDateTime.parse(fileNode.get("modifiedTime").asText().replace("Z", ""))
                            : null);
                    file.setParentId(fileNode.has("parents") ? fileNode.get("parents").get(0).asText() : null);
                    file.setIsFolder(file.getMimeType().equals("application/vnd.google-apps.folder"));
                    file.setIsTrashed(fileNode.has("trashed") && fileNode.get("trashed").asBoolean());
                    file.setSize(fileNode.has("size") ? fileNode.get("size").asLong() : 0L);
                    file.setCreatedAt(LocalDateTime.now());
                    files.add(file);
                }

                // Remove old files for this account
                cloudFileRepository.deleteByCloudAccountIdAndCloudAccountUserId(googleAccount.getId(), userId);

                // Save new ones
                cloudFileRepository.saveAll(files);

            } catch (Exception e) {
                throw new RuntimeException("Failed to parse Google Drive response", e);
            }
        } else {
            throw new RuntimeException("Google API error: " + response.getStatusCode());
        }
    }

    public void refreshGoogleAccessToken(Long userId) {
        String tokenUrl = "https://oauth2.googleapis.com/token";

        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found for user: " + userId));

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);


        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", googleAccount.getClientId());
        form.add("client_secret", googleAccount.getClientSecret());
        form.add("refresh_token", googleAccount.getRefreshToken());
        form.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String newAccessToken = (String) response.getBody().get("access_token");
                Integer expiresInSeconds = (Integer) response.getBody().get("expires_in");
                googleAccount.setAccessToken(newAccessToken);
                googleAccount.setTokenExpiry(LocalDateTime.now().plusSeconds(expiresInSeconds));
                System.out.println("New Access Token: " + newAccessToken);
                cloudAccountRepository.save(googleAccount);
            } else {
                System.err.println("Failed to refresh access token: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error refreshing token: " + e.getMessage());
        }
    }


    public void uploadFileToGoogleDrive(MultipartFile multipartFile, Long userId, @Nullable String parentFolderId) {

        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found for : " + userId));

        String uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";

        // Main Headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + googleAccount.getAccessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // Metadata JSON with optional parents
        HttpHeaders metadataHeaders = new HttpHeaders();
        metadataHeaders.setContentType(MediaType.APPLICATION_JSON);

        String metadataJson;
        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            metadataJson = String.format("{\"name\": \"%s\", \"parents\": [\"%s\"]}",
                    multipartFile.getOriginalFilename(), parentFolderId);
        } else {
            metadataJson = String.format("{\"name\": \"%s\"}", multipartFile.getOriginalFilename());
        }

        HttpEntity<String> metadataPart = new HttpEntity<>(metadataJson, metadataHeaders);

        // File content part
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        HttpEntity<byte[]> filePart;
        try {
            filePart = new HttpEntity<>(multipartFile.getBytes(), fileHeaders);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        // Multipart Body
        MultiValueMap<String, Object> multipartRequest = new LinkedMultiValueMap<>();
        multipartRequest.add("metadata", metadataPart);
        multipartRequest.add("file", filePart);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(multipartRequest, headers);

        try {
            ResponseEntity<String> response = new RestTemplate()
                    .postForEntity(uploadUrl, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.print("Google Drive file uploaded successfully! ");
                try {
                    // Parse response and save to DB
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(response.getBody());

                    String fileId = root.get("id").asText();
                    System.out.println("File ID: "+fileId);
                    String fileName = root.get("name").asText();
                    String mimeType = root.get("mimeType").asText();

/* This is get set correct file size to metadata
                    String fileGetUrl = "https://www.googleapis.com/drive/v3/files/" + fileId +
                            "?fields=id,name,size,mimeType,modifiedTime,parents";

                    HttpHeaders getHeaders = new HttpHeaders();
                    getHeaders.set("Authorization", "Bearer " + googleAccount.getAccessToken());

                    HttpEntity<Void> getRequest = new HttpEntity<>(getHeaders);

                    ResponseEntity<String> getResponse = new RestTemplate().exchange(
                            fileGetUrl, HttpMethod.GET, getRequest, String.class);
                    if (getResponse.getStatusCode().is2xxSuccessful()) {
                    JsonNode fileNode = mapper.readTree(getResponse.getBody());
                    size = fileNode.has("size") ? fileNode.get("size").asLong() : 0L;
                    } catch (Exception e) {
                System.out.println("Failed to sync size after upload. Consider optimizing this in future with webhook or batch sync.");
            }
              **/


                    long size = root.has("size") ? root.get("size").asLong() : 0L;

                    CloudFile cloudFile = new CloudFile();
                    cloudFile.setFileId(fileId);
                    cloudFile.setCloudAccount(googleAccount);
                    cloudFile.setFileName(fileName);
                    cloudFile.setMimeType(mimeType);
                    cloudFile.setSize(size);
                    cloudFile.setParentId(parentFolderId);
                    cloudFile.setIsFolder(mimeType.equals("application/vnd.google-apps.folder"));
                    cloudFile.setIsTrashed(false);
                    cloudFile.setModifiedTime(LocalDateTime.now()); // Optional: Google Drive doesn't return modifiedTime directly here

                    cloudFileRepository.save(cloudFile);
                } catch (Exception e) {
                    System.out.println("File Uploaded But Failed to sync metadata with google drive upload");
                }
            } else {
                throw new RuntimeException("Upload failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @Transactional
    public void deleteFileFromGoogleDrive(String fileId, Long userId) {
        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found for : " + userId));
        String deleteUrl = "https://www.googleapis.com/drive/v3/files/" + fileId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + googleAccount.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<Void> response = restTemplate.exchange(deleteUrl, HttpMethod.DELETE, request, Void.class);

            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {

                System.out.println("File Deleted successfully. and File ID: "+fileId);
                try {
                    cloudFileRepository.deleteByFileIdAndCloudAccountUserId(fileId, userId);
                }catch(Exception e){
                    System.out.println("File Deleted But Failed to Sync metadata with Google Drive");
                }

            } else {
                System.out.println(" Unexpected response: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Failed to delete file: " + e.getMessage());
        }
    }



    public void moveFileToTrashInGoogleDrive(String fileId, Long userId) {
        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found for: " + userId));

        String patchUrl = "https://www.googleapis.com/drive/v3/files/" + fileId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + googleAccount.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Request body to move file to trash
        String requestBody = "{\"trashed\": true}";
        System.out.println("Entered");

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    patchUrl,
                    HttpMethod.PATCH,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("File moved to trash successfully.");
                try {
                    CloudFile file = cloudFileRepository.findByFileId(fileId)
                            .orElseThrow(() -> new RuntimeException("File metadata not found"));
                    file.setIsTrashed(true);
                    cloudFileRepository.save(file);
                } catch (Exception e) {
                    System.out.println("File moved to trash But Failed to sync metadata with Google Drive trash operation");
                }

            } else {
                System.out.println("Unexpected response: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Failed to move file to trash: " + e.getMessage());
        }
    }



    //This is not need because we already having the metadata in the database with syncing function i wrote before
    public void listFilesFromGoogleDrive(Long userId) {

        CloudAccount googleAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.GOOGLE_DRIVE)
                .orElseThrow(() -> new RuntimeException("Google Drive account not found for user: " + userId));

        String url = "https://www.googleapis.com/drive/v3/files"
                + "?pageSize=100"
                + "&fields=files(id, name, mimeType, modifiedTime, size)";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + googleAccount.getAccessToken());
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            System.out.println("Files from Google Drive:");
            System.out.println(response.getBody());

        } catch (Exception e) {
            System.err.println("Failed to fetch file list: " + e.getMessage());
        }
    }

}
