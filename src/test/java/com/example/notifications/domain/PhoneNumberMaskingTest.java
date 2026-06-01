package com.example.notifications.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberMaskingTest {

    @Test
    void masksStandardPhone() {
        PhoneNumber phone = new PhoneNumber("1234567890");
        assertThat(phone.value()).isEqualTo("123****90");
    }

    @Test
    void masksPhoneWithPlusPrefix() {
        PhoneNumber phone = new PhoneNumber("+441234567890");
        assertThat(phone.value()).isEqualTo("+441****90");
    }

    @Test
    void masksShortPhone() {
        PhoneNumber phone = new PhoneNumber("12345");
        assertThat(phone.value()).isEqualTo("123****45");
    }

    @Test
    void masksTooShortPhone() {
        PhoneNumber phone = new PhoneNumber("1234");
        assertThat(phone.value()).isEqualTo("****");
    }

    @Test
    void masksPhoneWithDashes() {
        PhoneNumber phone = new PhoneNumber("123-456-7890");
        assertThat(phone.value()).isEqualTo("123****90");
    }
}
