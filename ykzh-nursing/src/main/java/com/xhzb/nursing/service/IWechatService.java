package com.xhzb.nursing.service;

/**
 * 微信小程序服务端接口
 *
 * @author lzh
 */
public interface IWechatService {

    /**
     * code2Session：用登录 code 换取 openid
     *
     * @param code 临时登录凭证
     * @return openid
     */
    String getOpenId(String code);

    /**
     * getPhoneNumber：用手机号 code 换取手机号
     *
     * @param phoneCode 获取手机号的临时 code
     * @return 手机号
     */
    String getPhone(String phoneCode);
}
