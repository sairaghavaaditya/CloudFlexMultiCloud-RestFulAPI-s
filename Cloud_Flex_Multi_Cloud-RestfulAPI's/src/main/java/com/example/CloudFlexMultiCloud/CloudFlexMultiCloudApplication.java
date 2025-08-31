package com.example.CloudFlexMultiCloud;
import com.example.CloudFlexMultiCloud.service.googleDrive.GoogleFileServices;
import com.example.CloudFlexMultiCloud.service.oneDrive.OneDriveFileServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration; // Correct import for excluding SSL configuration
import org.springframework.boot.CommandLineRunner;

@SpringBootApplication(exclude = {SslAutoConfiguration.class}) // Only one @SpringBootApplication annotation needed
//@EntityScan("com.example.springbootreactintegration.model") // Specify the package where your entities are located
//@EnableJpaRepositories("com.example.springbootreactintegration.repository") // Specify the package for your repositories
public class CloudFlexMultiCloudApplication implements CommandLineRunner {
    //Services
    @Autowired
    private GoogleFileServices googleFileServices;
    @Autowired
    private OneDriveFileServices oneDriveFileServices;

    public static void main(String[] args) {
        SpringApplication.run(CloudFlexMultiCloudApplication.class, args);
    }
    @Override
    public void run(String... args) {
        Long userId = 1L;
//        googleFileServices.refreshGoogleAccessToken(userId);
//        googleFileServices.syncUserGoogleDriveMetadata(userId);
//
//        oneDriveFileServices.refreshOneDriveAccessToken(userId);
//        oneDriveFileServices.syncUserOneDriveMetadata(userId);
    }
}

