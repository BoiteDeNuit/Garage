-- Топливо, коробка и кузов для поиска. Необязательные: старые объявления их не знают,
-- а продавец может заполнить позже
ALTER TABLE listings
    ADD COLUMN fuel_type    VARCHAR(20),
    ADD COLUMN transmission VARCHAR(20),
    ADD COLUMN body_type    VARCHAR(20),
    ADD CONSTRAINT chk_listings_fuel_type CHECK (
        fuel_type IS NULL OR fuel_type IN ('PETROL', 'DIESEL', 'HYBRID', 'ELECTRIC', 'GAS')),
    ADD CONSTRAINT chk_listings_transmission CHECK (
        transmission IS NULL OR transmission IN ('MANUAL', 'AUTOMATIC', 'ROBOT', 'CVT')),
    ADD CONSTRAINT chk_listings_body_type CHECK (
        body_type IS NULL OR body_type IN ('SEDAN', 'HATCHBACK', 'WAGON', 'SUV', 'COUPE', 'CONVERTIBLE', 'MINIVAN', 'PICKUP'));
