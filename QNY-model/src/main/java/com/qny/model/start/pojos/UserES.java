package com.qny.model.start.pojos;

import lombok.Data;

import java.util.Date;

@Data
public class UserES {

    private Integer id;

    private String login;

    private String location;

    private String avatar_url;

    private Integer score;

    private String type;

    private String company;

    private String email;

    private String blog;

    private String name;

    private String html_url;

    private String topic;

    private Date created_at;

    private Date updated_at;

}
