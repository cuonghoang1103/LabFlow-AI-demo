package vn.swt301.labflowdemo.availability;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.swt301.labflowdemo.catalog.Equipment;
import vn.swt301.labflowdemo.catalog.Lab;
import vn.swt301.labflowdemo.common.ApiResponse;

import java.time.Instant;
import java.util.List;

/** GET /api/v1/availability?from=&to=&capacity=&buildingId= (plan API contract). Times are ISO-8601 instants. */
@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    public ApiResponse<List<Lab>> labs(@RequestParam Instant from, @RequestParam Instant to,
                                       @RequestParam(required = false) Integer capacity,
                                       @RequestParam(required = false) Integer buildingId) {
        return ApiResponse.ok(availabilityService.findAvailableLabs(from, to, capacity, buildingId));
    }

    @GetMapping("/equipment")
    public ApiResponse<List<Equipment>> equipment(@RequestParam Instant from, @RequestParam Instant to,
                                                  @RequestParam Integer typeId) {
        return ApiResponse.ok(availabilityService.findAvailableEquipment(from, to, typeId));
    }
}
