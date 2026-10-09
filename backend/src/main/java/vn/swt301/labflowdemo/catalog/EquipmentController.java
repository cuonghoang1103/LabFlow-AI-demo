package vn.swt301.labflowdemo.catalog;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.swt301.labflowdemo.catalog.EquipmentDtos.BulkStatusRequest;
import vn.swt301.labflowdemo.catalog.EquipmentDtos.EquipmentRequest;
import vn.swt301.labflowdemo.catalog.EquipmentDtos.EquipmentStatusRequest;
import vn.swt301.labflowdemo.common.ApiResponse;
import vn.swt301.labflowdemo.common.PageResponse;
import vn.swt301.labflowdemo.security.CurrentUser;

import java.util.List;
import java.util.Map;

/** Equipment: S16 list + bulk status, S17 detail (owner C4). STAFF may change status, MANAGER/ADMIN manage. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EquipmentController {

    private static final String MANAGE = "hasAnyRole('MANAGER','ADMIN')";

    private final EquipmentService equipmentService;

    // ── equipment ──
    @GetMapping("/equipment-types")
    public ApiResponse<List<EquipmentType>> types() {
        return ApiResponse.ok(equipmentService.listTypes());
    }

    @GetMapping("/equipment")
    public ApiResponse<PageResponse<Equipment>> equipment(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) Integer labId,
                                                          @RequestParam(required = false) Integer typeId,
                                                          @RequestParam(required = false) EquipmentStatus status,
                                                          @PageableDefault(size = 20, sort = "serial") Pageable pageable) {
        return ApiResponse.ok(equipmentService.searchEquipment(q, labId, typeId, status, pageable));
    }

    @GetMapping("/equipment/{id}")
    public ApiResponse<Equipment> equipmentById(@PathVariable Long id) {
        return ApiResponse.ok(equipmentService.getEquipment(id));
    }

    @PostMapping("/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ApiResponse<Equipment> createEquipment(@Valid @RequestBody EquipmentRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(equipmentService.createEquipment(body, CurrentUser.id(jwt)));
    }

    @PutMapping("/equipment/{id}")
    @PreAuthorize(MANAGE)
    public ApiResponse<Equipment> updateEquipment(@PathVariable Long id, @Valid @RequestBody EquipmentRequest body,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(equipmentService.updateEquipment(id, body, CurrentUser.id(jwt)));
    }

    @PatchMapping("/equipment/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ApiResponse<Equipment> equipmentStatus(@PathVariable Long id, @Valid @RequestBody EquipmentStatusRequest body,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(equipmentService.changeStatus(id, body.status(), body.reason(), CurrentUser.id(jwt)));
    }

    @PatchMapping("/equipment/status")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")
    public ApiResponse<Map<String, Integer>> bulkStatus(@Valid @RequestBody BulkStatusRequest body,
                                                        @AuthenticationPrincipal Jwt jwt) {
        int changed = equipmentService.bulkChangeStatus(body.ids(), body.status(), body.reason(), CurrentUser.id(jwt));
        return ApiResponse.ok(Map.of("changed", changed));
    }
}
