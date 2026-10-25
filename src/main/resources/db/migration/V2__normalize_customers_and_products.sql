-- Expand: переносим данные и добавляем связи, сохраняя старый формат до contract.
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(50),
    CONSTRAINT customers_contact_unique UNIQUE NULLS NOT DISTINCT (full_name, address, phone)
);

INSERT INTO customers (full_name, address, phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone FROM orders;

ALTER TABLE orders ADD COLUMN customer_id BIGINT REFERENCES customers(id);

UPDATE orders o
SET customer_id = c.id
FROM customers c
WHERE c.full_name = o.customer_full_name
  AND c.address IS NOT DISTINCT FROM o.customer_address
  AND c.phone IS NOT DISTINCT FROM o.customer_phone;

ALTER TABLE orders ALTER COLUMN customer_id SET NOT NULL;
CREATE INDEX orders_customer_id_idx ON orders(customer_id);

-- Старые позиции не содержат идентификатора товара. Если соответствующая
-- пара названия и цены отсутствует в каталоге, восстанавливаем её из заказа.
INSERT INTO products (name, price)
SELECT DISTINCT i.product_name, i.product_price
FROM order_items i
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ADD COLUMN product_id BIGINT REFERENCES products(id);

-- При нескольких одинаковых товарах выбираем наименьший идентификатор:
-- точную исходную связь нельзя восстановить из старой схемы.
UPDATE order_items i
SET product_id = (
    SELECT MIN(p.id) FROM products p WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ALTER COLUMN product_id SET NOT NULL;
CREATE INDEX order_items_product_id_idx ON order_items(product_id);

-- Старые экземпляры приложения пишут контакты, новые — customer_id.
-- На переходном этапе оба формата должны оставаться работоспособными.
CREATE FUNCTION synchronize_order_customer() RETURNS TRIGGER AS $$
DECLARE
    legacy_changed BOOLEAN := FALSE;
    resolved_customer_id BIGINT;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        legacy_changed := NEW.customer_id IS NOT DISTINCT FROM OLD.customer_id
            AND ROW(NEW.customer_full_name, NEW.customer_address, NEW.customer_phone)
                IS DISTINCT FROM ROW(OLD.customer_full_name, OLD.customer_address, OLD.customer_phone);
    END IF;

    IF NEW.customer_id IS NULL OR legacy_changed THEN
        SELECT c.id INTO resolved_customer_id FROM customers c
        WHERE c.full_name = NEW.customer_full_name
          AND c.address IS NOT DISTINCT FROM NEW.customer_address
          AND c.phone IS NOT DISTINCT FROM NEW.customer_phone;

        IF resolved_customer_id IS NULL THEN
            INSERT INTO customers (full_name, address, phone)
            VALUES (NEW.customer_full_name, NEW.customer_address, NEW.customer_phone)
            ON CONFLICT (full_name, address, phone) DO UPDATE SET full_name = EXCLUDED.full_name
            RETURNING id INTO resolved_customer_id;
        END IF;
        NEW.customer_id := resolved_customer_id;
    END IF;

    SELECT c.full_name, c.address, c.phone
    INTO NEW.customer_full_name, NEW.customer_address, NEW.customer_phone
    FROM customers c WHERE c.id = NEW.customer_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER orders_synchronize_customer
BEFORE INSERT OR UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION synchronize_order_customer();

-- Обновления клиента также видны потребителям старых полей заказа.
CREATE FUNCTION synchronize_customer_order_contacts() RETURNS TRIGGER AS $$
BEGIN
    UPDATE orders
    SET customer_full_name = NEW.full_name,
        customer_address = NEW.address,
        customer_phone = NEW.phone
    WHERE customer_id = NEW.id
      AND ROW(customer_full_name, customer_address, customer_phone)
          IS DISTINCT FROM ROW(NEW.full_name, NEW.address, NEW.phone);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER customers_synchronize_order_contacts
AFTER UPDATE ON customers
FOR EACH ROW WHEN (
    ROW(OLD.full_name, OLD.address, OLD.phone) IS DISTINCT FROM ROW(NEW.full_name, NEW.address, NEW.phone)
)
EXECUTE FUNCTION synchronize_customer_order_contacts();

-- Поддерживаем старый формат позиций до перевода всех писателей на product_id.
CREATE FUNCTION synchronize_order_item_product() RETURNS TRIGGER AS $$
DECLARE
    legacy_changed BOOLEAN := FALSE;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        legacy_changed := NEW.product_id IS NOT DISTINCT FROM OLD.product_id
            AND ROW(NEW.product_name, NEW.product_price) IS DISTINCT FROM ROW(OLD.product_name, OLD.product_price);
    END IF;

    IF NEW.product_id IS NULL OR legacy_changed THEN
        SELECT MIN(p.id) INTO NEW.product_id FROM products p
        WHERE p.name = NEW.product_name AND p.price = NEW.product_price;
        IF NEW.product_id IS NULL THEN
            INSERT INTO products (name, price) VALUES (NEW.product_name, NEW.product_price)
            RETURNING id INTO NEW.product_id;
        END IF;
    END IF;

    SELECT p.name, p.price INTO NEW.product_name, NEW.product_price
    FROM products p WHERE p.id = NEW.product_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER order_items_synchronize_product
BEFORE INSERT OR UPDATE ON order_items
FOR EACH ROW EXECUTE FUNCTION synchronize_order_item_product();
