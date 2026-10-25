-- Expand: старое поле остаётся доступным старым экземплярам приложения.
SET LOCAL lock_timeout = '5s';

ALTER TABLE customers
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255);

-- Триггер поддерживает оба направления записи на время перехода.
CREATE FUNCTION synchronize_customer_names() RETURNS TRIGGER AS $$
DECLARE
    full_name_changed BOOLEAN;
    name_parts_changed BOOLEAN;
BEGIN
    IF TG_OP = 'INSERT' THEN
        full_name_changed := NEW.full_name IS NOT NULL;
        name_parts_changed := NEW.first_name IS NOT NULL;
    ELSE
        full_name_changed := NEW.full_name IS DISTINCT FROM OLD.full_name;
        name_parts_changed := NEW.first_name IS DISTINCT FROM OLD.first_name
                              OR NEW.last_name IS DISTINCT FROM OLD.last_name;
    END IF;

    IF full_name_changed AND name_parts_changed THEN
        IF NEW.full_name IS DISTINCT FROM concat_ws(' ', NEW.first_name, NEW.last_name) THEN
            RAISE EXCEPTION 'Противоречивые версии имени клиента' USING ERRCODE = '23514';
        END IF;
    ELSIF name_parts_changed THEN
        NEW.full_name := concat_ws(' ', NEW.first_name, NEW.last_name);
    ELSIF full_name_changed OR NEW.first_name IS NULL THEN
        -- Делим по первому пробелу, сохраняя все остальные символы.
        -- NULL обозначает отсутствие фамилии, пустая строка — завершающий пробел.
        NEW.first_name := split_part(NEW.full_name, ' ', 1);
        NEW.last_name := CASE WHEN strpos(NEW.full_name, ' ') > 0
            THEN substr(NEW.full_name, strpos(NEW.full_name, ' ') + 1)
            ELSE NULL END;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER customers_synchronize_names
BEFORE INSERT OR UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION synchronize_customer_names();
