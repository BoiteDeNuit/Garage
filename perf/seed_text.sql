-- Описания для замеров полнотекстового поиска. Запускать после seed.sql: в seed.sql описание
-- у всех одно ('Сид для замеров #n'), искать по нему нечего.
--   psql -h localhost -p 5433 -U garage -d garage -f perf/seed_text.sql
-- В каждом описании три разные фразы из двадцати, каждая фраза встречается примерно в 15% объявлений.
-- «Панорамная крыша» — редкая, примерно в 0,2%. setseed делает данные одинаковыми от прогона к прогону,
-- и повторный запуск пишет те же описания.
-- Если V12 уже применена, UPDATE пересчитывает search_vector и пишет в GIN: так меряется цена обновления
\set ON_ERROR_STOP on
\timing on

SELECT setseed(0.17);

WITH phrases AS (
    SELECT ARRAY['Небольшой пробег', 'Один владелец по ПТС', 'Не бита, не крашена', 'Зимняя резина в подарок',
                 'Обслуживалась у официального дилера', 'Торг у капота', 'Срочная продажа, переезд', 'Полный привод',
                 'Кожаный салон, подогрев сидений', 'Требует покраски крыла', 'После ДТП, восстановлена',
                 'Гаражное хранение', 'Новые тормозные колодки и диски', 'Камера заднего вида и парктроники',
                 'Вложений не требует', 'Свежее масло, заменён ремень ГРМ', 'Обмен не интересует',
                 'Пробег родной, есть сервисная книжка', 'Летняя и зимняя резина на дисках', 'Дизель, расход 7 литров'] AS p
), picks AS (
    -- Шаги 1–6 по кругу из 20 дают три разные фразы. random() считается над уже отсортированным
    -- подзапросом: в порядке id, а не в физическом порядке строк, который меняют UPDATE и VACUUM FULL
    SELECT id,
           floor(random() * 20)::int AS i1,
           1 + floor(random() * 6)::int AS step2,
           1 + floor(random() * 6)::int AS step3,
           random() AS rare
    FROM (SELECT id FROM listings ORDER BY id) AS ordered
)
UPDATE listings l
SET description = ph.p[1 + k.i1] || '. ' || ph.p[1 + (k.i1 + k.step2) % 20] || '. '
                      || ph.p[1 + (k.i1 + k.step2 + k.step3) % 20]
                      || CASE WHEN k.rare < 0.002 THEN '. Панорамная крыша' ELSE '' END
FROM picks k, phrases ph
WHERE l.id = k.id;

-- UPDATE пишет новую версию каждой строки, и таблица вырастает вдвое. VACUUM FULL переписывает её
-- начисто, чтобы замеры не читали мёртвое место. Обычный VACUUM ANALYZE здесь падал с No space left on device:
-- параллельной уборке миллиона мёртвых строк нужно около 64 МБ разделяемой памяти, это весь /dev/shm
-- контейнера Docker по умолчанию
VACUUM FULL listings;
ANALYZE listings;

SELECT count(*) FILTER (WHERE description LIKE 'Небольшой пробег%' OR description LIKE '%. Небольшой пробег%') AS small_mileage,
       count(*) FILTER (WHERE description LIKE '%Панорамная крыша%') AS panorama
FROM listings WHERE status = 'ACTIVE';
SELECT pg_size_pretty(pg_total_relation_size('listings')) AS listings_size;
