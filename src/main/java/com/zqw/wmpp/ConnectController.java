package com.zqw.wmpp;

import com.zqw.wmpp.auth.AppRegistryService;
import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.scheduler.SchedulerClient;
import com.zqw.wmpp.scheduler.SchedulerController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConnectController {

    @Autowired
    private WmppRole role;

    @Autowired
    private AppRegistryService appRegistryService;

    @Autowired
    private SchedulerClient schedulerClient;

    public record ConnectResponse(String pusherId, String wsUrl, String sseUrl) {}

    /**
     * 客户端获取推送端点（HTTP JSON）。与 WebSocket 路径 {@code /connect} 分离，避免与握手冲突。
     */
    @GetMapping("/api/connect")
    public ConnectResponse connect(@RequestParam String appId, @RequestParam String userId, HttpServletRequest request) {
        if (role != WmppRole.gateway && role != WmppRole.mono) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not gateway role");
        }
        if (appRegistryService.find(appId) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid appId");
        }
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid userId");
        }

        SchedulerController.AssignResponse assigned = (role == WmppRole.gateway)
                ? schedulerClient.assign(appId, userId)
                : new SchedulerController.AssignResponse("local", resolveHttpBaseUrl(request), resolveWsBaseUrl(request));

        String httpBase = assigned.httpBaseUrl();
        String wsBase = assigned.wsBaseUrl();
        // DDD: ws://host/connect?appId=xxx&userId=xxx（与 /ws/push 等价，便于论文表述一致）
        return new ConnectResponse(
                assigned.pusherId(),
                wsBase + "/connect?appId=" + appId + "&userId=" + userId,
                httpBase + "/stream?appId=" + appId + "&userId=" + userId
        );
    }

    private String resolveHttpBaseUrl(HttpServletRequest request) {
        String forwardedProto = firstHeaderValue(request, "X-Forwarded-Proto");
        String forwardedHost = firstHeaderValue(request, "X-Forwarded-Host");
        if (forwardedProto != null && forwardedHost != null) {
            return forwardedProto + "://" + forwardedHost;
        }

        String host = firstHeaderValue(request, "Host");
        if (host != null) {
            return request.getScheme() + "://" + host;
        }

        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && port == 443);
        return defaultPort
                ? request.getScheme() + "://" + request.getServerName()
                : request.getScheme() + "://" + request.getServerName() + ":" + port;
    }

    private String resolveWsBaseUrl(HttpServletRequest request) {
        String forwardedProto = firstHeaderValue(request, "X-Forwarded-Proto");
        String forwardedHost = firstHeaderValue(request, "X-Forwarded-Host");
        if (forwardedProto != null && forwardedHost != null) {
            String wsScheme = "https".equalsIgnoreCase(forwardedProto) ? "wss" : "ws";
            return wsScheme + "://" + forwardedHost;
        }

        String host = firstHeaderValue(request, "Host");
        if (host != null) {
            String wsScheme = "https".equalsIgnoreCase(request.getScheme()) ? "wss" : "ws";
            return wsScheme + "://" + host;
        }

        String wsScheme = "https".equalsIgnoreCase(request.getScheme()) ? "wss" : "ws";
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && port == 443);
        return defaultPort
                ? wsScheme + "://" + request.getServerName()
                : wsScheme + "://" + request.getServerName() + ":" + port;
    }

    private String firstHeaderValue(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return null;
        }
        int commaIndex = value.indexOf(',');
        return (commaIndex >= 0 ? value.substring(0, commaIndex) : value).trim();
    }
}

