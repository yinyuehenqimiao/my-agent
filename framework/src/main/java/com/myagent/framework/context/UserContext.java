package com.myagent.framework.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.myagent.framework.exception.ClientException;

/**
 * 用户上下文（基于 TTL，可跨线程池传递）
 * <p>
 * AI 流式场景常在独立线程推 SSE，普通 ThreadLocal 会丢上下文，所以用 TTL。
 */
public final class UserContext {

    private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static LoginUser requireUser() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new ClientException("未获取到当前登录用户");
        }
        return user;
    }

    public static String getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static boolean hasUser() {
        return CONTEXT.get() != null;
    }
}
