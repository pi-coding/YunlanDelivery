package com.fastdine.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 修改密码
 */
@Data
public class EmployeePasswordDTO implements Serializable {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{6,20}$", message = "新密码为 6-20 位数字或字母")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String affirmPassword;
}
