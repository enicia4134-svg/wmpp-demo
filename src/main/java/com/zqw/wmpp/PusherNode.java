package com.zqw.wmpp;

public class PusherNode {

    private final String appId;
    private final String pusherId;
    private int connectionCount;
    private boolean active = true;

    public PusherNode(String appId, String pusherId) {
        this.appId = appId;
        this.pusherId = pusherId;
        this.connectionCount = 0;
    }

    public String getAppId() {
        return appId;
    }

    public String getPusherId() {
        return pusherId;
    }

    public int getConnectionCount() {
        return connectionCount;
    }

    public void setConnectionCountSnapshot(int connectionCount) {
        this.connectionCount = connectionCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void addConnection() {
        connectionCount++;
    }

    public void removeConnection() {
        connectionCount--;
    }
}