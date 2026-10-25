-- Подготавливаем ограничения в отдельной транзакции после backfill.
-- Это исключает повышение блокировки таблицы при удержании блокировок строк.
SET LOCAL lock_timeout = '5s';

ALTER TABLE customers
    ALTER COLUMN first_name SET NOT NULL,
    ADD CONSTRAINT customers_name_parts_contact_unique
        UNIQUE NULLS NOT DISTINCT (first_name, last_name, address, phone);

-- После этого этапа можно развернуть новую версию приложения,
-- которая читает и пишет first_name/last_name. Старая версия ещё совместима.
