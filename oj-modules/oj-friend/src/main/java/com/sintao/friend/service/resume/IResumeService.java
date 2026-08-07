package com.sintao.friend.service.resume;

import com.sintao.friend.domain.resume.dto.ResumeDetailVO;
import com.sintao.friend.domain.resume.dto.ResumeListItemVO;
import com.sintao.friend.domain.resume.dto.ResumeUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IResumeService {

    ResumeUploadResponse uploadAndAnalyze(MultipartFile file);

    List<ResumeListItemVO> listMyResumes();

    ResumeDetailVO getDetail(Long resumeId);

    ResumeDetailVO reanalyze(Long resumeId);

    boolean delete(Long resumeId);
}
