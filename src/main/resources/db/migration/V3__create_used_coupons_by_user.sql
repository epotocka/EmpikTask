CREATE TABLE used_coupons_by_user
(
    id        UUID                     NOT NULL PRIMARY KEY,
    coupon_id UUID                     NOT NULL REFERENCES coupon (id),
    user_id   UUID                     NOT NULL,
    used_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_used_coupons_by_user_coupon_user UNIQUE (coupon_id, user_id)
);
