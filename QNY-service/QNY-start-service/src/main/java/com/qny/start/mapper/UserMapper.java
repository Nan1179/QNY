package com.qny.start.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qny.model.start.pojos.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
