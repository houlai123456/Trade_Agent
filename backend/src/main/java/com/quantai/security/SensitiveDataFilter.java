package com.quantai.security;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.regex.Pattern;

public class SensitiveDataFilter extends Filter<ILoggingEvent> {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(sk-[a-zA-Z0-9]{32,})" +
            "|(Bearer\\s+[a-zA-Z0-9\\-_.]+)" +
            "|(api[_-]?key[\"']?\\s*[:=]\\s*[\"']?[a-zA-Z0-9]{20,})" +
            "|(password[\"']?\\s*[:=]\\s*[\"']?[^\\s\"']+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String message = event.getFormattedMessage();

        if (message != null && SENSITIVE_PATTERN.matcher(message).find()) {
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }
}
