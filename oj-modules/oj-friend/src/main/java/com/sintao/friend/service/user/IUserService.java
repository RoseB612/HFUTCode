package com.sintao.friend.service.user;

import com.sintao.common.core.domain.R;
import com.sintao.common.core.domain.vo.LoginUserVO;
import com.sintao.friend.domain.user.dto.UserLoginDTO;
import com.sintao.friend.domain.user.dto.UserRegisterDTO;
import com.sintao.friend.domain.user.dto.UserUpdateDTO;
import com.sintao.friend.domain.user.vo.UserDashboardSummaryVO;
import com.sintao.friend.domain.user.vo.UserVO;

public interface IUserService {
    String register(UserRegisterDTO userRegisterDTO);

    String login(UserLoginDTO userLoginDTO);

    boolean logout(String token);

    R<LoginUserVO> info(String token);

    UserVO detail();

    UserDashboardSummaryVO dashboardSummary();

    int edit(UserUpdateDTO userUpdateDTO);

    int updateHeadImage(String headImage);
}

