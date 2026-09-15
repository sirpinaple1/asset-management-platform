package com.sk.asset.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * comm_public_basic 统一响应包装（ResponseConfig 自动包装）：
 * {code, message, data, timestamp}，code=200 表示成功。
 * 契约快照见 docs/modules/M01-骨架与鉴权.md 与 STATUS.md（2026-08-19 按 origin/main 核实）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommResult<T> {

    public static final int SUCCESS = 200;

    private int code;
    private String message;
    private T data;

    public boolean isSuccess() {
        return code == SUCCESS;
    }
}
