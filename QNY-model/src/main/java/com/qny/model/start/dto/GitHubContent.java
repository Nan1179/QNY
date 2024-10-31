package com.qny.model.start.dto;

import lombok.Data;

import java.util.Map;

@Data
public class GitHubContent {
    private String name;
    private String path;
    private String sha;
    private int size;
    private String url;
    private String htmlUrl;
    private String gitUrl;
    private String downloadUrl;
    private String type;
    private Map<String, String> _links;
}