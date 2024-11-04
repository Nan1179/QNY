package com.qny.start.listener;

import com.alibaba.fastjson.JSON;
import com.qny.model.start.pojos.UserES;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SyncGitHubUserListener {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @KafkaListener(topics = "es.sync.topic")
    public void onMessage(String message) {
        if (StringUtils.isNotBlank(message)) {
            log.info("SyncArticleListener, message = {}", message);

            UserES userES = JSON.parseObject(message, UserES.class);

            IndexRequest request = new IndexRequest("user").id(userES.getId().toString());
            request.source(message, XContentType.JSON);

            try {
                restHighLevelClient.index(request, RequestOptions.DEFAULT);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("sync es error = {}", e);
            }
        }
    }
}
