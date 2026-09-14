package com.myagent.rag.core.intent;

/**
 * 意图分类的服务或响应契约异常。
 *
 * <p>该异常与“模型没有找到适合的业务意图”区分开，调用方必须降级提示，不能将其当作
 * 可安全继续执行的空意图。</p>
 */
public class IntentClassificationException extends RuntimeException {

    public IntentClassificationException(String message) {
        super(message);
    }

    public IntentClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
