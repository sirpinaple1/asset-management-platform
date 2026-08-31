package com.sk.asset.dingtalk.client;

/**
 * 钉钉 OpenAPI 调用异常（M10）：统一由调用方捕获后降级（outbox 记 FAILED + 告警），
 * 不向业务事务传播（钉钉不可用不阻塞资产系统主流程，ADR-0007 D4）。
 */
public class DingTalkApiException extends RuntimeException {

    public DingTalkApiException(String message) {
        super(message);
    }

    public DingTalkApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
