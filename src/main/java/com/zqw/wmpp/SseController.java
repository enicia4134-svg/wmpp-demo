package com.zqw.wmpp;

import com.zqw.wmpp.auth.AppRegistryService;
import com.zqw.wmpp.registry.RegistryClient;
import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.session.SessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private AppRegistryService appRegistryService;

    @Autowired
    private WmppRole role;

    @Autowired
    private RegistryClient registryClient;

    @Value("${wmpp.pusher.id:pusher-1}")
    private String pusherId;

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam String appId, @RequestParam String userId) {
        if (role != WmppRole.pusher && role != WmppRole.mono) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not pusher role");
        }
        if (appRegistryService.find(appId) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid appId");
        }
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid userId");
        }
        SseEmitter emitter = sessionRegistry.registerSse(appId, userId);
        if (role == WmppRole.pusher) {
            registryClient.register(appId, userId, pusherId);
            emitter.onCompletion(() -> registryClient.unregister(appId, userId, pusherId));
            emitter.onTimeout(() -> registryClient.unregister(appId, userId, pusherId));
            emitter.onError(e -> registryClient.unregister(appId, userId, pusherId));
        }
        return emitter;
    }
}

