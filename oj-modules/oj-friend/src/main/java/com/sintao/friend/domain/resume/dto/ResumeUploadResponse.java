package com.sintao.friend.domain.resume.dto;

import lombok.Data;

@Data
public class ResumeUploadResponse {

    private Long resumeId;

    private String originalFilename;

    private String storageUrl;

    private Integer analysisStatus;

    private Integer analysisScore;

    private String analysisSummary;
}
