package com.myagent.infra.chat;

/**
 * 流式回调
 */
public interface StreamCallback {

    void onContent(String delta);

    default void onComplete() {
    }

    default void onError(Throwable error) {
    }
}
