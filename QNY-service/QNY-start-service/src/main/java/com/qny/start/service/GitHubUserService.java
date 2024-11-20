package com.qny.start.service;

import com.qny.model.response.Response;
import com.qny.model.start.dto.UserPageDto;

public interface GitHubUserService {

    /**
     * 分页查询 模糊查询（领域查询）
     * @param dto
     * @return
     */
    Response search(UserPageDto dto);

    /**
     * 添加指定login的GitHub用户
     * @param userName
     * @return
     */
    Response addUser(String userName);

    /**
     * 猜测GitHub用户nation
     * @param userName
     * @return
     */
    Response guessLocation(String userName);

    /**
     * 每周日凌晨四点 定时更新近两年活跃的用户的技术评分 取出等级判别的范围 存入redis中
     */
    void computeUserScore();

    /**
     * 更新技术评级阈值
     * @return
     */
    Response updateGrade();

    /**
     * 获得技术阈值
     * @return
     */
    Response getGrade();

    /**
     * 获得GitHub用户评估
     * @param login
     * @return
     */
    Response getEvaluate(String login);

    /**
     * 获取各等级人数
     * @return
     */
    Response getGradeCount();

    /**
     * 获取GitHub用户详细信息
     * @param login
     * @return
     */
    Response getUserInfo(String login);

    /**
     * 获取用户仓库
     * @param login
     * @return
     */
    Response getRepo(String login);

//    Response getRank(String login);
}
