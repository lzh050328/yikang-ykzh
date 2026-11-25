package com.xhzb.nursing.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xhzb.common.exception.ServiceException;
import com.xhzb.nursing.config.WechatProperties;
import com.xhzb.nursing.service.IWechatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序服务端接口实现（HTTP 请求全部使用 Hutool 的 HttpUtil）
 *
 * 官方文档：
 * 1. code2Session   https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
 * 2. 获取 access_token https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/mp-access-token/getAccessToken.html
 * 3. getPhoneNumber https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-info/phone-number/getPhoneNumber.html
 *
 * @author lzh
 */
@Slf4j
@Service
public class WechatServiceImpl implements IWechatService {

    /** 登录凭证校验：code 换 openid */
    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    /** 获取接口调用凭据 */
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";

    /** 获取用户手机号 */
    private static final String GET_PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";

    @Autowired
    private WechatProperties wechatProperties;

    /** access_token 缓存，微信要求全局唯一，不能每次调用都换 */
    private String accessToken;

    /** access_token 过期时间戳 */
    private long accessTokenExpireAt;

    @Override
    public String getOpenId(String code) {
        if (StrUtil.isBlank(code)) {
            throw new ServiceException("登录凭证code不能为空");
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("appid", wechatProperties.getAppid());
        paramMap.put("secret", wechatProperties.getSecret());
        paramMap.put("js_code", code);
        paramMap.put("grant_type", "authorization_code");

        JSONObject json = parse(HttpUtil.get(CODE2SESSION_URL, paramMap), "code2Session");
        checkError(json, "code2Session");
        String openId = json.getStr("openid");
        if (StrUtil.isBlank(openId)) {
            throw new ServiceException("微信登录失败：未获取到openid");
        }
        return openId;
    }

    @Override
    public String getPhone(String phoneCode) {
        if (StrUtil.isBlank(phoneCode)) {
            throw new ServiceException("手机号凭证phoneCode不能为空");
        }
        JSONObject body = new JSONObject().set("code", phoneCode);
        String url = GET_PHONE_URL + "?access_token=" + getAccessToken();
        String response = HttpUtil.createPost(url)
                .body(body.toString(), "application/json")
                .execute()
                .body();

        JSONObject json = parse(response, "getPhoneNumber");
        checkError(json, "getPhoneNumber");
        JSONObject phoneInfo = json.getJSONObject("phone_info");
        String phone = phoneInfo == null ? null : phoneInfo.getStr("purePhoneNumber");
        if (StrUtil.isBlank(phone)) {
            throw new ServiceException("微信登录失败：未获取到手机号");
        }
        return phone;
    }

    /**
     * 获取接口调用凭据（内存缓存，提前 5 分钟过期）
     */
    private synchronized String getAccessToken() {
        if (StrUtil.isNotBlank(accessToken) && System.currentTimeMillis() < accessTokenExpireAt) {
            return accessToken;
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("grant_type", "client_credential");
        paramMap.put("appid", wechatProperties.getAppid());
        paramMap.put("secret", wechatProperties.getSecret());

        JSONObject json = parse(HttpUtil.get(ACCESS_TOKEN_URL, paramMap), "getAccessToken");
        checkError(json, "getAccessToken");
        accessToken = json.getStr("access_token");
        if (StrUtil.isBlank(accessToken)) {
            throw new ServiceException("获取微信access_token失败");
        }
        long expiresIn = json.getLong("expires_in", 7200L);
        accessTokenExpireAt = System.currentTimeMillis() + Math.max(expiresIn - 300, 60) * 1000;
        return accessToken;
    }

    /**
     * 解析微信返回的 JSON，失败统一抛业务异常
     */
    private JSONObject parse(String body, String apiName) {
        try {
            return JSONUtil.parseObj(body);
        } catch (Exception e) {
            log.error("微信接口 {} 返回内容无法解析：{}", apiName, body);
            throw new ServiceException("调用微信接口失败：" + apiName);
        }
    }

    /**
     * 校验微信返回的 errcode
     */
    private void checkError(JSONObject json, String apiName) {
        Integer errcode = json.getInt("errcode");
        if (errcode != null && errcode != 0) {
            String errmsg = json.getStr("errmsg");
            log.warn("微信接口 {} 调用失败：errcode={}, errmsg={}", apiName, errcode, errmsg);
            throw new ServiceException("微信接口调用失败：" + errmsg + "(errcode=" + errcode + ")");
        }
    }
}
