package com.example.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
@JsonIgnoreProperties(ignoreUnknown = true)
public record CbrResponse(
        @JsonProperty("Valute") Map<String, Valute> valute,
        @JsonProperty("Date")OffsetDateTime date
) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Valute(
                @JsonProperty("CharCode")
                        String charCode,
                @JsonProperty("Nominal")
                        Integer nominal,
                @JsonProperty("Value")
                        BigDecimal value
        ) {}
}


