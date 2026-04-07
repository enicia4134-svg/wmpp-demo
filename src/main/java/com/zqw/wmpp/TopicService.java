package com.zqw.wmpp;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TopicService {

    // appId:topic -> userId set
    private final Map<String, Set<String>> topicUsers = new HashMap<>();

    public TopicService() {

        // 模拟关注关系
        topicUsers.put(key("systemA", "starA"), new HashSet<>(Arrays.asList("1001","2002")));
        topicUsers.put(key("systemA", "starB"), new HashSet<>(Arrays.asList("3003")));
        topicUsers.put(key("systemB", "news"), new HashSet<>(Arrays.asList("3003")));

        System.out.println("⭐ Topic订阅关系初始化完成");
    }

    public Set<String> getSubscribers(String appId, String topic){

        return topicUsers.getOrDefault(key(appId, topic), new HashSet<>());
    }

    private static String key(String appId, String topic) {
        return appId + ":" + topic;
    }
}