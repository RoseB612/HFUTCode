package com.sintao.common.file.domain;

import lombok.Data;

@Data
public class MinioResult {

    private boolean success;

    private String name;

    private String key;

    private String url;

    private String bucketName;

    private long fileSize;
}
