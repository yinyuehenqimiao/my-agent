package com.myagent.framework.exception;

import com.myagent.framework.errorcode.BaseErrorCode;
import com.myagent.framework.errorcode.IErrorCode;

import java.util.Optional;

/**
 * 服务端异常（业务未达预期）
 */
public class ServiceException extends AbstractException {

    public ServiceException(String message) {
        this(message, null, BaseErrorCode.SERVICE_ERROR);
    }

    public ServiceException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ServiceException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ServiceException(String message, Throwable cause, IErrorCode errorCode) {
        super(Optional.ofNullable(message).orElse(errorCode.message()), cause, errorCode);
    }

    @Override
    public String toString() {
        return "ServiceException{code='" + getErrorCode() + "', message='" + getErrorMessage() + "'}";
    }
}
