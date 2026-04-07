package com.zqw.wmpp.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppRegistryService {

    public record AppInfo(String appId, String secretKey, boolean allowPush) {}

    private final Map<String, AppInfo> apps = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${wmpp.appRegistry.file:data/apps.json}")
    private String registryFile;

    @PostConstruct
    public void init() {
        // Load persisted registry if present, otherwise seed demo
        if (!loadFromFile()) {
            register(new AppInfo("systemA", "systemA-secret", true));
            register(new AppInfo("systemB", "systemB-secret", true));
            persistQuietly();
        }
    }

    public void register(AppInfo app) {
        apps.put(app.appId(), app);
        persistQuietly();
    }

    public AppInfo find(String appId) {
        return apps.get(appId);
    }

    public List<AppInfo> list() {
        return apps.values().stream().sorted((a, b) -> a.appId().compareToIgnoreCase(b.appId())).toList();
    }

    public AppInfo upsert(String appId, String secretKey, boolean allowPush) {
        AppInfo info = new AppInfo(appId, secretKey, allowPush);
        apps.put(appId, info);
        persistQuietly();
        return info;
    }

    public AppInfo setAllowPush(String appId, boolean allowPush) {
        AppInfo old = apps.get(appId);
        if (old == null) return null;
        AppInfo updated = new AppInfo(old.appId(), old.secretKey(), allowPush);
        apps.put(appId, updated);
        persistQuietly();
        return updated;
    }

    public AppInfo rotateSecret(String appId) {
        AppInfo old = apps.get(appId);
        if (old == null) return null;
        String secret = generateSecret();
        AppInfo updated = new AppInfo(old.appId(), secret, old.allowPush());
        apps.put(appId, updated);
        persistQuietly();
        return updated;
    }

    public boolean authenticate(String appId, String secretKey) {
        if (appId == null || appId.isBlank() || secretKey == null || secretKey.isBlank()) return false;
        AppInfo app = apps.get(appId);
        if (app == null) return false;
        if (!app.allowPush()) return false;
        return secretKey.equals(app.secretKey());
    }

    private String generateSecret() {
        byte[] buf = new byte[24];
        secureRandom.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private boolean loadFromFile() {
        try {
            Path path = Path.of(registryFile);
            if (!Files.exists(path)) return false;
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) return false;
            List<AppInfo> list = objectMapper.readValue(bytes, new TypeReference<>() {});
            apps.clear();
            if (list != null) {
                for (AppInfo a : list) {
                    if (a == null || a.appId() == null || a.appId().isBlank()) continue;
                    apps.put(a.appId(), new AppInfo(a.appId(), a.secretKey(), a.allowPush()));
                }
            }
            return !apps.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void persistQuietly() {
        try {
            Path path = Path.of(registryFile);
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list());
            Path tmp = Path.of(registryFile + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
        }
    }
}

