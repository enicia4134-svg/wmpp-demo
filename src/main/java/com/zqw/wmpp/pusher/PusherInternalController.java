package com.zqw.wmpp.pusher;

import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.session.SessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/push")
public class PusherInternalController {

    @Autowired
    private WmppRole role;

    @Autowired
    private SessionRegistry sessionRegistry;

    public record PushBody(String message) {}

    @PostMapping("/broadcast")
    public void broadcast(@RequestParam String appId, @RequestBody(required = false) PushBody body) {
        requirePusher();
        String message = body == null || body.message() == null ? "" : body.message();
        sessionRegistry.broadcast(appId, message);
    }

    @PostMapping("/user")
    public void user(@RequestParam String appId, @RequestParam String userId, @RequestBody(required = false) PushBody body) {
        requirePusher();
        String message = body == null || body.message() == null ? "" : body.message();
        sessionRegistry.pushToUser(appId, userId, message);
    }

    private void requirePusher() {
        if (role != WmppRole.pusher && role != WmppRole.mono) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not pusher role");
        }
    }
}

