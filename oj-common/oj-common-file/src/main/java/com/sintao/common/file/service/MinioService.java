package com.sintao.common.file.service;

import cn.hutool.core.lang.ObjectId;
import cn.hutool.core.util.StrUtil;
import com.sintao.common.file.config.MinioProperties;
import com.sintao.common.file.domain.MinioResult;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioResult uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file cannot be empty");
        }

        String originalName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String objectKey = buildObjectKey(originalName);
        try (InputStream inputStream = file.getInputStream()) {
            ensureBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), 5 * 1024 * 1024)
                            .contentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"))
                            .build());
        } catch (Exception e) {
            log.error("MinIO upload failed, file={}", originalName, e);
            throw new IllegalStateException("file upload failed");
        }

        MinioResult result = new MinioResult();
        result.setSuccess(true);
        result.setName(originalName);
        result.setKey(objectKey);
        result.setBucketName(properties.getBucketName());
        result.setFileSize(file.getSize());
        result.setUrl(buildPublicUrl(objectKey));
        return result;
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.getBucketName())
                .build());
        if (!exists) {
            try {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.getBucketName())
                        .build());
            } catch (Exception e) {
                log.warn("MinIO bucket create skipped or failed for bucket={}", properties.getBucketName(), e);
            }
        }
    }

    public String buildPublicUrl(String objectKey) {
        if (StrUtil.isNotBlank(properties.getPublicBaseUrl())) {
            return trimSlash(properties.getPublicBaseUrl()) + "/" + trimSlash(properties.getBucketName()) + "/" + trimSlash(objectKey);
        }
        return trimSlash(properties.getEndpoint()) + "/" + trimSlash(properties.getBucketName()) + "/" + trimSlash(objectKey);
    }

    private String buildObjectKey(String originalName) {
        String suffix = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < originalName.length() - 1) {
            suffix = originalName.substring(dotIndex);
        }
        String prefix = trimSlash(properties.getPathPrefix());
        if (StrUtil.isNotBlank(prefix)) {
            return prefix + "/" + ObjectId.next() + suffix;
        }
        return ObjectId.next() + suffix;
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value;
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
