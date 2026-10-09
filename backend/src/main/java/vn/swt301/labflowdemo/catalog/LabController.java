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
import vn.swt301.labflowdemo.catalog.LabDtos.BuildingRequest;
import vn.swt301.labflowdemo.catalog.LabDtos.LabRequest;
import vn.swt301.labflowdemo.catalog.LabDtos.LabStatusRequest;
import vn.swt301.labflowdemo.common.ApiResponse;
import vn.swt301.labflowdemo.common.PageResponse;
import vn.swt301.labflowdemo.security.CurrentUser;

import java.util.List;

/** Buildings + labs: S13 list, S14 detail (owner C2). Read = any logged-in user, write = MANAGER/ADMIN. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LabController {

    private static final String MANAGE = "hasAnyRole('MANAGER','ADMIN')";

    private final LabService labService;

    // ── buildings + labs ──
    @GetMapping("/buildings")
    public ApiResponse<List<Building>> buildings() {
        return ApiResponse.ok(labService.listBuildings());
    }

    @PostMapping("/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ApiResponse<Building> createBuilding(@Valid @RequestBody BuildingRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(labService.createBuilding(body, CurrentUser.id(jwt)));
    }

    @GetMapping("/labs")
    public ApiResponse<PageResponse<Lab>> labs(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Integer buildingId,
                                               @RequestParam(required = false) ActiveStatus status,
                                               @RequestParam(required = false) Integer minCapacity,
                                               @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ApiResponse.ok(labService.searchLabs(q, buildingId, status, minCapacity, pageable));
    }

    @GetMapping("/labs/{id}")
    public ApiResponse<Lab> lab(@PathVariable Integer id) {
        return ApiResponse.ok(labService.getLab(id));
    }

    @PostMapping("/labs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ApiResponse<Lab> createLab(@Valid @RequestBody LabRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(labService.createLab(body, CurrentUser.id(jwt)));
    }

    @PutMapping("/labs/{id}")
    @PreAuthorize(MANAGE)
    public ApiResponse<Lab> updateLab(@PathVariable Integer id, @Valid @RequestBody LabRequest body,
                                      @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(labService.updateLab(id, body, CurrentUser.id(jwt)));
    }

    @PatchMapping("/labs/{id}/status")
    @PreAuthorize(MANAGE)
    public ApiResponse<Lab> labStatus(@PathVariable Integer id, @Valid @RequestBody LabStatusRequest body,
                                      @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(labService.changeLabStatus(id, body.status(), CurrentUser.id(jwt)));
    }
}
