package com.qny.model.start.pojos;

import lombok.Data;

@Data
public class Events {

    private String id;

    private Repo repo;

    @Data
    public class Repo {

        private long id;
        private String name;
        private String url;
    }
}
