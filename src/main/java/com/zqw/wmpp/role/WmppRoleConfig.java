package com.zqw.wmpp.role;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WmppRoleConfig {

    @Bean
    public WmppRole wmppRole(@Value("${wmpp.role:mono}") String role) {
        try {
            return WmppRole.valueOf(role.trim().toLowerCase());
        } catch (Exception ignored) {
            return WmppRole.mono;
        }
    }
}

