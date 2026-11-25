package com.xhzb.nursing.interceptor;

import com.alibaba.fastjson2.JSON;
import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.common.utils.ServletUtils;
import com.xhzb.common.utils.StringUtils;
import com.xhzb.framework.web.service.TokenService;
import com.xhzb.nursing.domain.FamilyMember;
import com.xhzb.nursing.service.IFamilyMemberService;
import com.xhzb.nursing.util.MemberHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 小程序端登录拦截器：解析 token 中的 userId，校验家属是否存在，写入 MemberHolder。
 *
 * @author lzh
 */
@Slf4j
@Component
public class MemberTokenInterceptor implements HandlerInterceptor {

    /** 兼容 Bearer 前缀 */
    private static final String TOKEN_PREFIX = "Bearer ";

    @Autowired
    private TokenService tokenService;

    @Autowired
    private IFamilyMemberService familyMemberService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        MemberHolder.clear();

        // 放行 CORS 预检请求
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (StringUtils.isEmpty(token)) {
            return unauthorized(response, "登录状态已失效，请重新登录");
        }

        Long memberId;
        try {
            Claims claims = tokenService.parseToken(token);
            Object userId = claims.get("userId");
            if (!(userId instanceof Number number)) {
                return unauthorized(response, "登录状态已失效，请重新登录");
            }
            memberId = number.longValue();
        } catch (Exception e) {
            return unauthorized(response, "登录状态已失效，请重新登录");
        }

        FamilyMember familyMember = familyMemberService.getById(memberId);
        if (familyMember == null) {
            return unauthorized(response, "登录状态已失效，请重新登录");
        }

        MemberHolder.set(memberId, familyMember.getName());
        log.info("member-api {} {}?{} memberId={}", request.getMethod(), request.getRequestURI(),
                request.getQueryString(), memberId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MemberHolder.clear();
    }

    /**
     * 从请求头取 token：Authorization（可带 Bearer）/ token
     */
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (StringUtils.isEmpty(token)) {
            token = request.getHeader("token");
        }
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        token = token.trim();
        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.substring(TOKEN_PREFIX.length()).trim();
        }
        return token;
    }

    /**
     * 统一返回 HTTP 200 + {code:401,msg:...}，方便小程序端按 code 判断
     */
    private boolean unauthorized(HttpServletResponse response, String message) {
        ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.error(401, message)));
        return false;
    }
}
