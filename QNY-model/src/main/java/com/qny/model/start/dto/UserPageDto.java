package com.qny.model.start.dto;

import com.qny.model.start.common.PageRequestDto;
import lombok.Data;

@Data
public class UserPageDto extends PageRequestDto {

    private String kindId;
    private String key;
}
