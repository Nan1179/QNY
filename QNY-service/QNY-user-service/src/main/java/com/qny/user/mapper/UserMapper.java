package com.qny.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qny.model.user.pojos.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
