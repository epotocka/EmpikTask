package com.example.empiktask.usage.service;

import com.example.empiktask.usage.model.Reservation;

public record ReservationResult(Reservation reservation, boolean created) {

    static ReservationResult created(Reservation reservation) {
        return new ReservationResult(reservation, true);
    }

    static ReservationResult existing(Reservation reservation) {
        return new ReservationResult(reservation, false);
    }
}
