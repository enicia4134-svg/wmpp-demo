package com.zqw.wmpp.scheduler;

import com.zqw.wmpp.PusherNode;

import java.util.List;

public interface SchedulerStrategy {
    PusherNode select(String appId, List<PusherNode> nodes);
}

