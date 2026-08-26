package com.myagent.framework.convention;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全局统一返回结果
 */
@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String SUCCESS_CODE = "0";

    /** 状态码，0 表示成功 */
    private String code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 请求追踪 ID（后续接 Trace 时填充） */
    private String requestId;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
