package com.sintao.friend.domain.resume.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ResumeDetailVO {

    private Long resumeId;

    private Long userId;

    private String originalFilename;

    private String storageKey;

    private String storageUrl;

    private Long fileSize;

    private String contentType;

    private String resumeText;

    private Integer analysisStatus;

    private Integer analysisScore;

    private String analysisSummary;

    private List<String> strengths;

    private List<String> weaknesses;

    private List<String> recommendations;

    private String aiModelName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
