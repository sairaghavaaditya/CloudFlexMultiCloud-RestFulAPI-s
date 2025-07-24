package com.example.CloudFlexMultiCloud.controller;

import com.example.CloudFlexMultiCloud.service.oneDrive.OneDriveFileServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/oneDrive")
public class OnedriveFileController {

    @Autowired
    OneDriveFileServices oneDriveFileServices;

    @PostMapping("/fileUpload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "parentFolderId",required = false)String parentFolderId){
        try{
            oneDriveFileServices.uploadFileToOneDrive(file,userId,parentFolderId);
            return ResponseEntity.ok("File Uploaded Successfully to OneDrive");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload Failed: "+e.getMessage());
        }
    }



    @PostMapping("/fileDelete")
    public ResponseEntity<String> deleteFile(@RequestParam("fileId") String fileId,@RequestParam("userId") Long userId){
        try{
            oneDriveFileServices.deleteFileFromOneDrive(fileId,userId);
            return ResponseEntity.ok("File deleted Successfully from oneDrive");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Delete Failed: "+e.getMessage());
        }
    }


}
