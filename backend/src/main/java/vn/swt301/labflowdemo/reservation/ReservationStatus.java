package vn.swt301.labflowdemo.reservation;

/** Reservation state machine (plan "Vòng đời các đối tượng"). */
public enum ReservationStatus {
    PENDING, CONFIRMED, REJECTED, CANCELLED, CHECKED_IN, NO_SHOW, COMPLETED
}
