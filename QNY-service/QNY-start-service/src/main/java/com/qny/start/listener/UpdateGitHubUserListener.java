package com.qny.start.listener;

import com.alibaba.fastjson.JSON;
import com.qny.model.start.pojos.User;
import com.qny.model.start.pojos.UserES;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateGitHubUserListener {
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @KafkaListener(topics = "es.update.topic")
    public void onMessage(String message) {
        if (StringUtils.isNotBlank(message)) {
            log.info("SyncArticleListener, message = {}", message);

            User user = JSON.parseObject(message, User.class);

            UpdateRequest request = new UpdateRequest("user", user.getId().toString());
            request.doc(
                    "score", user.getScore()
            );

            try {
                restHighLevelClient.update(request, RequestOptions.DEFAULT);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("update es error = {}", e);
            }
        }
    }
}
