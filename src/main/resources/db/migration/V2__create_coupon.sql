CREATE TABLE coupon
(
    id           UUID                     NOT NULL PRIMARY KEY,
    coupon_code  VARCHAR(64)              NOT NULL,
    country_code VARCHAR(2)               NOT NULL REFERENCES country (code),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    max_usages   INTEGER                  NOT NULL,
    used_count   INTEGER                  NOT NULL DEFAULT 0,
    CONSTRAINT ck_coupon_max_usages_positive CHECK (max_usages > 0),
    CONSTRAINT ck_coupon_used_count_within_limit CHECK (used_count >= 0 AND used_count <= max_usages)
);

CREATE UNIQUE INDEX ux_coupon_code_case_insensitive ON coupon (upper(coupon_code));

CREATE INDEX ix_coupon_country_code ON coupon (country_code);
