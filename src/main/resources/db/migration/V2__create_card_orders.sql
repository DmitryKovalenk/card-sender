BEGIN
EXECUTE IMMEDIATE q'~
        CREATE TABLE IF NOT EXISTS card_orders (
    id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    card_type_id NUMBER(19, 0) NOT NULL,
    first_name VARCHAR2(100 CHAR) NOT NULL,
    last_name VARCHAR2(100 CHAR) NOT NULL,
    card_number VARCHAR2(16 CHAR),
    status VARCHAR2(20 CHAR) DEFAULT 'PENDING' NOT NULL,
    generated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR2(100 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(100 CHAR) NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_card_orders PRIMARY KEY (id),
    CONSTRAINT fk_co_card_type FOREIGN KEY (card_type_id) REFERENCES card_types (id),
    -- Multiple pending orders may have NULL; every generated number is unique.
    CONSTRAINT uq_co_card_number UNIQUE (card_number),
    CONSTRAINT ck_co_first_name CHECK (
        REGEXP_LIKE(first_name, '^[A-Za-z]+([ ''-][A-Za-z]+)*$', 'c')
    ),
    CONSTRAINT ck_co_last_name CHECK (
        REGEXP_LIKE(last_name, '^[A-Za-z]+([ ''-][A-Za-z]+)*$', 'c')
    ),
    CONSTRAINT ck_co_card_number CHECK (
        card_number IS NULL
        OR REGEXP_LIKE(card_number, '^[0-9]{16}$', 'c')
    ),
    CONSTRAINT ck_co_status CHECK (status IN ('PENDING', 'GENERATED')),
    CONSTRAINT ck_co_generation_state CHECK (
        (
            status = 'PENDING'
            AND card_number IS NULL
            AND generated_at IS NULL
        )
        OR (
            status = 'GENERATED'
            AND card_number IS NOT NULL
            AND generated_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_co_created_by CHECK (TRIM(created_by) IS NOT NULL),
    CONSTRAINT ck_co_updated_by CHECK (TRIM(updated_by) IS NOT NULL)
        )
    ~';
END;
/
