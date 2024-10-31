package com.qny.user.controller;

import com.qny.model.response.Response;
import com.qny.model.user.dtos.LoginDto;
import com.qny.user.service.UserService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 登录
     * @param dto
     * @return
     */
    @PostMapping("/login")
    public Response login(@RequestBody LoginDto dto) {
        return userService.login(dto);
    }

    /**
     * 注册
     * @param dto
     * @return
     */
    @PostMapping("/register")
    public Response register(@RequestBody LoginDto dto) {
        return userService.register(dto);
    }
}
