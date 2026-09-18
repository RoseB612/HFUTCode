package com.sintao.friend.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sintao.common.core.enums.ResultCode;
import com.sintao.common.core.enums.UserStatus;
import com.sintao.common.security.exception.ServiceException;
import com.sintao.common.security.service.TokenService;
import com.sintao.friend.domain.user.User;
import com.sintao.friend.domain.user.dto.UserLoginDTO;
import com.sintao.friend.domain.user.dto.UserRegisterDTO;
import com.sintao.friend.manager.UserCacheManager;
import com.sintao.friend.mapper.user.UserMapper;
import com.sintao.friend.mapper.user.UserSubmitMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserCacheManager userCacheManager;

    @Mock
    private UserSubmitMapper userSubmitMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "secret", "test-secret");
        ReflectionTestUtils.setField(userService, "downloadUrl", "https://cdn.example.com/");
    }

    @Test
    void registerShouldNormalizeEmailHashPasswordAndReturnToken() {
        UserRegisterDTO request = registerRequest(" User@Example.com ", "SynCode123!");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            invocation.<User>getArgument(0).setUserId(1001L);
            return 1;
        });
        when(tokenService.createToken(any(), anyString(), any(), any(), any())).thenReturn("token-1");

        String token = userService.register(request);

        assertEquals("token-1", token);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User insertedUser = userCaptor.getValue();
        assertEquals("user@example.com", insertedUser.getEmail());
        assertEquals("SynCoder", insertedUser.getNickName());
        assertEquals(UserStatus.Normal.getValue(), insertedUser.getStatus());
        assertNotEquals(request.getPassword(), insertedUser.getPassword());
        assertTrue(new BCryptPasswordEncoder().matches(request.getPassword(), insertedUser.getPassword()));
    }

    @Test
    void registerShouldRejectExistingEmail() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new User());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.register(registerRequest("user@example.com", "SynCode123!"))
        );

        assertEquals(ResultCode.FAILED_USER_EXISTS, exception.getResultCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void loginShouldReturnTokenForCorrectPassword() {
        User user = user("user@example.com", "SynCode123!", UserStatus.Normal.getValue());
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(tokenService.createToken(any(), anyString(), any(), any(), any())).thenReturn("token-2");

        String token = userService.login(loginRequest(" USER@example.com ", "SynCode123!"));

        assertEquals("token-2", token);
    }

    @Test
    void loginShouldReturnSameErrorForMissingUserAndWrongPassword() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(user("user@example.com", "Correct123!", UserStatus.Normal.getValue()));

        ServiceException missingUser = assertThrows(
                ServiceException.class,
                () -> userService.login(loginRequest("missing@example.com", "Wrong123!"))
        );
        ServiceException wrongPassword = assertThrows(
                ServiceException.class,
                () -> userService.login(loginRequest("user@example.com", "Wrong123!"))
        );

        assertEquals(ResultCode.FAILED_LOGIN, missingUser.getResultCode());
        assertEquals(ResultCode.FAILED_LOGIN, wrongPassword.getResultCode());
    }

    @Test
    void loginShouldRejectBlockedUser() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(user("user@example.com", "SynCode123!", UserStatus.Block.getValue()));

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> userService.login(loginRequest("user@example.com", "SynCode123!"))
        );

        assertEquals(ResultCode.FAILED_USER_BANNED, exception.getResultCode());
        verify(tokenService, never()).createToken(any(), anyString(), any(), any(), any());
    }

    private UserRegisterDTO registerRequest(String email, String password) {
        UserRegisterDTO request = new UserRegisterDTO();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private UserLoginDTO loginRequest(String email, String password) {
        UserLoginDTO request = new UserLoginDTO();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private User user(String email, String rawPassword, Integer status) {
        User user = new User();
        user.setUserId(1001L);
        user.setEmail(email);
        user.setPassword(new BCryptPasswordEncoder().encode(rawPassword));
        user.setNickName("Tester");
        user.setStatus(status);
        return user;
    }
}
