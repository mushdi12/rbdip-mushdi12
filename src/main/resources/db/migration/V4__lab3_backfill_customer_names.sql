-- Backfill: заполняем новые поля, сохраняя доступность full_name.
SET LOCAL lock_timeout = '5s';

UPDATE customers
SET first_name = split_part(full_name, ' ', 1),
    last_name = CASE WHEN strpos(full_name, ' ') > 0
        THEN substr(full_name, strpos(full_name, ' ') + 1)
        ELSE NULL END
WHERE first_name IS NULL;
