package com.example.CloudFlexMultiCloud.service.oneDrive;

import com.example.CloudFlexMultiCloud.dao.CloudFileRepository;
import com.example.CloudFlexMultiCloud.model.CloudAccount;
import com.example.CloudFlexMultiCloud.model.CloudFile;
import com.example.CloudFlexMultiCloud.model.CloudProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.example.CloudFlexMultiCloud.dao.CloudAccountRepository;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;



@Service
public class OneDriveFileServicesImpl implements OneDriveFileServices{

    @Autowired
    private CloudAccountRepository cloudAccountRepository;

    @Autowired
    private CloudFileRepository cloudFileRepository;

    private final RestTemplate restTemplate = new RestTemplate();


    public void refreshOneDriveAccessToken(Long userId){
        CloudAccount onedriveAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.ONEDRIVE)
                .orElseThrow(() -> new RuntimeException("OneDrive account not found for user: " + userId));
        String tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", onedriveAccount.getClientId());
        form.add("client_secret", onedriveAccount.getClientSecret());
        form.add("refresh_token", onedriveAccount.getRefreshToken());
        form.add("grant_type", "refresh_token");
        form.add("scope", "https://graph.microsoft.com/.default");



        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String newAccessToken = (String) response.getBody().get("access_token");
                onedriveAccount.setAccessToken(newAccessToken);
                System.out.println("New Access Token: " + newAccessToken);
                try {
                    cloudAccountRepository.save(onedriveAccount);
                }catch (Exception e){
                    System.out.println("Failed to store One Drive Access Token in Database");
                }
            } else {
                System.out.println("Failed to refresh token. Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error refreshing OneDrive token: " + e.getMessage());
        }

    }

    @Override
    @Transactional
    public void syncUserOneDriveMetadata(Long userId) {
        CloudAccount oneDriveAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.ONEDRIVE)
                .orElseThrow(() -> new RuntimeException("OneDrive account not found for user: " + userId));

        String accessToken = oneDriveAccount.getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        List<CloudFile> files = new ArrayList<>();

        // Start recursive fetching from root
        fetchOneDriveFilesRecursively("root", oneDriveAccount, headers, files);


        try {
            // Remove old records
            cloudFileRepository.deleteByCloudAccountIdAndCloudAccountUserId(oneDriveAccount.getId(), userId);

            // Save all updated metadata
            cloudFileRepository.saveAll(files);
        }catch (Exception e){
            System.out.println("Failed to Sync data of One Drive with Database");
        }
    }
    private void fetchOneDriveFilesRecursively(String folderId, CloudAccount account, HttpHeaders headers, List<CloudFile> files) {
        String url = "https://graph.microsoft.com/v1.0/me/drive/items/" + folderId + "/children";
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                JsonNode items = root.path("value");


                for (JsonNode item : items) {
                    String parentId = null;


                    CloudFile file = new CloudFile();
                    file.setCloudAccount(account);
                    file.setFileId(item.get("id").asText());
                    file.setFileName(item.get("name").asText());
                    file.setMimeType(item.has("file") ? item.path("file").path("mimeType").asText() : "folder");
                    file.setIsFolder(item.has("folder"));
                    file.setSize(item.has("size") ? item.get("size").asLong() : 0L);
                    file.setModifiedTime(item.has("lastModifiedDateTime")
                            ? OffsetDateTime.parse(item.get("lastModifiedDateTime").asText()).toLocalDateTime()
                            : null);

                    file.setParentId(item.has("parentReference") && item.get("parentReference").has("id")
                            ? item.get("parentReference").get("id").asText()
                            : null);
                    file.setIsTrashed(false); // Deleted object = trashed

                    files.add(file);
                    // Recurse into folder
                    if (file.getIsFolder()) {
                        fetchOneDriveFilesRecursively(file.getFileId(), account, headers, files);
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException("Error parsing OneDrive response: " + e.getMessage(), e);
            }
        } else {
            System.err.println("OneDrive API error: " + response.getStatusCode());
        }
    }




    public void uploadFileToOneDrive(MultipartFile multipartFile, Long userId, String parentFolderId) {
        CloudAccount oneDriveAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.ONEDRIVE)
                .orElseThrow(() -> new RuntimeException("OneDrive account not found for user: " + userId));
        String fileName = multipartFile.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            System.err.println("Invalid file name");
            return;
        }
        String uploadUrl;
        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            uploadUrl = "https://graph.microsoft.com/v1.0/me/drive/items/" + parentFolderId + ":/" + fileName + ":/content";
        } else {
            uploadUrl = "https://graph.microsoft.com/v1.0/me/drive/root:/" + fileName + ":/content";
        }
        try {
            byte[] fileBytes = multipartFile.getBytes(); //  Directly get bytes from MultipartFile

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + oneDriveAccount.getAccessToken()); // Correct access token
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM); // Generic binary

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileBytes, headers);

            RestTemplate restTemplate = new RestTemplate(); // Only once
            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.PUT,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("File Uploaded to OneDrive successful!"+fileName);


                try {
                    // Parse OneDrive response JSON to get metadata
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonResponse = mapper.readTree(response.getBody());

                    String fileId = jsonResponse.get("id").asText();
                    String returnedFileName = jsonResponse.get("name").asText();
                    String parentId = jsonResponse.has("parentReference") ?
                            jsonResponse.get("parentReference").get("id").asText() : null;
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
                }catch (Exception e){
                    System.out.println("File Uploaded but Failed to sync metadata with One Drive upload");
                }

            } else {
                throw new RuntimeException("Upload failed with status: " + response.getStatusCode());
            }

        } catch (IOException e) {
            System.err.println(" File read error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println(" Upload failed: " + e.getMessage());
        }
    }





    @Transactional
    public void deleteFileFromOneDrive(String fileId,Long userId) {
        CloudAccount oneDriveAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.ONEDRIVE)
                .orElseThrow(() -> new RuntimeException("OneDrive account not found for user: " + userId));
        String deleteUrl = "https://graph.microsoft.com/v1.0/me/drive/items/" + fileId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + oneDriveAccount.getAccessToken());

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<Void> response = restTemplate.exchange(deleteUrl, HttpMethod.DELETE, requestEntity, Void.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println(" File deleted successfully from OneDrive.");
                try {
//                    CloudFile file = cloudFileRepository.findByFileId(fileId)
//                            .orElseThrow(() -> new RuntimeException("File metadata not found"));
//                    file.setIsTrashed(true);
//                    cloudFileRepository.save(file);
                      cloudFileRepository.deleteByFileIdAndCloudAccountUserId(fileId,userId);
                }catch (Exception e){
                    System.out.println("File deleted but Failed to sync metadata with One drive deletion");
                }
            } else {
                System.err.println(" Failed to delete file. Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error deleting file: " + e.getMessage());
        }
    }







    //this is not need because we are already syncing the metadata with database tables
    public void OneDriveListallfiles(Long userId) {
        CloudAccount oneDriveAccount = cloudAccountRepository
                .findByUserIdAndProvider(userId, CloudProvider.ONEDRIVE)
                .orElseThrow(() -> new RuntimeException("OneDrive account not found for user: " + userId));
        String url = "https://graph.microsoft.com/v1.0/me/drive/root/children";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + oneDriveAccount.getAccessToken());
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("Files from OneDrive:");
                System.out.println(response.getBody());
            } else {
                System.out.println("Failed to fetch files. Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error while fetching OneDrive files: " + e.getMessage());
        }
    }
}
