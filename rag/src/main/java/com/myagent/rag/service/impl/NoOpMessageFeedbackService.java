package com.myagent.rag.service.impl;

import com.myagent.rag.controller.request.MessageFeedbackRequest;
import com.myagent.rag.mq.event.MessageFeedbackEvent;
import com.myagent.rag.service.MessageFeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NoOpMessageFeedbackService implements MessageFeedbackService {

    @Override
    public void submitFeedback(String messageId, MessageFeedbackRequest request) {
    }

    @Override
    public void submitFeedbackAsync(String messageId, MessageFeedbackRequest request) {
    }

    @Override
    public void cancelFeedbackAsync(String messageId) {
    }

    @Override
    public void submitFeedbackByEvent(MessageFeedbackEvent event) {
        log.debug("MessageFeedbackService noop, messageId={}", event != null ? event.getMessageId() : null);
    }

    @Override
    public Map<String, Integer> getUserVotes(String userId, List<String> messageIds) {
        return Collections.emptyMap();
    }
}
