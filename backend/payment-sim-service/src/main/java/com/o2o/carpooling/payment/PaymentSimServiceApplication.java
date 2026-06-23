package com.o2o.carpooling.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class PaymentSimServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentSimServiceApplication.class, args);
    }
}
