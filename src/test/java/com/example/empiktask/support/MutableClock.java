package com.example.empiktask.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public class MutableClock extends Clock {

    private static final Instant INITIAL_INSTANT = Instant.parse("2026-01-15T10:00:00Z");

    private final AtomicReference<Instant> current = new AtomicReference<>(INITIAL_INSTANT);

    public void advance(Duration duration) {
        current.updateAndGet(instant -> instant.plus(duration));
    }

    public void reset() {
        current.set(INITIAL_INSTANT);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
