package com.myagent.rag.service;

import com.myagent.rag.mq.event.MessageFeedbackEvent;

public interface MessageFeedbackService {
    void submitFeedbackByEvent(MessageFeedbackEvent event);
}
