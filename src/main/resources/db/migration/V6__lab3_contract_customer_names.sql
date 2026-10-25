-- Contract: выполняется после перевода всех экземпляров приложения на новые поля.
-- При поэтапном развёртывании сначала ограничивают Flyway target=5,
-- затем обновляют приложение и только после этого разрешают миграцию V6.
SET LOCAL lock_timeout = '5s';

DROP TRIGGER customers_synchronize_names ON customers;
DROP FUNCTION synchronize_customer_names();

ALTER TABLE customers
    DROP CONSTRAINT customers_contact_unique,
    DROP COLUMN full_name;
