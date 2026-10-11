package com.bank.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FxRateClientTest {

    private MockRestServiceServer server;
    private FxRateClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new FxRateClient(restTemplate, "http://fx.example");
    }

    @Test
    void returnsRate() {
        server.expect(requestTo("http://fx.example/rate?from=USD&to=EUR"))
                .andRespond(withSuccess("0.92", MediaType.TEXT_PLAIN));

        assertEquals(new BigDecimal("0.92"), client.rate("USD", "EUR"));
        server.verify();
    }

    @Test
    void failsWhenServiceHasNoRate() {
        server.expect(requestTo("http://fx.example/rate?from=USD&to=XYZ"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThrows(IllegalStateException.class, () -> client.rate("USD", "XYZ"));
        server.verify();
    }
}
