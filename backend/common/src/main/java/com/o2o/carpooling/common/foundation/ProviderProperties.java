package com.o2o.carpooling.common.foundation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selects which adapter implementation backs each external <em>business</em> capability.
 * Infrastructure (MySQL/Redis/RabbitMQ/MongoDB/MinIO/Nacos) is never selectable here — it is
 * always real. Each capability resolves to {@code demo} (interactive mock, demo profile only)
 * or a real provider name (e.g. {@code aliyun}, {@code stripe}, {@code amap}) wired through
 * secure environment configuration. An empty type means "not configured": the demo bean is not
 * created and any real call fails closed at invocation time, so staging/production still boot.
 */
@ConfigurationProperties(prefix = "providers")
public class ProviderProperties {

    public static final String DEMO = "demo";

    private Provider sms = new Provider();
    private Provider ocr = new Provider();
    private Provider payment = new Provider();
    private Provider identity = new Provider();
    private Provider map = new Provider();
    private Provider notification = new Provider();

    public Provider getSms() {
        return sms;
    }

    public void setSms(Provider sms) {
        this.sms = sms;
    }

    public Provider getOcr() {
        return ocr;
    }

    public void setOcr(Provider ocr) {
        this.ocr = ocr;
    }

    public Provider getPayment() {
        return payment;
    }

    public void setPayment(Provider payment) {
        this.payment = payment;
    }

    public Provider getIdentity() {
        return identity;
    }

    public void setIdentity(Provider identity) {
        this.identity = identity;
    }

    public Provider getMap() {
        return map;
    }

    public void setMap(Provider map) {
        this.map = map;
    }

    public Provider getNotification() {
        return notification;
    }

    public void setNotification(Provider notification) {
        this.notification = notification;
    }

    public static class Provider {
        private String type = "";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isDemo() {
            return DEMO.equalsIgnoreCase(type);
        }
    }
}
