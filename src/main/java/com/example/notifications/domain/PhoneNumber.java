package com.example.notifications.domain;

public record PhoneNumber(String value) {
    public PhoneNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be blank");
        }
        value = mask(value);
    }

    private static String mask(String raw) {
        // Strip to digits only for masking, preserve leading +
        String prefix = raw.startsWith("+") ? "+" : "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 5) {
            return prefix + "****";
        }
        String first3 = digits.substring(0, 3);
        String last2 = digits.substring(digits.length() - 2);
        return prefix + first3 + "****" + last2;
    }

    public static PhoneNumber of(String raw) {
        return new PhoneNumber(raw);
    }
}
