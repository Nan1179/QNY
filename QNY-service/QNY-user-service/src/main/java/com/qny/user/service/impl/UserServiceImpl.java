package com.qny.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qny.utils.AppJwtUtil;
import com.qny.model.response.AppHttpCodeEnum;
import com.qny.model.response.Response;
import com.qny.model.user.dtos.LoginDto;
import com.qny.model.user.pojos.User;
import com.qny.user.mapper.UserMapper;
import com.qny.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Response login(LoginDto dto) {

        if (!StringUtils.isNotBlank(dto.getName()) || !StringUtils.isNotBlank((dto.getPassword()))) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);

        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getName, dto.getName());
        User user = userMapper.selectOne(lambdaQueryWrapper);

        if (user == null || user.getStatus() == 0) return Response.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);

        String password = dto.getPassword();
        String salt = user.getSalt();
        password = DigestUtils.md5DigestAsHex((password + salt).getBytes());

        if (!password.equals(user.getPassword())) return Response.errorResult(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);

        // 1.3.返回数据 JWT
        Map<String, Object> map = new HashMap<>();
        map.put("token", AppJwtUtil.getToken(user.getId().longValue()));
        user.setSalt("");
        user.setPassword("");
        map.put("user", user);

        return Response.okResult(map);
    }

    @Override
    public Response register(LoginDto dto) {
        if (!StringUtils.isNotBlank(dto.getName()) || !StringUtils.isNotBlank((dto.getPassword()))) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);

        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getName, dto.getName());
        User u = userMapper.selectOne(lambdaQueryWrapper);

        if (u != null) return Response.errorResult(AppHttpCodeEnum.DATA_EXIST);

        User user = new User();
        user.setStatus(1);
        user.setCreatedTime(new Date());
        user.setName(dto.getName());
        Random random = new Random();
        // salt
        Integer i = random.nextInt(1000);
        user.setSalt(i.toString());
        String password = DigestUtils.md5DigestAsHex((dto.getPassword() + i).getBytes());
        user.setPassword(password);

        userMapper.insert(user);

        // 返回数据 JWT
        Map<String, Object> map = new HashMap<>();
        map.put("token", AppJwtUtil.getToken(user.getId().longValue()));
        user.setSalt("");
        user.setPassword("");
        map.put("user", user);

        return Response.okResult(map);
    }
}
