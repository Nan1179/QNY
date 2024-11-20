package com.qny.start.job;

import com.qny.start.service.DataService;
import com.qny.start.service.GitHubUserService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AddUserJob {

    @Autowired
    private DataService dataService;

    @XxlJob("addUserJob")
    public void handle() throws InterruptedException {
        log.info("用户自动添加任务开始执行...");
        dataService.loadUser(200);
        log.info("用户自动添加任务开始执行...");
    }
}
