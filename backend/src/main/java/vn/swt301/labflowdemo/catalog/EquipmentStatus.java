package vn.swt301.labflowdemo.catalog;

/** BR-11: only AVAILABLE equipment can be booked or borrowed. ON_LOAN is set by the loan module only. */
public enum EquipmentStatus {
    AVAILABLE, ON_LOAN, MAINTENANCE, RETIRED
}
