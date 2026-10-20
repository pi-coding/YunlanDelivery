package com.fastdine.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * C端用户登录
 */
@Data
public class UserLoginDTO implements Serializable {

    /**
     * 小程序 wx.login() 返回的临时登录凭证
     */
    @NotBlank(message = "登录凭证不能为空")
    private String code;

}
