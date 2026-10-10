BEGIN
EXECUTE IMMEDIATE q'~
        CREATE TABLE IF NOT EXISTS shipment_orders (
    id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    card_order_id NUMBER(19, 0) NOT NULL,
    recipient_first_name VARCHAR2(100 CHAR) NOT NULL,
    recipient_last_name VARCHAR2(100 CHAR) NOT NULL,
    recipient_phone VARCHAR2(12 CHAR) NOT NULL,
    recipient_city VARCHAR2(200 CHAR) NOT NULL,
    warehouse_number VARCHAR2(30 CHAR) NOT NULL,
    status VARCHAR2(20 CHAR) DEFAULT 'PENDING' NOT NULL,
    waybill_number VARCHAR2(30 CHAR),
    created_by VARCHAR2(100 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(100 CHAR) NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_shipment_orders PRIMARY KEY (id),
    CONSTRAINT fk_so_card_order FOREIGN KEY (card_order_id) REFERENCES card_orders (id),
    CONSTRAINT uq_so_card_order UNIQUE (card_order_id),
    CONSTRAINT uq_so_waybill UNIQUE (waybill_number),
    CONSTRAINT ck_so_phone CHECK (
        REGEXP_LIKE(recipient_phone, '^380[0-9]{9}$', 'c')
    ),
    CONSTRAINT ck_so_recipient_first CHECK (TRIM(recipient_first_name) IS NOT NULL),
    CONSTRAINT ck_so_recipient_last CHECK (TRIM(recipient_last_name) IS NOT NULL),
    CONSTRAINT ck_so_city CHECK (TRIM(recipient_city) IS NOT NULL),
    CONSTRAINT ck_so_warehouse CHECK (TRIM(warehouse_number) IS NOT NULL),
    CONSTRAINT ck_so_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')
    ),
    CONSTRAINT ck_so_waybill CHECK (
        waybill_number IS NULL
        OR REGEXP_LIKE(waybill_number, '^[0-9]{1,30}$', 'c')
    ),
    CONSTRAINT ck_so_result CHECK (
        (
            status = 'SENT'
            AND waybill_number IS NOT NULL
        )
        OR (
            status IN ('PENDING', 'PROCESSING', 'FAILED')
            AND waybill_number IS NULL
        )
    ),
    CONSTRAINT ck_so_created_by CHECK (TRIM(created_by) IS NOT NULL),
    CONSTRAINT ck_so_updated_by CHECK (TRIM(updated_by) IS NOT NULL)
)
    ~';
END;
/
