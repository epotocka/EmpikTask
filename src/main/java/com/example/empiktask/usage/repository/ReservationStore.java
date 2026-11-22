package com.example.empiktask.usage.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationStore {

    private static final String KEY_PREFIX = "coupon:reservations:";
    private static final String USER_KEY_PREFIX = "user:reservation:";

    private final StringRedisTemplate redisTemplate;

    public Optional<Instant> findActiveExpiry(UUID couponId, UUID userId, Instant now) {
        Double expiresAtMillis = zSet().score(key(couponId), userId.toString());
        if (expiresAtMillis == null || expiresAtMillis.longValue() <= now.toEpochMilli()) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(expiresAtMillis.longValue()));
    }

    public long countActive(UUID couponId, Instant now) {
        Long count = zSet().count(key(couponId), now.toEpochMilli() + 1, Double.POSITIVE_INFINITY);
        return count == null ? 0 : count;
    }

    public Optional<UUID> upsertUserReservation(UUID userId, UUID couponId, Instant expiresAt, Instant now,
            Duration ttl) {
        // ZADD into the new coupon's sorted set (+ expired-member cleanup and key TTL)
        String key = key(couponId);
        zSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now.toEpochMilli());
        zSet().add(key, userId.toString(), expiresAt.toEpochMilli());
        redisTemplate.expire(key, ttl);
        // SET user:reservation:{userId} <couponId> GET PX ttl → previous coupon
        String previousCouponId = redisTemplate.opsForValue().setGet(userKey(userId), couponId.toString(), ttl);
        // if the previous coupon is a different one → ZREM the user from it
        Optional<UUID> releasedCouponId = Optional.ofNullable(previousCouponId)
                .map(UUID::fromString)
                .filter(previous -> !previous.equals(couponId));
        releasedCouponId.ifPresent(previous -> remove(previous, userId));
        // returns the released coupon, if any
        return releasedCouponId;
    }

    public void remove(UUID couponId, UUID userId) {
        zSet().remove(key(couponId), userId.toString());
    }

    private ZSetOperations<String, String> zSet() {
        return redisTemplate.opsForZSet();
    }

    private static String key(UUID couponId) {
        return KEY_PREFIX + couponId;
    }

    private static String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }
}
