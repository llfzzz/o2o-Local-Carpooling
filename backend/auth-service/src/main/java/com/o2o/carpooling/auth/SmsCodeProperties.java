package com.o2o.carpooling.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.sms")
public class SmsCodeProperties {

    private int codeLength = 6;
    private Duration codeTtl = Duration.ofMinutes(5);
    private int maxVerifyAttempts = 5;
    private int issueMaxPerWindow = 5;
    private Duration issueWindow = Duration.ofMinutes(10);

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public Duration getCodeTtl() {
        return codeTtl;
    }

    public void setCodeTtl(Duration codeTtl) {
        this.codeTtl = codeTtl;
    }

    public int getMaxVerifyAttempts() {
        return maxVerifyAttempts;
    }

    public void setMaxVerifyAttempts(int maxVerifyAttempts) {
        this.maxVerifyAttempts = maxVerifyAttempts;
    }

    public int getIssueMaxPerWindow() {
        return issueMaxPerWindow;
    }

    public void setIssueMaxPerWindow(int issueMaxPerWindow) {
        this.issueMaxPerWindow = issueMaxPerWindow;
    }

    public Duration getIssueWindow() {
        return issueWindow;
    }

    public void setIssueWindow(Duration issueWindow) {
        this.issueWindow = issueWindow;
    }
}
