package com.fastdine.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fastdine.constant.MessageConstant;
import com.fastdine.dto.UserLoginDTO;
import com.fastdine.entity.User;
import com.fastdine.exception.LoginFailedException;
import com.fastdine.mapper.UserMapper;
import com.fastdine.properties.WeChatProperties;
import com.fastdine.service.UserService;
import com.fastdine.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户端微信登录。
 * <p>
 * 正常流程：小程序 wx.login 拿到 code → 后端用 code + appid + secret 调微信 jscode2session 换 openid
 * → 按 openid 查用户，没有就自动注册 → 签发用户端 JWT。
 * <p>
 * 降级逻辑：本项目在本地开发环境没有小程序 AppID（个人拿不到企业资质也不会去申请），
 * 因此当 fastdine.wechat.appid / secret 未配置时，直接返回一个固定的 mock openid，
 * 保证整条链路（登录 + 后续鉴权接口）在本地可以跑通。配置真实 AppID 后自动切回真实调用，
 * 不需要改代码。
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    /**
     * 微信登录凭证校验接口
     */
    private static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private UserMapper userMapper;

    /**
     * 未配置小程序信息时使用的固定 openid，保证本地反复登录始终命中同一个用户
     */
    @Value("${fastdine.wechat.mock-openid:local-mock-openid}")
    private String mockOpenid;

    /**
     * 降级提示只打印一次，避免每次登录刷日志
     */
    private volatile boolean mockWarned = false;

    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        String openid = getOpenid(userLoginDTO.getCode());
        if (openid == null || openid.trim().isEmpty()) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        openid = openid.trim();

        User user = userMapper.getByOpenid(openid);
        if (user == null) {
            //首次登录，自动注册
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            log.info("微信用户首次登录，已自动注册：id={}，openid={}", user.getId(), openid);
        }
        return user;
    }

    /**
     * 用 code 换取 openid，未配置小程序信息时走本地 mock
     *
     * @param code 小程序 wx.login 返回的临时凭证
     * @return openid，失败返回 null
     */
    private String getOpenid(String code) {
        String appid = weChatProperties.getAppid();
        String secret = weChatProperties.getSecret();

        if (isBlank(appid) || isBlank(secret)) {
            if (!mockWarned) {
                mockWarned = true;
                log.warn("未配置 fastdine.wechat.appid / secret，微信登录降级为本地 mock（openid={}），"
                        + "配置真实小程序信息后会自动走 jscode2session", mockOpenid);
            }
            return mockOpenid;
        }

        if (isBlank(code)) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", appid);
        paramMap.put("secret", secret);
        paramMap.put("js_code", code);
        paramMap.put("grant_type", "authorization_code");

        String json;
        try {
            json = HttpClientUtil.doGet(WX_LOGIN_URL, paramMap);
        } catch (Exception e) {
            log.error("调用微信登录接口失败：{}", e.getMessage());
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        JSONObject jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            log.error("微信登录接口返回为空");
            return null;
        }
        //微信在失败时会返回 errcode，例如 40029 code 无效、45011 频率限制
        Integer errcode = jsonObject.getInteger("errcode");
        if (errcode != null && errcode != 0) {
            log.error("微信登录失败：errcode={}，errmsg={}", errcode, jsonObject.getString("errmsg"));
            return null;
        }
        return jsonObject.getString("openid");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
