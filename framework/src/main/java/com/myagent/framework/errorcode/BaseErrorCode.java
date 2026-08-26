package com.myagent.framework.errorcode;

/**
 * 基础错误码（阿里巴巴错误码风格精简版）
 * <ul>
 *   <li>A：用户端错误</li>
 *   <li>B：系统执行错误</li>
 *   <li>C：第三方服务错误</li>
 * </ul>
 */
public enum BaseErrorCode implements IErrorCode {

    CLIENT_ERROR("A000001", "用户端错误"),
    SERVICE_ERROR("B000001", "系统执行出错"),
    REMOTE_ERROR("C000001", "调用第三方服务出错");

    private final String code;
    private final String message;

    BaseErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
