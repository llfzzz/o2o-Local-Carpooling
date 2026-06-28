package com.o2o.carpooling.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSummaryMaskingTest {

    @Test
    void masksPhoneKeepingPrefixAndSuffix() {
        assertThat(UserController.UserSummary.maskPhone("13800000000")).isEqualTo("138****0000");
    }

    @Test
    void masksShortOrNullPhoneFully() {
        assertThat(UserController.UserSummary.maskPhone("123")).isEqualTo("***");
        assertThat(UserController.UserSummary.maskPhone(null)).isEqualTo("***");
    }
}
