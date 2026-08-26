package com.myagent.framework.exception;

import com.myagent.framework.errorcode.BaseErrorCode;
import com.myagent.framework.errorcode.IErrorCode;

/**
 * 客户端异常（参数不合法、未登录等）
 */
public class ClientException extends AbstractException {

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ClientException(String message, Throwable cause, IErrorCode errorCode) {
        super(message, cause, errorCode);
    }

    @Override
    public String toString() {
        return "ClientException{code='" + getErrorCode() + "', message='" + getErrorMessage() + "'}";
    }
}
