package com.qny.model.start.pojos;

import lombok.Data;

@Data
public class Contributors {

    private String login;
    private int id;
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
    private int contributions;
}
