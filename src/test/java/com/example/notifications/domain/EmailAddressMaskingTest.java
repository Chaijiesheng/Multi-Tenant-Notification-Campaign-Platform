package com.example.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressMaskingTest {

    @Test
    void masksStandardEmail() {
        EmailAddress email = new EmailAddress("john@example.com");
        assertThat(email.masked()).isEqualTo("j****@example.com");
    }

    @Test
    void masksSingleCharLocal() {
        EmailAddress email = new EmailAddress("a@test.org");
        assertThat(email.masked()).isEqualTo("a****@test.org");
    }

    @Test
    void masksLongLocalPart() {
        EmailAddress email = new EmailAddress("verylongname@domain.io");
        assertThat(email.masked()).isEqualTo("v****@domain.io");
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> new EmailAddress("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankEmail() {
        assertThatThrownBy(() -> new EmailAddress(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
