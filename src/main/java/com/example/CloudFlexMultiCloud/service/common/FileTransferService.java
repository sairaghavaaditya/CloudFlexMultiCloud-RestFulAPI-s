package com.example.CloudFlexMultiCloud.service.common;

public interface FileTransferService {
    void transferFile(Long userId, String sourceProvider, String destinationProvider, String fileId, String destinationParentId);
}
