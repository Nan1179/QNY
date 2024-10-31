package com.qny.model.user.dtos;

import lombok.Data;

@Data
public class LoginDto {

    /**
     * 手机号
     */
    private String name;

    /**
     * 密码
     */
    private String password;
}
