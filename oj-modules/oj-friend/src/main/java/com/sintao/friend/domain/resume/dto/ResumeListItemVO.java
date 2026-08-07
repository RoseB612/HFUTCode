package com.sintao.friend.domain.resume.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeListItemVO {

    private Long resumeId;

    private String originalFilename;

    private Integer analysisStatus;

    private Integer analysisScore;

    private String analysisSummary;

    private LocalDateTime createTime;
}
