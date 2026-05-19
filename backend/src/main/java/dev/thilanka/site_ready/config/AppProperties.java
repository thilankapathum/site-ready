package dev.thilanka.site_ready.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        JwtProperties jwt,
        MinioProperties minio,
        SigningProperties signing,
        String baseUrl
) {
    public record JwtProperties(String secret, long expiryMs) {}

    public record MinioProperties(
            String endpoint,
            String accessKey,
            String secretKey,
            String bucket
    ) {}

    public record SigningProperties(
            String keystorePath,
            String keystorePassword,
            String alias
    ) {}
}
