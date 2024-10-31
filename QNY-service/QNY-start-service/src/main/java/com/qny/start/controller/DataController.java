package com.qny.start.controller;

import com.qny.model.response.Response;
import com.qny.start.service.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据加载
 */
@RestController
@RequestMapping("/data")
public class DataController {

    @Autowired
    private DataService dataService;

    /**
     * 加载指定数量的github用户
     * @param map
     * @return
     * @throws InterruptedException
     */
    @PostMapping("/loadUser")
    public Response loadUserData(@RequestBody Map map) throws InterruptedException {
        return dataService.loadUser((Integer) map.get("num"));
    }
}
