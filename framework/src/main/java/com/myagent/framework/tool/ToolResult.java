package com.myagent.framework.tool;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 工具调用结果。输出经脱敏后再返回调用方与审计。
 */
@Data
@Builder
public class ToolResult {

    public enum State { SUCCESS, ERROR, AWAITING_CONFIRMATION }

    private State state;
    /** 脱敏后的输出数据 */
    private Object output;
    /** 错误码（失败时） */
    private String errorCode;
    /** 错误信息（已脱敏，可对外） */
    private String message;
    /** 追踪号 */
    private String traceId;

    public static ToolResult success(Object output, String traceId) {
        return ToolResult.builder().state(State.SUCCESS).output(output).traceId(traceId).build();
    }

    public static ToolResult awaiting(Object output, String traceId) {
        return ToolResult.builder().state(State.AWAITING_CONFIRMATION).output(output).traceId(traceId).build();
    }

    public static ToolResult error(String errorCode, String message, String traceId) {
        return ToolResult.builder().state(State.ERROR).errorCode(errorCode).message(message).traceId(traceId).build();
    }
}
