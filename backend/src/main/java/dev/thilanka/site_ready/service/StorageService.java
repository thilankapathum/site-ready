package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.config.AppProperties;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final AppProperties appProperties;

    @PostConstruct
    public void initBucket() {
        String bucket = appProperties.minio().bucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", e.getMessage());
        }
    }

    public void upload(String objectKey, byte[] data, String contentType) {
        String bucket = appProperties.minio().bucket();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload to MinIO: " + objectKey, e);
        }
    }

    public byte[] download(String objectKey) {
        String bucket = appProperties.minio().bucket();
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download from MinIO: " + objectKey, e);
        }
    }

    public void delete(String objectKey) {
        String bucket = appProperties.minio().bucket();
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            log.warn("Failed to delete object {}: {}", objectKey, e.getMessage());
        }
    }

    /**
     * Build a deterministic storage key for a report version.
     * Pattern: reports/{namingKey}/V{version}/{type}.pdf
     */
    public String buildKey(String namingKey, int version, String type) {
        return String.format("reports/%s/V%d/%s.pdf", namingKey, version, type);
    }
}
