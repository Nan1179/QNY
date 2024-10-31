package com.qny.start.job;

import com.qny.start.service.GitHubUserService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ComputeUserScoreJob {

    @Autowired
    private GitHubUserService gitHubUserService;

    @XxlJob("computeUserScoreJob")
    public void handle() {
        log.info("用户技术评估调度任务开始执行...");
        gitHubUserService.computeUserScore();
        log.info("用户技术评估调度任务结束...");
    }
}
