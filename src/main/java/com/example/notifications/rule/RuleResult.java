package com.example.notifications.rule;

import java.time.LocalDateTime;

public record RuleResult(Outcome outcome, String reason, LocalDateTime retryAt) {

    public enum Outcome {
        PASS, SKIP, DELAY, REJECT, DISCARD
    }

    public static RuleResult pass() {
        return new RuleResult(Outcome.PASS, null, null);
    }

    public static RuleResult skip(String reason) {
        return new RuleResult(Outcome.SKIP, reason, null);
    }

    public static RuleResult delay(String reason, LocalDateTime retryAt) {
        return new RuleResult(Outcome.DELAY, reason, retryAt);
    }

    public static RuleResult reject(String reason) {
        return new RuleResult(Outcome.REJECT, reason, null);
    }

    public static RuleResult discard(String reason) {
        return new RuleResult(Outcome.DISCARD, reason, null);
    }
}
