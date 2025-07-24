package com.example.CloudFlexMultiCloud.controller;

import com.example.CloudFlexMultiCloud.service.googleDrive.GoogleFileServices;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;



@RestController
@RequestMapping("/google/drive")
public class GooglefileController {

    @Autowired
    GoogleFileServices googleFileServices;


@PostMapping("/fileUpload")
public ResponseEntity<String> uploadFileToDrive(
        @RequestParam("file") MultipartFile file,
        @RequestParam("userId") Long userId,
        @RequestParam(value = "parentFolderId", required = false) String parentFolderId){
    try{
        googleFileServices.uploadFileToGoogleDrive(file,userId,parentFolderId);
        return ResponseEntity.ok("File Uploaded Successfully");
    }catch(Exception e){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Upload failed: "+e.getMessage());
    }
}


@PostMapping("/fileDelete")
public ResponseEntity<String> deleteFilefromDrive(@RequestParam("fileId") String fileId,@RequestParam("userId") Long userId){
    try{
        googleFileServices.deleteFileFromGoogleDrive(fileId,userId);
        return ResponseEntity.ok("File Deleted Successfully");
    }catch(Exception e){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Delete failed: "+ e.getMessage());
    }

}


@PostMapping("/filetrash")
public ResponseEntity<String> moveFileToTrash(
        @RequestParam String fileId,
        @RequestParam Long userId
) {
    try {
        System.out.println("Requested");
        googleFileServices.moveFileToTrashInGoogleDrive(fileId, userId);
        return ResponseEntity.ok("File moved to trash successfully.");
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to move file to trash: " + e.getMessage());
    }
}

















}
