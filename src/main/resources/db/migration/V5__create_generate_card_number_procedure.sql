-- Generates a number for one order. The caller owns COMMIT / ROLLBACK.
-- Repeated or concurrent calls for the same order return the existing number.
CREATE OR REPLACE PROCEDURE generate_card_number (
        p_card_order_id IN card_orders.id %TYPE,
        p_operator IN card_orders.updated_by %TYPE,
        p_card_number OUT card_orders.card_number %TYPE
    ) AUTHID DEFINER AS v_card_type_id card_orders.card_type_id %TYPE;
v_bin card_types.bin %TYPE;
v_prefix VARCHAR2(15 CHAR);
v_candidate card_orders.card_number %TYPE;
v_sum PLS_INTEGER;
v_digit PLS_INTEGER;
BEGIN IF TRIM(p_operator) IS NULL
OR LENGTH(p_operator) > 100 THEN RAISE_APPLICATION_ERROR(
    -20001,
    'Operator must contain 1 to 100 characters'
);
END IF;
SELECT card_type_id,
    card_number INTO v_card_type_id,
    p_card_number
FROM card_orders
WHERE id = p_card_order_id FOR
UPDATE;
IF p_card_number IS NOT NULL THEN RETURN;
END IF;
SELECT bin INTO v_bin
FROM card_types
WHERE id = v_card_type_id;
FOR attempt IN 1..10 LOOP v_prefix := v_bin || TO_CHAR(
    TRUNC(DBMS_RANDOM.VALUE(0, 1000000000)),
    'FM000000000'
);
v_sum := 0;
FOR position IN 1..15 LOOP v_digit := TO_NUMBER(SUBSTR(v_prefix, position, 1));
IF MOD(position, 2) = 1 THEN v_digit := v_digit * 2;
IF v_digit > 9 THEN v_digit := v_digit - 9;
END IF;
END IF;
v_sum := v_sum + v_digit;
END LOOP;
v_candidate := v_prefix || TO_CHAR(
    MOD(10 - MOD(v_sum, 10), 10),
    'FM0'
);
BEGIN
UPDATE card_orders
SET card_number = v_candidate,
    status = 'GENERATED',
    generated_at = SYSTIMESTAMP,
    updated_by = TRIM(p_operator),
    updated_at = SYSTIMESTAMP
WHERE id = p_card_order_id;
p_card_number := v_candidate;
RETURN;
EXCEPTION
WHEN DUP_VAL_ON_INDEX THEN NULL;
END;
END LOOP;
RAISE_APPLICATION_ERROR(
    -20002,
    'Could not allocate a unique card number after 10 attempts'
);
EXCEPTION
WHEN NO_DATA_FOUND THEN RAISE_APPLICATION_ERROR(
    -20003,
    'Card order not found'
);
END generate_card_number;
/
