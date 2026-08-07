package com.sintao.friend.domain.resume;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sintao.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_resume_analysis_log")
public class ResumeAnalysisLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long logId;

    private Long resumeId;

    private Long userId;

    private String analysisText;

    private String aiModelName;
}
