package com.example.service;

import com.example.dto.ListingDto;
import com.example.dto.MarketPriceDto;
import com.example.exception.EntityNotFoundException;
import com.example.model.MarketVerdict;
import com.example.repository.ListingRepository;
import com.example.repository.MarketPriceView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

// Оценка цены по рынку. Только для опубликованных и проданных, как цена в валюте: черновик никому не оцениваем.
// Цифры из market_prices — им до часа, сама цена — из карточки
@Service
public class MarketPriceService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final ListingReader reader;
    private final ListingAccessPolicy policy;
    private final ListingRepository repository;
    public MarketPriceService(ListingReader reader, ListingAccessPolicy policy, ListingRepository repository)
    {
        this.reader=reader;
        this.policy=policy;
        this.repository=repository;
    }
    @Transactional(readOnly = true)
    public MarketPriceDto estimate(Long listingId)
    {
        ListingDto card = reader.findCard(listingId);
        if(!policy.publicStatuses().contains(card.status()))
        {
            throw EntityNotFoundException.listing(listingId);
        }
        // На свежей базе представление ещё не посчитано: запрос к нему упал бы с 500. Считает его MarketPriceRefresh
        // при старте и раз в час, а до того оценки просто нет
        Optional<MarketPriceView> market = Boolean.TRUE.equals(repository.marketPricesPopulated())
                ? repository.findMarketPrice(card.brand(), card.model(), card.year())
                : Optional.empty();
        if(market.isEmpty() || card.price() == null)
        {
            return new MarketPriceDto(listingId, card.price(), MarketVerdict.NOT_ENOUGH_DATA, null, null, null, null, null, null);
        }
        MarketPriceView stats = market.get();
        BigDecimal p25 = stats.getP25();
        BigDecimal median = stats.getMedian();
        BigDecimal p75 = stats.getP75();
        // compareTo, а не equals: масштаб у цены карточки и у перцентиля разный
        MarketVerdict verdict = card.price().compareTo(p25) < 0 ? MarketVerdict.BELOW_MARKET
                : card.price().compareTo(p75) > 0 ? MarketVerdict.ABOVE_MARKET
                : MarketVerdict.IN_MARKET;
        BigDecimal delta = card.price().subtract(median).multiply(HUNDRED).divide(median, 1, RoundingMode.HALF_UP);
        return new MarketPriceDto(listingId, card.price(), verdict, stats.getListings(), p25, median, p75, delta, stats.getCalculatedAt());
    }
}
