package com.example.client;

import com.example.exception.ExternalServiceException;
import com.example.exception.UnknownCurrencyException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class CurrencyClientTest
{
    private static final String CBR_JSON = """
            {"Date":"2026-09-19T11:30:00+03:00","PreviousDate":"2026-09-18T11:30:00+03:00",
             "Valute":{
               "USD":{"ID":"R01235","CharCode":"USD","Nominal":1,"Value":84.1975,"Previous":84.1732},
               "JPY":{"ID":"R01820","CharCode":"JPY","Nominal":100,"Value":53.5948,"Previous":54.1206}}}
            """;
    private static final String CBR_CONTENT_TYPE = "application/javascript; charset=utf-8";

    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status;
    private volatile String contentType;
    private volatile String body;
    private volatile long delayMs;

    @BeforeEach
    void startServer() throws IOException
    {
        respond(200, CBR_CONTENT_TYPE, CBR_JSON);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            pause(delayMs);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(bytes);
            }
        });
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer()
    {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void returnsUsdRate()
    {
        assertThat(client().rateToRub("USD")).isEqualByComparingTo("84.1975");
    }

    @Test
    void dividesByNominal()
    {
        assertThat(client().rateToRub("JPY")).isEqualByComparingTo("0.535948");
    }

    @Test
    void acceptsLowercaseCode()
    {
        assertThat(client().rateToRub("usd")).isEqualByComparingTo("84.1975");
    }

    @Test
    void unknownCurrencyIsClientError()
    {
        assertThatThrownBy(() -> client().rateToRub("XYZ"))
                .isInstanceOf(UnknownCurrencyException.class);
    }

    @Test
    void blankCodeIsRejectedWithoutCallingCbr()
    {
        assertThatThrownBy(() -> client().rateToRub("   "))
                .isInstanceOf(UnknownCurrencyException.class);
        assertThat(requests.get()).isZero();
    }

    @Test
    void cbrServerErrorBecomesExternalServiceException()
    {
        respond(500, "application/json", "{\"error\":\"boom\"}");
        assertThatThrownBy(() -> client().rateToRub("USD"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("500");
    }

    @Test
    void brokenJsonBecomesExternalServiceException()
    {
        respond(200, CBR_CONTENT_TYPE, "{\"Valute\": {\"USD\": {\"Value\": 84.5");
        assertThatThrownBy(() -> client().rateToRub("USD"))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void valuteWithoutValueBecomesExternalServiceException()
    {
        respond(200, CBR_CONTENT_TYPE, "{\"Valute\":{\"USD\":{\"CharCode\":\"USD\",\"Nominal\":1}}}");
        assertThatThrownBy(() -> client().rateToRub("USD"))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void slowCbrHitsReadTimeout()
    {
        delayMs = 1500;
        assertThatThrownBy(() -> client(Duration.ofMillis(300)).rateToRub("USD"))
                .isInstanceOf(ExternalServiceException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    private CurrencyClient client()
    {
        return client(Duration.ofSeconds(2));
    }

    private CurrencyClient client(Duration readTimeout)
    {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/daily_json.js";
        return new CurrencyClient(url, Duration.ofSeconds(1), readTimeout);
    }

    private void respond(int status, String contentType, String body)
    {
        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    private static void pause(long ms)
    {
        try
        {
            Thread.sleep(ms);
        } catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
