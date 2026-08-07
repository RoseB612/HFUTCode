package com.sintao.friend.controller.resume;

import com.sintao.common.core.controller.BaseController;
import com.sintao.common.core.domain.R;
import com.sintao.friend.domain.resume.dto.ResumeDetailVO;
import com.sintao.friend.domain.resume.dto.ResumeListItemVO;
import com.sintao.friend.domain.resume.dto.ResumeUploadResponse;
import com.sintao.friend.service.resume.IResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/resume")
@Tag(name = "Resume Analysis", description = "Resume upload, parsing, AI analysis and history APIs")
public class ResumeController extends BaseController {

    private final IResumeService resumeService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and analyze a resume")
    @ApiResponse(responseCode = "200", description = "Returns the resume analysis result")
    public R<ResumeUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return R.ok(resumeService.uploadAndAnalyze(file));
    }

    @GetMapping("/list")
    @Operation(summary = "List current user's resumes")
    public R<List<ResumeListItemVO>> list() {
        return R.ok(resumeService.listMyResumes());
    }

    @GetMapping("/{resumeId}")
    @Operation(summary = "Get resume detail")
    public R<ResumeDetailVO> detail(@PathVariable Long resumeId) {
        return R.ok(resumeService.getDetail(resumeId));
    }

    @PostMapping("/{resumeId}/reanalyze")
    @Operation(summary = "Reanalyze a resume")
    public R<ResumeDetailVO> reanalyze(@PathVariable Long resumeId) {
        return R.ok(resumeService.reanalyze(resumeId));
    }

    @DeleteMapping("/{resumeId}")
    @Operation(summary = "Delete a resume")
    public R<Void> delete(@PathVariable Long resumeId) {
        return toR(resumeService.delete(resumeId));
    }

    @GetMapping("/health")
    @Operation(summary = "Resume service health check")
    public R<String> health() {
        return R.ok("ok");
    }
}
