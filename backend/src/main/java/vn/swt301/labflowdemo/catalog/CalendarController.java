package vn.swt301.labflowdemo.catalog;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.swt301.labflowdemo.catalog.CalendarDtos.BlackoutRequest;
import vn.swt301.labflowdemo.catalog.CalendarDtos.OperatingHoursRequest;
import vn.swt301.labflowdemo.common.ApiResponse;
import vn.swt301.labflowdemo.security.CurrentUser;

import java.util.List;

/** Operating calendar: S18 opening hours + blackouts (owner C2). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CalendarController {

    private static final String MANAGE = "hasAnyRole('MANAGER','ADMIN')";

    private final CalendarService calendarService;

    // ── operating calendar ──
    @GetMapping("/labs/{id}/hours")
    public ApiResponse<List<OperatingHours>> hours(@PathVariable Integer id) {
        return ApiResponse.ok(calendarService.hoursFor(id));
    }

    @PutMapping("/operating-hours")
    @PreAuthorize(MANAGE)
    public ApiResponse<OperatingHours> setHours(@Valid @RequestBody OperatingHoursRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(calendarService.setOperatingHours(body, CurrentUser.id(jwt)));
    }

    @GetMapping("/labs/{id}/blackouts")
    public ApiResponse<List<Blackout>> blackouts(@PathVariable Integer id) {
        return ApiResponse.ok(calendarService.upcomingBlackouts(id));
    }

    @PostMapping("/blackouts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ApiResponse<Blackout> addBlackout(@Valid @RequestBody BlackoutRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(calendarService.addBlackout(body, CurrentUser.id(jwt)));
    }
}
