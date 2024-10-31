package com.qny.model.start.pojos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.List;

@Data
@TableName("user_info")
public class UserInfo {
    private String login;
    @TableId(value = "id", type = IdType.ID_WORKER)
    private Integer id;
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
    private String topic;
}
