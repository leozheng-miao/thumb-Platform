package com.leo.thumbbackend.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$",
            message = "账号只能包含字母、数字和下划线，长度为 4-32 位")
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=\\S{8,32}$)(?=.*[A-Za-z])(?=.*\\d).*$",
            message = "密码长度为 8-32 位，且必须同时包含字母和数字")
    private String userPassword;
}
