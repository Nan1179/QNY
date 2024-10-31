package com.qny.model.start.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

@Data
public class UserDto {
    private String login;
    private Integer id;
    private String nodeId;
    private String avatarUrl;
    private String gravatarId;
    private String url;
    private String htmlUrl;
    private String followersUrl;
    private String followingUrl;
    private String gistsUrl;
    private String starredUrl;
    private String subscriptionsUrl;
    private String organizationsUrl;
    private String reposUrl;
    private String eventsUrl;
    private String receivedEventsUrl;
    private String type;
    private String userViewType;
    private boolean siteAdmin;
    private String name;
    private String company;
    private String blog;
    private String location;
    private String email;
    private String bio;
    private String twitterUsername;
    private Integer publicRepos;
    private Integer publicGists;
    private Integer followers;
    private Integer following;
    private Date createdAt;
    private Date updatedAt;
}
