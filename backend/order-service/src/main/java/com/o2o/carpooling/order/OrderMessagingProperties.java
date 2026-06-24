package com.o2o.carpooling.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "orders.messaging")
class OrderMessagingProperties {

    private int publishBatchSize = 50;
    private Duration publishFixedDelay = Duration.ofSeconds(10);
    private Duration retryDelay = Duration.ofSeconds(30);
    private final Timeout timeout = new Timeout();

    public int getPublishBatchSize() {
        return publishBatchSize;
    }

    public void setPublishBatchSize(int publishBatchSize) {
        this.publishBatchSize = publishBatchSize;
    }

    public Duration getPublishFixedDelay() {
        return publishFixedDelay;
    }

    public void setPublishFixedDelay(Duration publishFixedDelay) {
        this.publishFixedDelay = publishFixedDelay;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    static class Timeout {
        private String delayExchange = "o2o.order.timeout.delay";
        private String delayQueue = "o2o.order.timeout.delay.queue";
        private String delayRoutingKey = "order.payment.timeout.requested";
        private String expiredExchange = "o2o.order.timeout.expired";
        private String expiredQueue = "o2o.order.timeout.expired.queue";
        private String expiredRoutingKey = "order.payment.timeout.expired";

        public String getDelayExchange() {
            return delayExchange;
        }

        public void setDelayExchange(String delayExchange) {
            this.delayExchange = delayExchange;
        }

        public String getDelayQueue() {
            return delayQueue;
        }

        public void setDelayQueue(String delayQueue) {
            this.delayQueue = delayQueue;
        }

        public String getDelayRoutingKey() {
            return delayRoutingKey;
        }

        public void setDelayRoutingKey(String delayRoutingKey) {
            this.delayRoutingKey = delayRoutingKey;
        }

        public String getExpiredExchange() {
            return expiredExchange;
        }

        public void setExpiredExchange(String expiredExchange) {
            this.expiredExchange = expiredExchange;
        }

        public String getExpiredQueue() {
            return expiredQueue;
        }

        public void setExpiredQueue(String expiredQueue) {
            this.expiredQueue = expiredQueue;
        }

        public String getExpiredRoutingKey() {
            return expiredRoutingKey;
        }

        public void setExpiredRoutingKey(String expiredRoutingKey) {
            this.expiredRoutingKey = expiredRoutingKey;
        }
    }
}
