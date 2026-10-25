-- Contract: выполняется после перевода всех экземпляров приложения на новые поля.
-- При поэтапном развёртывании сначала ограничивают Flyway target=5,
-- затем обновляют приложение и только после этого разрешают миграцию V6.
SET LOCAL lock_timeout = '5s';

-- Чтения и записи могут брать блокировки в разном порядке. Не ждём,
-- удерживая часть блокировок: при неудаче вложенный блок освобождает их.
DO $contract_locks$
BEGIN
    FOR attempt IN 1..100 LOOP
        BEGIN
            LOCK TABLE customers, orders, order_items IN ACCESS EXCLUSIVE MODE NOWAIT;
            RETURN;
        EXCEPTION WHEN lock_not_available THEN
            IF attempt = 100 THEN
                RAISE;
            END IF;
            PERFORM pg_sleep(0.02);
        END;
    END LOOP;
END;
$contract_locks$;

DROP TRIGGER customers_synchronize_order_contacts ON customers;
DROP TRIGGER orders_synchronize_customer ON orders;
DROP TRIGGER order_items_synchronize_product ON order_items;
DROP FUNCTION synchronize_customer_order_contacts();
DROP FUNCTION synchronize_order_customer();
DROP FUNCTION synchronize_order_item_product();

ALTER TABLE orders
    DROP COLUMN customer_full_name,
    DROP COLUMN customer_address,
    DROP COLUMN customer_phone;

ALTER TABLE order_items
    DROP COLUMN product_name,
    DROP COLUMN product_price;

DROP TRIGGER customers_synchronize_names ON customers;
DROP FUNCTION synchronize_customer_names();

ALTER TABLE customers
    DROP CONSTRAINT customers_contact_unique,
    DROP COLUMN full_name;
