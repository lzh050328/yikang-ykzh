package com.xhzb.ai.dify;

/**
 * Dify 接口调用异常
 *
 * @author xhzb
 */
public class DifyApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DifyApiException(String message) {
        super(message);
    }

    public DifyApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
