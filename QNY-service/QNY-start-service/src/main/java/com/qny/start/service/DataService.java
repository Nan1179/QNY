package com.qny.start.service;

import com.qny.model.response.Response;

public interface DataService {

    /**
     * GitHub用户数据预处理和加载
     * @param num
     * @return
     * @throws InterruptedException
     */
    Response loadUser(Integer num) throws InterruptedException;
}
