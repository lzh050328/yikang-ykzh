package com.xhzb.framework.interceptor;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.xhzb.common.utils.UserThreadLocal;
import com.xhzb.framework.web.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 小程序端Token验证拦截器
 *
 * @Author mr.wu
 * @Date 2026-8-19 10:21
 */
@Component
public class MemberInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenService tokenService;

    /**
     * Token验证核心处理逻辑方法（拦截方法）
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler chosen handler to execute, for type and/or instance evaluation
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 从请求头中获取Token令牌
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);

        //2. 如果Token为空， 则响应401状态码
        if (StrUtil.isBlank(token)) {
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }

        //3. 解析Token
        try {
            Claims claims = tokenService.parseToken(token);

            //4. 如果Token解析结果为空， 则响应401状态码
            Long userId = MapUtil.get(claims, "userId", Long.class);
            if (userId==null) {
                response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
                return false;
            }

            //5. 将userId存入ThreadLocal
            UserThreadLocal.set(userId);
        } catch (Exception e) {
            e.printStackTrace();
            //6. 如果Token解析抛出异常， 则响应401状态码
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }

        //7. 放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserThreadLocal.remove();
    }
}
