package vn.swt301.labflowdemo.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One physical unit (ResourceUnit) with a unique serial. Never hard-deleted: RETIRED instead (plan ERD). */
@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer labId;
    private Integer typeId;
    private String serial;
    private String name;

    @Enumerated(EnumType.STRING)
    private EquipmentStatus status;
    private boolean requiresTraining;
    private boolean requiresApproval;
    private String statusReason;

    @Version
    private int version;
}
