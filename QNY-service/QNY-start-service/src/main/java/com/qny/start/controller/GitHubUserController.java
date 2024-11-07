package com.qny.start.controller;

import com.qny.model.response.Response;
import com.qny.model.start.dto.UserPageDto;
import com.qny.start.service.GitHubUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GitHub相关功能
 */
@RestController
@RequestMapping("/gUser")
public class GitHubUserController {

    @Autowired
    private GitHubUserService gitHubUserService;

    /**
     * 查询github用户（包含领域查询 模糊查询 分页）
     * @param dto
     * @return
     */
    @PostMapping("/search")
    public Response search(@RequestBody UserPageDto dto) {
        return gitHubUserService.search(dto);
    }

    /**
     * 添加指定github用户名的用户
     * @param map
     * @return
     */
    @PostMapping("/add")
    public Response addUser(@RequestBody Map map) {
        return gitHubUserService.addUser((String) map.get("login"));
    }

    /**
     * 猜测用户所在国家
     * @param map
     * @return
     */
    @PostMapping("/guessLocation")
    public Response guessLocation(@RequestBody Map map) {
        return gitHubUserService.guessLocation((String) map.get("login"));
    }

    /**
     * 更新等级阈值
     * @return
     */
    @PostMapping("/updateGrade")
    public Response updateGrade() {
        return gitHubUserService.updateGrade();
    }

    /**
     * 从redis中获取等级阈值
     * @return
     */
    @PostMapping("/grade")
    public Response getGrade() {
        return gitHubUserService.getGrade();
    }

    /**
     * 获得评估
     * @param map
     * @return
     */
    @PostMapping("/getEvaluate")
    public Response getEvaluate(@RequestBody Map map) {
        return gitHubUserService.getEvaluate((String) map.get("login"));

    }

    /**
     * 获取各个等级的人数（用于前端展示）
     * @return
     */
    @PostMapping("/getGradeCount")
    public Response getGradeCount() {
        return gitHubUserService.getGradeCount();
    }

    @PostMapping("/getUserInfo")
    public Response getUserInfo(@RequestBody Map map) {
        return gitHubUserService.getUserInfo((String) map.get("login"));
    }
}
