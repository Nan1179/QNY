package com.qny.start.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qny.model.start.pojos.UserInfo;
import org.mapstruct.Mapper;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
