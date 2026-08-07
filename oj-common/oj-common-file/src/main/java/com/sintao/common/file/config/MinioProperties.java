package com.sintao.common.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "file.minio")
public class MinioProperties {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucketName;

    private String publicBaseUrl;

    private String pathPrefix = "resume/";
}
