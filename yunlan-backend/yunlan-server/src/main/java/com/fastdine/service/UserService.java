package com.fastdine.service;

import com.fastdine.dto.UserLoginDTO;
import com.fastdine.entity.User;

/**
 * 用户端（微信小程序）用户服务
 */
public interface UserService {

    /**
     * 微信登录：用 code 换 openid，首次登录自动注册
     *
     * @param userLoginDTO
     * @return 已存在的或新注册的用户
     */
    User wxLogin(UserLoginDTO userLoginDTO);
}
