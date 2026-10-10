BEGIN
EXECUTE IMMEDIATE q'~
        CREATE TABLE IF NOT EXISTS card_types (
    id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    name VARCHAR2(200 CHAR) NOT NULL,
    bin VARCHAR2(6 CHAR) NOT NULL,
    is_active NUMBER(1, 0) DEFAULT 1 NOT NULL,
    created_by VARCHAR2(100 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by VARCHAR2(100 CHAR) NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_card_types PRIMARY KEY (id),
    CONSTRAINT uq_ct_bin UNIQUE (bin),
    CONSTRAINT ck_ct_name CHECK (TRIM(name) IS NOT NULL),
    CONSTRAINT ck_ct_bin CHECK (REGEXP_LIKE(bin, '^[0-9]{6}$', 'c')),
    CONSTRAINT ck_ct_active CHECK (is_active IN (0, 1)),
    CONSTRAINT ck_ct_created_by CHECK (TRIM(created_by) IS NOT NULL),
    CONSTRAINT ck_ct_updated_by CHECK (TRIM(updated_by) IS NOT NULL)
        )
    ~';
END;
/
