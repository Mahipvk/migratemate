package com.bank.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/** Calls an external FX service that returns a plain-text rate, e.g. "0.92". */
@Component
public class FxRateClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FxRateClient(RestTemplate restTemplate, @Value("${fx.base-url:http://fx.example}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public BigDecimal rate(String from, String to) {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/rate?from={from}&to={to}", String.class, from, to);
        HttpStatus status = response.getStatusCode();
        if (status != HttpStatus.OK || response.getBody() == null) {
            throw new IllegalStateException("FX service returned " + status.value());
        }
        return new BigDecimal(response.getBody().trim());
    }
}
