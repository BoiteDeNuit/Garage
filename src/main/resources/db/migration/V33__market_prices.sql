-- Рыночная цена: квартили цен опубликованных объявлений по марке, модели и году. Считать их на каждый запрос
-- карточки — сортировка всех похожих объявлений, поэтому материализованное представление, его раз в час
-- пересчитывает задача по расписанию (MarketPriceRefresh). Меньше пяти похожих — оценки нет: медиана по двум
-- объявлениям ничего не говорит о рынке.
-- WITH NO DATA: на миллионе строк расчёт занимает секунды, миграция их не ждёт, первый расчёт сделает задача.
-- calculated_at вычисляется при каждом пересчёте: клиент видит, насколько оценка свежая
CREATE MATERIALIZED VIEW market_prices AS
SELECT upper(brand)                                                        AS brand,
       upper(model)                                                        AS model,
       year,
       count(*)                                                            AS listings,
       -- percentile_cont считает в double: между 3 и 4 млн выйдет 3500000.0000001. Обратно в рубли с копейками
       percentile_cont(0.25) WITHIN GROUP (ORDER BY price)::numeric(12, 2) AS p25,
       percentile_cont(0.5) WITHIN GROUP (ORDER BY price)::numeric(12, 2)  AS median,
       percentile_cont(0.75) WITHIN GROUP (ORDER BY price)::numeric(12, 2) AS p75,
       now()                                                               AS calculated_at
FROM listings
WHERE status = 'ACTIVE' AND price IS NOT NULL
GROUP BY upper(brand), upper(model), year
HAVING count(*) >= 5
WITH NO DATA;

-- Уникальный индекс нужен REFRESH ... CONCURRENTLY: по нему пересчёт сравнивает старые строки с новыми
-- и не закрывает представление от чтения. Он же — поиск оценки для карточки
CREATE UNIQUE INDEX uq_market_prices_car ON market_prices (brand, model, year);
