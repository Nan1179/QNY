package com.qny.model.start.common;

import lombok.Data;

import java.util.List;

@Data
public class KimiDto {

    private String model;

    private List<KimiMessage> messages;


}
