package vn.swt301.labflowdemo.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request shapes for equipment (S16, S17). */
public final class EquipmentDtos {

    private EquipmentDtos() {
    }

    public record EquipmentRequest(
            @NotNull Integer labId,
            @NotNull Integer typeId,
            @NotBlank @Size(max = 50) String serial,
            @NotBlank @Size(max = 100) String name,
            boolean requiresTraining,
            boolean requiresApproval) {
    }

    public record EquipmentStatusRequest(@NotNull EquipmentStatus status, @Size(max = 255) String reason) {
    }

    public record BulkStatusRequest(@NotEmpty List<Long> ids, @NotNull EquipmentStatus status,
                                    @Size(max = 255) String reason) {
    }
}
