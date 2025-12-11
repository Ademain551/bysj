package com.dlu.mtjbysj.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        Object attr = session != null ? session.getAttribute("LOGIN_USER") : null;
        if (attr instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) attr;
            Object role = m.get("role");
            if (role != null && "admin".equals(String.valueOf(role))) {
                return true;
            }
        }
        writeForbidden(response);
        return false;
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse body = new ApiResponse(false, "需要管理员权限");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
