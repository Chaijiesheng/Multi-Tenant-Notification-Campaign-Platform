package com.example.notifications.config;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiiMaskingConverter extends MessageConverter {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "([a-zA-Z0-9._%+\\-])([a-zA-Z0-9._%+\\-]*)(@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?[0-9]{2,4})([0-9]{4,10})([0-9]{2})");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) return "";

        // Mask emails first
        Matcher emailMatcher = EMAIL_PATTERN.matcher(message);
        message = emailMatcher.replaceAll("$1****$3");

        // Mask phone numbers
        Matcher phoneMatcher = PHONE_PATTERN.matcher(message);
        message = phoneMatcher.replaceAll("$1****$3");

        return message;
    }
}
