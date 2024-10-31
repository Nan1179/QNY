package com.qny.user.service;

import com.qny.model.response.Response;
import com.qny.model.user.dtos.LoginDto;

public interface UserService {
    Response login(LoginDto dto);

    Response register(LoginDto dto);
}
