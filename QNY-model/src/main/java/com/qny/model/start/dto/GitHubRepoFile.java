package com.qny.model.start.dto;

import lombok.Data;

import java.util.Map;

@Data
public class GitHubRepoFile {

    private String name;
    private String path;
    private String sha;
    private int size;
    private String url;
    private String htmlUrl;
    private String gitUrl;
    private String downloadUrl;
    private String type;
    private String content;
    private String encoding;
    private Map<String, String> _links;
}
