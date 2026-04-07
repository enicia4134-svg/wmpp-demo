package com.zqw.wmpp.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AppAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_APP_ID = "wmpp.rest.appId";

    /** 与对外约定一致：仅使用 AppSecretKey（HTTP Header 形式） */
    public static final String HEADER_APP_SECRET_KEY = "X-App-Secret-Key";

    @Autowired
    private AppRegistryService appRegistryService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String appId = firstNonBlank(
                request.getHeader("X-App-Id"),
                request.getParameter("appId")
        );
        String appSecretKey = firstNonBlank(
                request.getHeader(HEADER_APP_SECRET_KEY),
                request.getParameter("appSecretKey")
        );

        if (!appRegistryService.authenticate(appId, appSecretKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Unauthorized: invalid appId or appSecretKey");
            return false;
        }

        request.setAttribute(ATTR_APP_ID, appId);
        return true;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
