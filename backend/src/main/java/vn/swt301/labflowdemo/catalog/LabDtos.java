package vn.swt301.labflowdemo.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request shapes for buildings and labs (S13, S14). */
public final class LabDtos {

    private LabDtos() {
    }

    public record BuildingRequest(@NotBlank @Size(max = 20) String code, @NotBlank @Size(max = 100) String name) {
    }

    public record LabRequest(
            @NotNull Integer buildingId,
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull Integer floor,
            @NotNull Integer capacity,
            Integer version) {   // version: required on update (optimistic lock), ignored on create
    }

    public record LabStatusRequest(@NotNull ActiveStatus status) {
    }
}
