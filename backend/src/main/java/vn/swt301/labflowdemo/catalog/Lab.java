package vn.swt301.labflowdemo.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A bookable laboratory room. requiresApproval follows BR-06 (more seats than booking.approval-capacity). */
@Entity
@Table(name = "labs")
@Getter
@Setter
@NoArgsConstructor
public class Lab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer buildingId;
    private String code;
    private String name;
    private int floor;
    private int capacity;

    @Enumerated(EnumType.STRING)
    private ActiveStatus status;
    private boolean requiresApproval;

    /** Optimistic lock: two admins editing the same lab - the second save fails instead of overwriting. */
    @Version
    private int version;
}
