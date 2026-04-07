package com.zqw.wmpp.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/apps")
public class AppAdminController {

    @Autowired
    private AdminAuth adminAuth;

    @Autowired
    private AppRegistryService appRegistryService;

    public record CreateAppRequest(String appId, Boolean allowPush) {}

    @GetMapping
    public List<AppRegistryService.AppInfo> list(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        adminAuth.require(token);
        return appRegistryService.list();
    }

    @PostMapping
    public AppRegistryService.AppInfo create(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestBody CreateAppRequest body
    ) {
        adminAuth.require(token);
        String appId = body == null ? null : body.appId();
        boolean allowPush = body != null && body.allowPush() != null ? body.allowPush() : true;
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId required");
        }
        // create with rotated secret by default
        AppRegistryService.AppInfo existing = appRegistryService.find(appId);
        if (existing != null) return existing;
        AppRegistryService.AppInfo created = appRegistryService.upsert(appId, "temp", allowPush);
        return appRegistryService.rotateSecret(created.appId());
    }

    @PostMapping("/{appId}/allowPush")
    public AppRegistryService.AppInfo allowPush(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @PathVariable String appId,
            @RequestParam boolean allow
    ) {
        adminAuth.require(token);
        return appRegistryService.setAllowPush(appId, allow);
    }

    @PostMapping("/{appId}/rotateSecret")
    public AppRegistryService.AppInfo rotateSecret(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @PathVariable String appId
    ) {
        adminAuth.require(token);
        return appRegistryService.rotateSecret(appId);
    }
}

