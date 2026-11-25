package com.xhzb.nursing.config;

import com.xhzb.nursing.interceptor.MemberTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 小程序端拦截器注册。
 * 只拦家属端新增的接口，不动已有的 /member/roomTypes 等公开接口。
 *
 * @author lzh
 */
@Configuration
public class MemberWebConfig implements WebMvcConfigurer {

    @Autowired
    private MemberTokenInterceptor memberTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memberTokenInterceptor)
                .addPathPatterns("/member/user/my", "/member/user/add", "/member/user/list-by-page",
                        "/member/user/queryServiceProperties/**", "/member/user/queryDeviceDataListByDay",
                        "/member/user/queryDeviceDataListByWeek",
                        "/member/reservation/**", "/member/orders/**")
                .excludePathPatterns("/member/user/login");
    }
}
