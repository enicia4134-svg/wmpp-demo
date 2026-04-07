package com.zqw.wmpp.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminAuth {

    @Value("${wmpp.admin.token:change-me}")
    private String adminToken;

    public void require(String token) {
        if (token == null || token.isBlank() || adminToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing admin token");
        }
        if (!token.equals(adminToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid admin token");
        }
    }
}

