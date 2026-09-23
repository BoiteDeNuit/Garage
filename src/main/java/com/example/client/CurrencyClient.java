package com.example.client;

import com.example.exception.ExternalServiceException;
import com.example.exception.UnknownCurrencyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class CurrencyClient {
    private final RestClient client;
    public CurrencyClient(RestClient.Builder builder,
                          @Value("${currency.api.url}") String baseUrl,
                          @Value("${currency.api.connect-timeout}") Duration connectTimeout,
                          @Value("${currency.api.read-timeout}") Duration readTimeout)
    {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        var jsonConverter = new JacksonJsonHttpMessageConverter();
        jsonConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON,MediaType.parseMediaType("application/javascript")));
        this.client = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .configureMessageConverters(c -> c.withJsonConverter(jsonConverter))
                .defaultStatusHandler(HttpStatusCode::isError,((request, response) -> {
                    throw new ExternalServiceException("ЦБР ответил статусом " + response.getStatusCode());
                }))
                .build();
    }

    public BigDecimal rateToRub(String currencyCode)
    {
        CbrResponse response;
        if(currencyCode == null || currencyCode.isBlank())
        {
            throw new UnknownCurrencyException(currencyCode);
        }
        String code = currencyCode.toUpperCase();
        try
        {
            response = client.get()
                    .retrieve()
                    .body(CbrResponse.class);
        } catch (RestClientException e)
        {
            throw new ExternalServiceException("Сервис курсов валют недоступен",e);
        }
        if (response == null || response.valute() == null)
        {
            throw new ExternalServiceException("Пустой ответ от ЦБ");
        }
        CbrResponse.Valute valute = response.valute().get(code);
        if(valute == null)
        {
            throw new UnknownCurrencyException(code);
        }
        if(valute.value() == null || valute.nominal() == null)
        {
            throw new ExternalServiceException("Одно из полей не дошло");
        }
        return valute.value().divide(BigDecimal.valueOf(valute.nominal()),8, RoundingMode.HALF_UP);

    }

}
