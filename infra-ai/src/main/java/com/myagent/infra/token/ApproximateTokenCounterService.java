package com.myagent.infra.token;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApproximateTokenCounterService implements TokenCounterService {

    @Override
    public Integer countTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
