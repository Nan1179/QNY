package com.qny.start.service.impl;

import com.alibaba.fastjson.JSON;
import com.qny.utils.GitHubUtils;

import com.qny.model.start.dto.UserDto;
import com.qny.model.start.pojos.Repos;
import com.qny.model.start.pojos.User;
import com.qny.model.start.pojos.UserES;
import com.qny.model.start.pojos.UserInfo;
import com.qny.model.response.AppHttpCodeEnum;
import com.qny.model.response.Response;
import com.qny.start.mapper.UserInfoMapper;
import com.qny.start.mapper.UserMapper;
import com.qny.start.service.DataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@Transactional
public class DataServiceImpl implements DataService {

    private int THREAD_NUM = 10;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private RestHighLevelClient client;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 加锁
     * @param name
     * @param expire
     * @return
     */
    public String tryLock(String name, long expire) {
        name = name + "_lock";
        String token = UUID.randomUUID().toString();
        RedisConnectionFactory factory = stringRedisTemplate.getConnectionFactory();
        RedisConnection conn = factory.getConnection();
        try {
            //参考redis命令：
            //set key value [EX seconds] [PX milliseconds] [NX|XX]
            Boolean result = conn.set(
                    name.getBytes(),
                    token.getBytes(),
                    Expiration.from(expire, TimeUnit.MILLISECONDS),
                    RedisStringCommands.SetOption.SET_IF_ABSENT //NX
            );
            if (result != null && result)
                return token;
        } finally {
            RedisConnectionUtils.releaseConnection(conn, factory,false);
        }
        return null;
    }

    @Override
    public Response loadUser(Integer num) throws InterruptedException {

        if (num == null) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUM);

        List<User> users = GitHubUtils.getUser(num);

        for (User user : users) {
            // 为了防止出现线程问题，导致重复添加user 使用redis做分布式锁
            String token = tryLock("User" + user.getId(), 1000 * 5);
            if (StringUtils.isNotBlank(token)) {
                User select = userMapper.selectById(user.getId());
                if (select != null) continue;
                UserDto userDto = GitHubUtils.getUserInfo(user.getLogin());
                UserInfo userInfo = new UserInfo();
                if (userDto == null) continue;
                BeanUtils.copyProperties(userDto, userInfo);
                if (user.getId() == null || userInfo.getId() == null) continue;


                executor.submit((Callable<Void>) () -> {
                    try {
                        user.setGrade("未评级");
                        user.setScore(GitHubUtils.getScore(user.getLogin()));
                        user.setUpdatedAt(userInfo.getUpdatedAt());
                        user.setCreatedAt(userInfo.getCreatedAt());

                        // 计算某用户的领域
                        Map<String, Integer> frequencyMap = new HashMap<>();
                        for (Repos repos : GitHubUtils.getRepo(user.getLogin())) {
                            for (String topic : repos.getTopics()) {
                                frequencyMap.put(topic, frequencyMap.getOrDefault(topic, 0) + 1);
                            }
                        }

                        // 使用优先队列来找出出现次数最多的前五个字符串
                        // 这里使用了一个小技巧，我们对频率进行降序排序，然后取前五个
                        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>(
                                (a, b) -> b.getValue().compareTo(a.getValue()) // 按频率降序排列
                        );
                        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
                            pq.offer(entry);
                            if (pq.size() > 5) {
                                pq.poll(); // 保持队列大小不超过5
                            }
                        }

                        // 将结果转换为List
                        List<String> topFiveFrequent = new ArrayList<>();
                        while (!pq.isEmpty()) {
                            topFiveFrequent.add(0, pq.poll().getKey());
                        }

                        userInfo.setTopic(topFiveFrequent.toString());

                        // 插入ES
                        UserES userES = new UserES();
                        BeanUtils.copyProperties(user, userES);
                        BeanUtils.copyProperties(userInfo, userES);
                        userES.setAvatar_url(user.getAvatarUrl());
                        userES.setHtml_url(user.getHtmlUrl());
                        userES.setCreated_at(userInfo.getCreatedAt());
                        userES.setUpdated_at(userInfo.getUpdatedAt());
                        String jsonString = JSON.toJSONString(userES);
                        IndexRequest request = new IndexRequest("user").id(userES.getId().toString());
                        request.source(jsonString, XContentType.JSON);
                        userMapper.insert(user);
                        userInfoMapper.insert(userInfo);
                        client.index(request, RequestOptions.DEFAULT);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                });
            }
        }

        executor.shutdown();
        if (!executor.awaitTermination(300, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        return Response.okResult(AppHttpCodeEnum.SUCCESS);
    }

}
