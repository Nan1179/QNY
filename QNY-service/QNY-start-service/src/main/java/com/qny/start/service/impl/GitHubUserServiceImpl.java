package com.qny.start.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qny.model.start.dto.ChatCompletionResponse;
import com.qny.model.start.dto.UserInfoDto;
import com.qny.utils.GitHubUtils;
import com.qny.model.response.AppHttpCodeEnum;
import com.qny.model.response.Response;
import com.qny.model.start.common.PageResponseResult;
import com.qny.model.start.dto.UserDto;
import com.qny.model.start.dto.UserPageDto;
import com.qny.model.start.pojos.Repos;
import com.qny.model.start.pojos.User;
import com.qny.model.start.pojos.UserES;
import com.qny.model.start.pojos.UserInfo;
import com.qny.start.mapper.UserInfoMapper;
import com.qny.start.mapper.UserMapper;
import com.qny.start.service.GitHubUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class GitHubUserServiceImpl implements GitHubUserService {

    @Autowired
    private RestHighLevelClient client;

    private void buildBasicQuery(SearchRequest request, UserPageDto dto) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        String key = dto.getKey();
        if (StringUtils.isBlank(key)) {
            boolQueryBuilder.must(QueryBuilders.matchAllQuery());
        } else {
            boolQueryBuilder.must(QueryBuilders.matchQuery("all", key));
        }

//        boolQueryBuilder.filter(QueryBuilders.termQuery("kindId", dto.getKindId()));

        request.source().query(boolQueryBuilder);
    }

    /**
     * es查询
     * @param dto
     * @return
     */
    @Override
    public Response search(UserPageDto dto) {
        dto.checkParam();

        try {
            SearchRequest request = new SearchRequest("user");
            // 准备DLS;
            buildBasicQuery(request, dto);

            Integer page = dto.getPage();
            Integer size = dto.getSize();
            // 高亮
            HighlightBuilder highlightBuilder = new HighlightBuilder().field("name").field("topic").field("location").field("company")
                    .requireFieldMatch(false);
            highlightBuilder.preTags("<font size=\"4\" color=\"red\">");
            highlightBuilder.postTags("</font>");
            request.source().highlighter(highlightBuilder);
            // 分页&排序
            request.source().from((page - 1) * size).size(size).sort("score", SortOrder.DESC).sort("created_at", SortOrder.DESC).sort("updated_at", SortOrder.DESC);

            // 发送请求
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);

            // 解析响应
            return handleResponse(response, dto);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public Response addUser(String userName) {
        if (!StringUtils.isNotBlank(userName)) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);

        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getLogin, userName);
        User u = userMapper.selectOne(lambdaQueryWrapper);

        if (u != null) return Response.errorResult(AppHttpCodeEnum.DATA_EXIST);

        UserDto userDto = GitHubUtils.getUserInfo(userName);

        if (userDto == null) return Response.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);

        Integer score = GitHubUtils.getScore(userName);

        User user = new User();
        user.setGrade("未评级");
        user.setScore(score);
        UserInfo userInfo = new UserInfo();

        BeanUtils.copyProperties(userDto, user);
        BeanUtils.copyProperties(userDto, userInfo);

        // 计算某用户的领域
        Map<String, Integer> frequencyMap = new HashMap<>();
        List<Repos> repo = GitHubUtils.getRepo(user.getLogin());

        if (repo != null) {
            for (Repos repos : repo) {
                for (String topic : repos.getTopics()) {
                    frequencyMap.put(topic, frequencyMap.getOrDefault(topic, 0) + 1);
                }
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


        // ES数据准备
        UserES userES = new UserES();
        BeanUtils.copyProperties(user, userES);
        BeanUtils.copyProperties(userInfo, userES);
        userES.setAvatar_url(user.getAvatarUrl());
        userES.setHtml_url(userES.getHtml_url());
        userES.setCreated_at(userInfo.getCreatedAt());
        userES.setUpdated_at(userInfo.getUpdatedAt());
        String jsonString = JSON.toJSONString(userES);
        IndexRequest request = new IndexRequest("user").id(userES.getId().toString());
        request.source(jsonString, XContentType.JSON);

        // 插入数据库
        userMapper.insert(user);
        userInfoMapper.insert(userInfo);

        try {
            // 插入ES
            client.index(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Response.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public Response guessLocation(String userName) {
        if (!StringUtils.isNotBlank(userName)) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);

        String userLocations = GitHubUtils.getUserLocations1(userName);

        Map<String, String> map = new HashMap<>();
        map.put("userLocations", userLocations);

        return Response.okResult(map);
    }

    /**
     * 线程池线程数量
     */
    private int THREAD_NUM = 10;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    /**
     * 更新技术评级得分
     */
    @Override
    public void computeUserScore() {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUM);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        // 只更新活跃的前300个用户
        queryWrapper.orderByDesc(User::getUpdatedAt);
        List<User> userList = userMapper.selectList(queryWrapper);

        if (userList == null) return;

        // 只取前300个用户
        userList = userList.subList(0, Math.min(300, userList.size()));
        log.info("要更新的用户数量：{}", userList.size());

        try {
            // 更新分值
            for (User user : userList) {
                executor.submit((Callable<Void>) () -> {
                    try {
                        Integer score = GitHubUtils.getScore(user.getLogin());
                        // 如果需要修改
                        if (score != 0 && !Objects.equals(user.getScore(), score)) {
                            log.info("{}正在被更新", user.getLogin());
                            user.setScore(score);
                            userMapper.updateById(user);
                            kafkaTemplate.send("es.update.topic", JSON.toJSONString(user));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            }

            executor.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.gt(User::getScore, 0).orderByDesc(User::getScore);
        List<User> collect = userMapper.selectList(lambdaQueryWrapper);

//        // 取出score大于0的元素，并以降序排序
//        List<User> collect = userList1.stream()
//                .sorted(Comparator.comparingInt(User::getScore).reversed())
//                .collect(Collectors.toList());


        // 前1% 是 s 类
        int sIndex = (int) Math.ceil(collect.size() * 0.01);
        // 前10% 是 a 类
        int aIndex = (int) Math.ceil(collect.size() * 0.1);
        // 前30% 是 b 类
        int bIndex = (int) Math.ceil(collect.size() * 0.3);
        // 前70% 是 c 类
        int cIndex = (int) Math.ceil(collect.size() * 0.7);

        if (sIndex < collect.size()) {
            int sScore = collect.get(sIndex).getScore();
            stringRedisTemplate.opsForValue().set("sScore", String.valueOf(sScore));
            log.info("s分数为：" + sScore);
        }

        if (aIndex < collect.size()) {
            int aScore = collect.get(aIndex).getScore();
            stringRedisTemplate.opsForValue().set("aScore", String.valueOf(aScore));
            log.info("a分数为：" + aScore);
        }

        if (bIndex < collect.size()) {
            int bScore = collect.get(bIndex).getScore();
            stringRedisTemplate.opsForValue().set("bScore", String.valueOf(bScore));
            log.info("b分数为：" + bScore);
        }

        if (cIndex < collect.size()) {
            int cScore = collect.get(cIndex).getScore();
            stringRedisTemplate.opsForValue().set("cScore", String.valueOf(cScore));
            log.info("c分数为：" + cScore);
        }
    }

    @Override
    public Response updateGrade() {
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.gt(User::getScore, 0).orderByDesc(User::getScore);
        List<User> list = userMapper.selectList(lambdaQueryWrapper);

        // 前1% 是s 类推下去
        int sIndex = (int) Math.ceil(list.size() * 0.01);
        int aIndex = (int) Math.ceil(list.size() * 0.1);
        int bIndex = (int) Math.ceil(list.size() * 0.3);
        int cIndex = (int) Math.ceil(list.size() * 0.7);

        if (sIndex < list.size()) {
            int sScore = list.get(sIndex).getScore();
            stringRedisTemplate.opsForValue().set("sScore", String.valueOf(sScore));
            log.info("s分数为：" + sScore);
        }

        if (aIndex < list.size()) {
            int aScore = list.get(aIndex).getScore();
            stringRedisTemplate.opsForValue().set("aScore", String.valueOf(aScore));
            log.info("a分数为：" + aScore);
        }

        if (bIndex < list.size()) {
            int bScore = list.get(bIndex).getScore();
            stringRedisTemplate.opsForValue().set("bScore", String.valueOf(bScore));
            log.info("b分数为：" + bScore);
        }

        if (cIndex < list.size()) {
            int cScore = list.get(cIndex).getScore();
            stringRedisTemplate.opsForValue().set("cScore", String.valueOf(cScore));
            log.info("c分数为：" + cScore);
        }

        return Response.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public Response getGrade() {
        String sScore = stringRedisTemplate.opsForValue().get("sScore");
        String aScore = stringRedisTemplate.opsForValue().get("aScore");
        String bScore = stringRedisTemplate.opsForValue().get("bScore");
        String cScore = stringRedisTemplate.opsForValue().get("cScore");

        if (!StringUtils.isNotBlank(sScore)) updateGrade();

        Map<String, String> map = new HashMap<>();
        map.put("sScore", sScore);
        map.put("aScore", aScore);
        map.put("bScore", bScore);
        map.put("cScore", cScore);

        return Response.okResult(map);
    }

    @Override
    public Response getEvaluate(String login) {
        if (!StringUtils.isNotBlank(login)) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);

        String s = GitHubUtils.getKimiEvaluate(login);
        ChatCompletionResponse chatCompletionResponse = JSON.parseObject(s, ChatCompletionResponse.class);

        if (chatCompletionResponse == null) return Response.okResult("该用户所留信息太少，无法进行评价分析！");

        String evaluate = "";

        for (ChatCompletionResponse.Choice choice : chatCompletionResponse.getChoices()) {
            evaluate = choice.getMessage().getContent();
        }

        return Response.okResult(evaluate);
    }

    @Override
    public Response getGradeCount() {

        String sSize = stringRedisTemplate.opsForValue().get("sSize");
        String aSize = stringRedisTemplate.opsForValue().get("aSize");
        String bSize = stringRedisTemplate.opsForValue().get("bSize");
        String cSize = stringRedisTemplate.opsForValue().get("cSize");
        String dSize = stringRedisTemplate.opsForValue().get("dSize");

        if (!StringUtils.isNotBlank(sSize)) {
            LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.gt(User::getScore, 0).orderByDesc(User::getScore);
            List<User> list = userMapper.selectList(lambdaQueryWrapper);

            // 前1% 是s 类推下去
            int sIndex = (int) Math.ceil(list.size() * 0.01);
            int aIndex = (int) Math.ceil(list.size() * 0.1);
            int bIndex = (int) Math.ceil(list.size() * 0.3);
            int cIndex = (int) Math.ceil(list.size() * 0.7);
            int dIndex = list.size() - cIndex;

            if (sIndex < list.size()) {
                stringRedisTemplate.opsForValue().set("sSize", String.valueOf(sIndex));
            }

            if (aIndex < list.size()) {
                stringRedisTemplate.opsForValue().set("aSize", String.valueOf(aIndex - sIndex));
            }

            if (bIndex < list.size()) {
                stringRedisTemplate.opsForValue().set("bSize", String.valueOf(bIndex - aIndex));
            }

            if (cIndex < list.size()) {
                stringRedisTemplate.opsForValue().set("cSize", String.valueOf(cIndex - bIndex));
                stringRedisTemplate.opsForValue().set("dSize", String.valueOf(dIndex));

            }

            sSize = String.valueOf(sIndex);
            aSize = String.valueOf(aIndex - sIndex);
            bSize = String.valueOf(bIndex - aIndex);
            cSize = String.valueOf(cIndex - bIndex);
            dSize = String.valueOf(dIndex);
        }

        Map<String, String> map = new HashMap<>();
        map.put("sSize", sSize);
        map.put("aSize", aSize);
        map.put("bSize", bSize);
        map.put("cSize", cSize);
        map.put("dSize", dSize);

        return Response.okResult(map);
    }

    @Override
    public Response getUserInfo(String login) {

        if (!StringUtils.isNotBlank(login)) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);

        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getLogin, login);
        User user = userMapper.selectOne(lambdaQueryWrapper);

        if (user == null) return Response.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);

        LambdaQueryWrapper<UserInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserInfo::getLogin, login);
        UserInfo userInfo = userInfoMapper.selectOne(queryWrapper);

        if (userInfo == null) return Response.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);

        UserInfoDto userDto = new UserInfoDto();
        BeanUtils.copyProperties(user, userDto);
        BeanUtils.copyProperties(userInfo, userDto);

        return Response.okResult(userDto);
    }

    @Override
    public Response getRepo(String login) {

        if (!StringUtils.isNotBlank(login)) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);

        List<Repos> repo = GitHubUtils.getRepo(login);

        return Response.okResult(repo);
    }

//    @Override
//    public Response getRank(String login) {
//
//        if (!StringUtils.isNotBlank(login)) return Response.errorResult(AppHttpCodeEnum.PARAM_INVALID);
//
//
//        return null;
//    }

    private Response handleResponse(SearchResponse response, UserPageDto dto) {
        SearchHits searchHits = response.getHits();
        long total = searchHits.getTotalHits().value;
        SearchHit[] hits = searchHits.getHits();

        List<UserES> userList = new ArrayList<>();
        for (SearchHit hit : hits) {
            String json = hit.getSourceAsString();
            UserES user = JSON.parseObject(json, UserES.class);
            // 获取高亮
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (!CollectionUtils.isEmpty(highlightFields)) {
                HighlightField highlightField1 = highlightFields.get("name");
                HighlightField highlightField2 = highlightFields.get("topic");
                HighlightField highlightField3 = highlightFields.get("location");
                HighlightField highlightField4 = highlightFields.get("company");


                if (highlightField1 != null) {
                    String name = highlightField1.getFragments()[0].toString();
                    user.setName(name);
                }

                if (highlightField2 != null) {
                    String topic = highlightField2.getFragments()[0].toString();
                    user.setTopic(topic);
                }

                if (highlightField3 != null) {
                    String location = highlightField3.getFragments()[0].toString();
                    user.setLocation(location);
                }

                if (highlightField4 != null) {
                    String company = highlightField4.getFragments()[0].toString();
                    user.setCompany(company);
                }
            }

            userList.add(user);
        }

        Response res = new PageResponseResult(dto.getPage(), dto.getSize(), (int) total);
        res.setData(userList);

        return res;
    }
}
