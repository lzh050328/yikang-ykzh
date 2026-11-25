package com.xhzb.nursing.util;

/**
 * 当前登录家属上下文（基于 ThreadLocal）。
 * 由 MemberTokenInterceptor 写入，由小程序端业务代码读取。
 *
 * @author lzh
 */
public class MemberHolder {

    private static final ThreadLocal<Long> MEMBER_ID = new ThreadLocal<>();

    private static final ThreadLocal<String> MEMBER_NAME = new ThreadLocal<>();

    private MemberHolder() {
    }

    public static void set(Long memberId, String memberName) {
        MEMBER_ID.set(memberId);
        MEMBER_NAME.set(memberName);
    }

    public static Long getMemberId() {
        return MEMBER_ID.get();
    }

    public static String getMemberName() {
        return MEMBER_NAME.get();
    }

    public static void clear() {
        MEMBER_ID.remove();
        MEMBER_NAME.remove();
    }
}
