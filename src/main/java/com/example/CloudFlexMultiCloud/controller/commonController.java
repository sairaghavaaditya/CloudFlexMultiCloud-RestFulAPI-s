package com.example.CloudFlexMultiCloud.controller;

import com.example.CloudFlexMultiCloud.service.common.FileTransferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cloud")
public class commonController {

    @Autowired
    private FileTransferService fileTransferService;

    @PostMapping("/transfer")
    public ResponseEntity<String> transferFile(
            @RequestParam("userId") Long userId,
            @RequestParam("sourceProvider") String sourceProvider,
            @RequestParam("destinationProvider") String destinationProvider,
            @RequestParam("fileId") String sourceFileId,
            @RequestParam(value = "destinationParentId",required = false) String destinationParentId
    ) {
        try {
            fileTransferService.transferFile(userId, sourceProvider, destinationProvider, sourceFileId, destinationParentId);
            return ResponseEntity.ok("File transferred successfully from " + sourceProvider + " to " + destinationProvider);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Transfer failed: " + e.getMessage());
        }
    }

}
