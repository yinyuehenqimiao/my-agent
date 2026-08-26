package com.myagent.framework.web;

import com.myagent.framework.convention.Result;
import com.myagent.framework.errorcode.BaseErrorCode;
import com.myagent.framework.exception.AbstractException;

import java.util.Optional;

/**
 * Result 构造器
 */
public final class Results {

    private Results() {
    }

    public static Result<Void> success() {
        return new Result<Void>().setCode(Result.SUCCESS_CODE);
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>().setCode(Result.SUCCESS_CODE).setData(data);
    }

    public static Result<Void> failure() {
        return new Result<Void>()
                .setCode(BaseErrorCode.SERVICE_ERROR.code())
                .setMessage(BaseErrorCode.SERVICE_ERROR.message());
    }

    public static Result<Void> failure(AbstractException ex) {
        String code = Optional.ofNullable(ex.getErrorCode()).orElse(BaseErrorCode.SERVICE_ERROR.code());
        String message = Optional.ofNullable(ex.getErrorMessage()).orElse(BaseErrorCode.SERVICE_ERROR.message());
        return new Result<Void>().setCode(code).setMessage(message);
    }

    public static Result<Void> failure(String errorCode, String errorMessage) {
        return new Result<Void>().setCode(errorCode).setMessage(errorMessage);
    }
}
