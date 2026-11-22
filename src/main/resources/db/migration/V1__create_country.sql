CREATE TABLE country
(
    code VARCHAR(2)   NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_country_code_upper CHECK (code = upper(code))
);
