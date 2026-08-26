package com.myagent.rag.service.impl;

import com.myagent.rag.mq.event.MessageFeedbackEvent;
import com.myagent.rag.service.MessageFeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NoOpMessageFeedbackService implements MessageFeedbackService {

    @Override
    public void submitFeedbackByEvent(MessageFeedbackEvent event) {
        log.warn("MessageFeedbackService 未实现，忽略反馈事件 messageId={}",
                event != null ? event.getMessageId() : null);
    }
}
