package vn.swt301.labflowdemo.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resource type, e.g. OSC = oscilloscope. */
@Entity
@Table(name = "equipment_types")
@Getter
@Setter
@NoArgsConstructor
public class EquipmentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String code;
    private String name;
}
