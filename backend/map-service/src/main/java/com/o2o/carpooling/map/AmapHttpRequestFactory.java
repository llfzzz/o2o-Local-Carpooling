package com.o2o.carpooling.map;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;

@Component
class AmapHttpRequestFactory {

    private final AmapProperties properties;

    AmapHttpRequestFactory(AmapProperties properties) {
        this.properties = properties;
    }

    ClientHttpRequestFactory create() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }
}
