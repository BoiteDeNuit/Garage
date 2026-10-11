package com.example.model;

// Ниже первого квартиля — дешевле рынка, выше третьего — дороже, между ними — в рынке
public enum MarketVerdict {
    BELOW_MARKET,
    IN_MARKET,
    ABOVE_MARKET,
    NOT_ENOUGH_DATA
}
