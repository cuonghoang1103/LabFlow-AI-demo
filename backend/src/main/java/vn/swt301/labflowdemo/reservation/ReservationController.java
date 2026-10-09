package vn.swt301.labflowdemo.reservation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.swt301.labflowdemo.common.ApiResponse;
import vn.swt301.labflowdemo.reservation.ReservationDtos.CreateReservationRequest;
import vn.swt301.labflowdemo.reservation.ReservationDtos.ReservationView;
import vn.swt301.labflowdemo.security.CurrentUser;

import java.util.List;

/** POST /api/v1/reservations needs header Idempotency-Key (plan API contract). */
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationView>> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                               @Valid @RequestBody CreateReservationRequest body,
                                                               @AuthenticationPrincipal Jwt jwt) {
        ReservationView view = reservationService.createReservation(CurrentUser.id(jwt), body, key);
        return ResponseEntity.status(view.replayed() ? 200 : 201).body(ApiResponse.ok(view));
    }

    @GetMapping("/mine")
    public ApiResponse<List<ReservationView>> mine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(reservationService.myReservations(CurrentUser.id(jwt)));
    }
}
