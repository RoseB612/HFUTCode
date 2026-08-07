package com.sintao.friend.domain.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sintao.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_resume")
public class ResumeRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
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

    private String strengthsJson;

    private String weaknessesJson;

    private String recommendationsJson;

    private String aiModelName;
}
