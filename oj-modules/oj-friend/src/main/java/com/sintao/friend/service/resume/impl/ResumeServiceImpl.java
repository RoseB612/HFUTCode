package com.sintao.friend.service.resume.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sintao.common.core.constants.Constants;
import com.sintao.common.core.enums.ResultCode;
import com.sintao.common.core.utils.ThreadLocalUtil;
import com.sintao.common.file.domain.MinioResult;
import com.sintao.common.file.service.MinioService;
import com.sintao.common.security.exception.ServiceException;
import com.sintao.common.redis.service.RedisService;
import com.sintao.friend.domain.resume.ResumeAnalysisLog;
import com.sintao.friend.domain.resume.ResumeRecord;
import com.sintao.friend.domain.resume.dto.ResumeDetailVO;
import com.sintao.friend.domain.resume.dto.ResumeListItemVO;
import com.sintao.friend.domain.resume.dto.ResumeUploadResponse;
import com.sintao.friend.mapper.resume.ResumeAnalysisLogMapper;
import com.sintao.friend.mapper.resume.ResumeMapper;
import com.sintao.friend.service.resume.IResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements IResumeService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_DONE = 1;

    private final ResumeMapper resumeMapper;
    private final ResumeAnalysisLogMapper analysisLogMapper;
    private final MinioService minioService;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final Tika tika = new Tika();

    @Value("${resume.upload.max-file-size-mb:10}")
    private int maxFileSizeMb;

    @Value("${resume.upload.daily-limit:10}")
    private int uploadDailyLimit;

    @Value("${resume.reanalyze.daily-limit:20}")
    private int reanalyzeDailyLimit;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadResponse uploadAndAnalyze(MultipartFile file) {
        validateFile(file);
        Long userId = currentUserId();
        checkDailyLimit("upload", userId, uploadDailyLimit);

        MinioResult storage = minioService.uploadFile(file);
        String resumeText = parseText(file);
        ResumeAnalysisResult analysis = analyze(resumeText, file.getOriginalFilename());

        ResumeRecord record = new ResumeRecord();
        record.setUserId(userId);
        record.setOriginalFilename(file.getOriginalFilename());
        record.setStorageKey(storage.getKey());
        record.setStorageUrl(storage.getUrl());
        record.setFileSize(storage.getFileSize());
        record.setContentType(file.getContentType());
        record.setResumeText(resumeText);
        record.setAnalysisStatus(STATUS_PENDING);
        record.setAnalysisScore(analysis.score());
        record.setAnalysisSummary(analysis.summary());
        record.setStrengthsJson(writeJson(analysis.strengths()));
        record.setWeaknessesJson(writeJson(analysis.weaknesses()));
        record.setRecommendationsJson(writeJson(analysis.recommendations()));
        record.setAiModelName(analysis.aiModelName());
        record.setAnalysisStatus(STATUS_DONE);
        resumeMapper.insert(record);

        insertLog(record.getResumeId(), userId, analysis);
        return toUploadResponse(record);
    }

    @Override
    public List<ResumeListItemVO> listMyResumes() {
        Long userId = currentUserId();
        List<ResumeRecord> records = resumeMapper.selectList(new LambdaQueryWrapper<ResumeRecord>()
                .eq(ResumeRecord::getUserId, userId)
                .orderByDesc(ResumeRecord::getCreateTime));
        List<ResumeListItemVO> result = new ArrayList<>(records.size());
        for (ResumeRecord record : records) {
            result.add(toListItem(record));
        }
        return result;
    }

    @Override
    public ResumeDetailVO getDetail(Long resumeId) {
        ResumeRecord record = loadOwnedRecord(resumeId);
        return toDetail(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeDetailVO reanalyze(Long resumeId) {
        Long userId = currentUserId();
        ResumeRecord record = loadOwnedRecord(resumeId);
        if (!userId.equals(record.getUserId())) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }
        checkDailyLimit("reanalyze", userId, reanalyzeDailyLimit);

        ResumeAnalysisResult analysis = analyze(record.getResumeText(), record.getOriginalFilename());
        record.setAnalysisStatus(STATUS_DONE);
        record.setAnalysisScore(analysis.score());
        record.setAnalysisSummary(analysis.summary());
        record.setStrengthsJson(writeJson(analysis.strengths()));
        record.setWeaknessesJson(writeJson(analysis.weaknesses()));
        record.setRecommendationsJson(writeJson(analysis.recommendations()));
        record.setAiModelName(analysis.aiModelName());
        resumeMapper.updateById(record);

        insertLog(record.getResumeId(), userId, analysis);
        return toDetail(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long resumeId) {
        Long userId = currentUserId();
        ResumeRecord record = loadOwnedRecord(resumeId);
        if (!userId.equals(record.getUserId())) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }
        analysisLogMapper.delete(new LambdaQueryWrapper<ResumeAnalysisLog>()
                .eq(ResumeAnalysisLog::getResumeId, resumeId)
                .eq(ResumeAnalysisLog::getUserId, userId));
        return resumeMapper.deleteById(resumeId) > 0;
    }

    private ResumeRecord loadOwnedRecord(Long resumeId) {
        if (resumeId == null) {
            throw new ServiceException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        ResumeRecord record = resumeMapper.selectById(resumeId);
        if (record == null) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }
        return record;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        if (StrUtil.isBlank(file.getOriginalFilename())) {
            throw new ServiceException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        long maxSizeBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new ServiceException(ResultCode.FAILED_FILE_UPLOAD_TIME_LIMIT);
        }
    }

    private String parseText(MultipartFile file) {
        try {
            String text = tika.parseToString(file.getInputStream());
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.warn("resume parsing failed, falling back to empty text", e);
            return "";
        }
    }

    private ResumeAnalysisResult analyze(String resumeText, String fileName) {
        ResumeAnalysisResult aiResult = analyzeWithAi(resumeText, fileName);
        return aiResult != null ? aiResult : analyzeWithHeuristic(resumeText, fileName);
    }

    private ResumeAnalysisResult analyzeWithAi(String resumeText, String fileName) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null || StrUtil.isBlank(resumeText)) {
            return null;
        }

        String prompt = """
            Return strict JSON only.
            {
              "score": 0,
              "summary": "",
              "strengths": [],
              "weaknesses": [],
              "recommendations": [],
              "aiModelName": "spring-ai"
            }

            Resume file name: %s
            Resume content:
            %s
            """.formatted(fileName, resumeText);

        try {
            String content = builder.build().prompt().user(prompt).call().content();
            if (StrUtil.isBlank(content)) {
                return null;
            }
            return parseAiResult(content);
        } catch (Exception e) {
            log.warn("spring ai resume analysis failed, fallback to heuristic", e);
            return null;
        }
    }

    private ResumeAnalysisResult parseAiResult(String content) throws JsonProcessingException {
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        JsonNode root = objectMapper.readTree(normalized);
        return new ResumeAnalysisResult(
                root.path("score").asInt(70),
                root.path("summary").asText("resume analysis completed"),
                readStringList(root.path("strengths")),
                readStringList(root.path("weaknesses")),
                readStringList(root.path("recommendations")),
                root.path("aiModelName").asText("spring-ai"));
    }

    private ResumeAnalysisResult analyzeWithHeuristic(String resumeText, String fileName) {
        String normalized = resumeText == null ? "" : resumeText.toLowerCase(Locale.ROOT);
        int score = 60;
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        if (normalized.contains("java")) {
            score += 8;
            strengths.add("Java experience mentioned");
        }
        if (normalized.contains("spring")) {
            score += 8;
            strengths.add("Spring ecosystem experience mentioned");
        }
        if (normalized.contains("mysql") || normalized.contains("postgres") || normalized.contains("sql")) {
            score += 6;
            strengths.add("Database practice mentioned");
        }
        if (normalized.contains("redis")) {
            score += 4;
            strengths.add("Caching experience mentioned");
        }
        if (normalized.contains("docker") || normalized.contains("k8s")) {
            score += 4;
            strengths.add("Deployment or containerization experience mentioned");
        }
        if (normalized.length() < 500) {
            weaknesses.add("Resume content is short");
        } else {
            strengths.add("Resume content is sufficiently detailed");
        }
        if (!normalized.contains("quant")) {
            weaknesses.add("Missing quantified achievements");
        }
        recommendations.add("Add responsibilities, metrics, and outcomes for key projects");
        recommendations.add("Strengthen the latest role with business-impact details");

        String summary = "Initial resume analysis completed for " + (StrUtil.isBlank(fileName) ? "resume" : fileName);
        return new ResumeAnalysisResult(
                Math.min(score, 95),
                summary,
                strengths.isEmpty() ? List.of("Resume structure is basic but acceptable") : strengths,
                weaknesses.isEmpty() ? List.of("More project context could be added") : weaknesses,
                recommendations,
                "heuristic-fallback");
    }

    private void insertLog(Long resumeId, Long userId, ResumeAnalysisResult analysis) {
        ResumeAnalysisLog logRecord = new ResumeAnalysisLog();
        logRecord.setResumeId(resumeId);
        logRecord.setUserId(userId);
        logRecord.setAnalysisText(writeAnalysisText(analysis));
        logRecord.setAiModelName(analysis.aiModelName());
        analysisLogMapper.insert(logRecord);
    }

    private ResumeUploadResponse toUploadResponse(ResumeRecord record) {
        ResumeUploadResponse response = new ResumeUploadResponse();
        response.setResumeId(record.getResumeId());
        response.setOriginalFilename(record.getOriginalFilename());
        response.setStorageUrl(record.getStorageUrl());
        response.setAnalysisStatus(record.getAnalysisStatus());
        response.setAnalysisScore(record.getAnalysisScore());
        response.setAnalysisSummary(record.getAnalysisSummary());
        return response;
    }

    private ResumeListItemVO toListItem(ResumeRecord record) {
        ResumeListItemVO vo = new ResumeListItemVO();
        vo.setResumeId(record.getResumeId());
        vo.setOriginalFilename(record.getOriginalFilename());
        vo.setAnalysisStatus(record.getAnalysisStatus());
        vo.setAnalysisScore(record.getAnalysisScore());
        vo.setAnalysisSummary(record.getAnalysisSummary());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private ResumeDetailVO toDetail(ResumeRecord record) {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setResumeId(record.getResumeId());
        vo.setUserId(record.getUserId());
        vo.setOriginalFilename(record.getOriginalFilename());
        vo.setStorageKey(record.getStorageKey());
        vo.setStorageUrl(record.getStorageUrl());
        vo.setFileSize(record.getFileSize());
        vo.setContentType(record.getContentType());
        vo.setResumeText(record.getResumeText());
        vo.setAnalysisStatus(record.getAnalysisStatus());
        vo.setAnalysisScore(record.getAnalysisScore());
        vo.setAnalysisSummary(record.getAnalysisSummary());
        vo.setStrengths(readJsonList(record.getStrengthsJson()));
        vo.setWeaknesses(readJsonList(record.getWeaknessesJson()));
        vo.setRecommendations(readJsonList(record.getRecommendationsJson()));
        vo.setAiModelName(record.getAiModelName());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }

    private List<String> readJsonList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                if (item != null && !item.isNull()) {
                    values.add(item.asText());
                }
            });
            return values;
        }
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        return List.of();
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String writeAnalysisText(ResumeAnalysisResult analysis) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "score", analysis.score(),
                    "summary", analysis.summary(),
                    "strengths", analysis.strengths(),
                    "weaknesses", analysis.weaknesses(),
                    "recommendations", analysis.recommendations(),
                    "aiModelName", analysis.aiModelName()
            ));
        } catch (JsonProcessingException e) {
            return analysis.summary();
        }
    }

    private Long currentUserId() {
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_UNAUTHORIZED);
        }
        return userId;
    }

    private void checkDailyLimit(String action, Long userId, int limit) {
        if (limit <= 0) {
            return;
        }
        String key = "resume:limit:" + action + ":" + LocalDate.now() + ":" + userId;
        Long count = redisService.increment(key);
        if (count != null && count == 1L) {
            redisService.expire(key, secondsUntilTomorrow(), TimeUnit.SECONDS);
        }
        if (count != null && count > limit) {
            throw new ServiceException("upload".equals(action) ? ResultCode.FAILED_TIME_LIMIT : ResultCode.FAILED_FREQUENT);
        }
    }

    private long secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atStartOfDay();
        return Math.max(1, tomorrow.atZone(ZoneId.systemDefault()).toEpochSecond() - now.atZone(ZoneId.systemDefault()).toEpochSecond());
    }

    private record ResumeAnalysisResult(
            int score,
            String summary,
            List<String> strengths,
            List<String> weaknesses,
            List<String> recommendations,
            String aiModelName) {}
}
