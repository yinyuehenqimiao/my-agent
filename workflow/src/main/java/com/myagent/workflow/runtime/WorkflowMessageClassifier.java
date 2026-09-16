package com.myagent.workflow.runtime;

import org.springframework.stereotype.Component;

/** 等待用户输入时的保守分类器；只有明确字段输入才推进流程。 */
@Component
public class WorkflowMessageClassifier {
    public MessageKind classify(String message) {
        String text = message == null ? "" : message.trim();
        if (text.matches(".*(取消|算了|不用了).*")) return MessageKind.CANCEL;
        if (text.matches(".*(改成|修改|换成|重新).*")) return MessageKind.MODIFY_FIELD;
        if (text.endsWith("?") || text.endsWith("？") || text.matches(".*(制度|规定|余额|还有多少|能不能|为什么|什么时候).*")) return MessageKind.SIDE_QUESTION;
        return MessageKind.FIELD_INPUT;
    }
    public enum MessageKind { FIELD_INPUT, MODIFY_FIELD, SIDE_QUESTION, CANCEL }
}
