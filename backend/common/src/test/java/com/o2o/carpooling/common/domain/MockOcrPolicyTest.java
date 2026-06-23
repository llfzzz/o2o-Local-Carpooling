package com.o2o.carpooling.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockOcrPolicyTest {

    @Test
    void returnsReviewableDriverLicenseSignals() {
        MockOcrPolicy policy = new MockOcrPolicy();

        OcrResult result = policy.inspect("file-driving-license-001");

        assertThat(result.provider()).isEqualTo("mock-ocr");
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.80);
        assertThat(result.fields()).containsKeys("name", "licenseNo", "expiresAt");
    }
}
