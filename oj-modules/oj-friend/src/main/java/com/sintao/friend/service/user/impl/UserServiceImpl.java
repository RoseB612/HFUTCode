package com.sintao.friend.service.user.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sintao.common.core.constants.Constants;
import com.sintao.common.core.constants.HttpConstants;
import com.sintao.common.core.domain.LoginUser;
import com.sintao.common.core.domain.R;
import com.sintao.common.core.domain.vo.LoginUserVO;
import com.sintao.common.core.enums.ResultCode;
import com.sintao.common.core.enums.UserIdentity;
import com.sintao.common.core.enums.UserStatus;
import com.sintao.common.core.utils.ThreadLocalUtil;
import com.sintao.common.security.exception.ServiceException;
import com.sintao.common.security.service.TokenService;
import com.sintao.friend.domain.user.User;
import com.sintao.friend.domain.user.dto.UserLoginDTO;
import com.sintao.friend.domain.user.dto.UserRegisterDTO;
import com.sintao.friend.domain.user.dto.UserUpdateDTO;
import com.sintao.friend.domain.user.vo.UserDashboardSummaryVO;
import com.sintao.friend.domain.user.vo.UserHeatmapPointVO;
import com.sintao.friend.domain.user.vo.UserVO;
import com.sintao.friend.manager.UserCacheManager;
import com.sintao.friend.mapper.user.UserMapper;
import com.sintao.friend.mapper.user.UserSubmitMapper;
import com.sintao.friend.service.user.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final String DEFAULT_NICK_NAME = "SynCoder";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserCacheManager userCacheManager;

    @Autowired
    private UserSubmitMapper userSubmitMapper;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${file.minio.public-base-url:}")
    private String downloadUrl;

    @Override
    @Transactional
    public String register(UserRegisterDTO userRegisterDTO) {
        String email = normalizeEmail(userRegisterDTO.getEmail());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user != null) {
            throw new ServiceException(ResultCode.FAILED_USER_EXISTS);
        }

        user = new User();
        user.setEmail(email);
        user.setPassword(PASSWORD_ENCODER.encode(userRegisterDTO.getPassword()));
        user.setNickName(DEFAULT_NICK_NAME);
        user.setStatus(UserStatus.Normal.getValue());
        user.setCreateBy(Constants.SYSTEM_USER_ID);
        try {
            userMapper.insert(user);
        } catch (DataIntegrityViolationException exception) {
            log.info("register rejected because email already exists, email={}", email);
            throw new ServiceException(ResultCode.FAILED_USER_EXISTS);
        }

        return createToken(user);
    }

    @Override
    public String login(UserLoginDTO userLoginDTO) {
        String email = normalizeEmail(userLoginDTO.getEmail());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null || !PASSWORD_ENCODER.matches(userLoginDTO.getPassword(), user.getPassword())) {
            throw new ServiceException(ResultCode.FAILED_LOGIN);
        }
        if (Objects.equals(UserStatus.Block.getValue(), user.getStatus())) {
            throw new ServiceException(ResultCode.FAILED_USER_BANNED);
        }
        return createToken(user);
    }

    private String createToken(User user) {
        return tokenService.createToken(
                user.getUserId(),
                secret,
                UserIdentity.ORDINARY.getValue(),
                user.getNickName(),
                user.getHeadImage()
        );
    }

    @Override
    public boolean logout(String token) {
        if (StrUtil.isNotEmpty(token) && token.startsWith(HttpConstants.PREFIX)) {
            token = token.replaceFirst(HttpConstants.PREFIX, StrUtil.EMPTY);
        }
        return tokenService.deleteLoginUser(token, secret);
    }

    @Override
    public R<LoginUserVO> info(String token) {
        if (StrUtil.isNotEmpty(token) && token.startsWith(HttpConstants.PREFIX)) {
            token = token.replaceFirst(HttpConstants.PREFIX, StrUtil.EMPTY);
        }
        LoginUser loginUser = tokenService.getLoginUser(token, secret);
        if (loginUser == null) {
            return R.fail();
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setNickName(loginUser.getNickName());
        if (StrUtil.isNotEmpty(loginUser.getHeadImage())) {
            loginUserVO.setHeadImage(downloadUrl + loginUser.getHeadImage());
        }
        return R.ok(loginUserVO);
    }

    @Override
    public UserVO detail() {
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        UserVO userVO = userCacheManager.getUserById(userId);
        if (userVO == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        if (StrUtil.isNotEmpty(userVO.getHeadImage())) {
            userVO.setHeadImage(downloadUrl + userVO.getHeadImage());
        }
        return userVO;
    }

    @Override
    public UserDashboardSummaryVO dashboardSummary() {
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }

        LocalDateTime sinceTime = LocalDateTime.now().minusDays(180);
        List<UserHeatmapPointVO> heatmap = userSubmitMapper.selectHeatmap(userId, sinceTime);
        heatmap.sort(Comparator.comparing(UserHeatmapPointVO::getStudyDate).reversed());

        UserDashboardSummaryVO summaryVO = new UserDashboardSummaryVO();
        summaryVO.setSolvedCount(defaultZero(userSubmitMapper.selectSolvedQuestionCount(userId)));
        summaryVO.setSubmissionCount(defaultZero(userSubmitMapper.selectSubmissionCount(userId)));
        summaryVO.setHeatmap(heatmap);
        summaryVO.setStreakDays(calculateStreakDays(heatmap));
        return summaryVO;
    }

    @Override
    public int edit(UserUpdateDTO userUpdateDTO) {
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        user.setNickName(userUpdateDTO.getNickName());
        user.setSex(userUpdateDTO.getSex());
        user.setSchoolName(userUpdateDTO.getSchoolName());
        user.setMajorName(userUpdateDTO.getMajorName());
        user.setPhone(userUpdateDTO.getPhone());
        user.setWechat(userUpdateDTO.getWechat());
        user.setIntroduce(userUpdateDTO.getIntroduce());
        userCacheManager.refreshUser(user);
        tokenService.refreshLoginUser(
                user.getNickName(),
                user.getHeadImage(),
                ThreadLocalUtil.get(Constants.USER_KEY, String.class)
        );
        return userMapper.updateById(user);
    }

    @Override
    public int updateHeadImage(String headImage) {
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        user.setHeadImage(headImage);
        userCacheManager.refreshUser(user);
        tokenService.refreshLoginUser(
                user.getNickName(),
                user.getHeadImage(),
                ThreadLocalUtil.get(Constants.USER_KEY, String.class)
        );
        return userMapper.updateById(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer calculateStreakDays(List<UserHeatmapPointVO> heatmap) {
        if (heatmap == null || heatmap.isEmpty()) {
            return 0;
        }

        int streak = 0;
        LocalDateTime cursor = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<String> activeDays = heatmap.stream()
                .map(UserHeatmapPointVO::getStudyDate)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        for (String activeDay : activeDays) {
            String expected = cursor.toLocalDate().toString();
            if (expected.equals(activeDay)) {
                streak += 1;
                cursor = cursor.minusDays(1);
                continue;
            }
            if (streak == 0 && cursor.minusDays(1).toLocalDate().toString().equals(activeDay)) {
                streak += 1;
                cursor = cursor.minusDays(2);
                continue;
            }
            break;
        }
        return streak;
    }
}
