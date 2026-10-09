package vn.swt301.labflowdemo.common;

import org.springframework.http.HttpStatus;

/**
 * Every business error of the system: a stable code, an HTTP status and a default message.
 * <p>
 * Quy ước: mã lỗi = TIỀN_TỐ_MODULE + tên lỗi. Message ở đây chính là dòng "Log message"
 * trong sheet 5.1 (GlobalExceptionHandler ghi log {@code WARN [CODE] message} và trả về client).
 */
public enum ErrorCode {
    // common
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid input"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to do this"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "The data was changed by someone else, reload and try again"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again later"),

    // auth (S01, S03, S04, S05)
    AUTH_EMAIL_INVALID(HttpStatus.BAD_REQUEST, "Email format is invalid"),
    AUTH_EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Email domain is not allowed to register"),
    AUTH_EMAIL_TAKEN(HttpStatus.CONFLICT, "Email is already registered"),
    AUTH_PASSWORD_WEAK(HttpStatus.BAD_REQUEST, "Password must be 8-64 characters and contain letters and digits"),
    AUTH_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "New password must be different from the current password"),
    AUTH_FULL_NAME_INVALID(HttpStatus.BAD_REQUEST, "Full name must be 1-100 characters"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    AUTH_WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "Current password is incorrect"),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is locked"),
    AUTH_ACCOUNT_TEMP_LOCKED(HttpStatus.LOCKED, "Too many failed logins, account is locked for a while"),
    AUTH_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email is not verified yet"),
    AUTH_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Link or token is invalid"),
    AUTH_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "Link or token has expired"),
    AUTH_TOKEN_USED(HttpStatus.BAD_REQUEST, "Link or token has already been used"),

    // user admin + profile (S06, S10, S11)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    USER_ROLES_REQUIRED(HttpStatus.BAD_REQUEST, "A user needs at least one role"),
    USER_CANNOT_CHANGE_SELF(HttpStatus.BAD_REQUEST, "You cannot lock yourself or remove your own ADMIN role"),
    USER_LAST_ADMIN(HttpStatus.CONFLICT, "The system must keep at least one active admin"),
    USER_STATUS_UNCHANGED(HttpStatus.CONFLICT, "User already has this status"),
    USER_PHONE_INVALID(HttpStatus.BAD_REQUEST, "Phone must be a Vietnamese number: 10 digits starting with 0"),
    USER_AVATAR_URL_INVALID(HttpStatus.BAD_REQUEST, "Avatar must be an http(s) URL of at most 500 characters"),

    // catalog: building, lab (S13, S14)
    BUILDING_NOT_FOUND(HttpStatus.NOT_FOUND, "Building not found"),
    BUILDING_CODE_INVALID(HttpStatus.BAD_REQUEST, "Code must be 2-20 characters: A-Z, 0-9 or '-'"),
    BUILDING_CODE_TAKEN(HttpStatus.CONFLICT, "Building code already exists"),
    BUILDING_CLOSED(HttpStatus.CONFLICT, "Building is closed"),
    LAB_NOT_FOUND(HttpStatus.NOT_FOUND, "Lab not found"),
    LAB_CODE_INVALID(HttpStatus.BAD_REQUEST, "Code must be 2-20 characters: A-Z, 0-9 or '-'"),
    LAB_CODE_TAKEN(HttpStatus.CONFLICT, "Lab code already exists"),
    LAB_NAME_INVALID(HttpStatus.BAD_REQUEST, "Name must be 1-100 characters"),
    LAB_CAPACITY_INVALID(HttpStatus.BAD_REQUEST, "Capacity must be between 1 and 200"),
    LAB_FLOOR_INVALID(HttpStatus.BAD_REQUEST, "Floor must be between 0 and 50"),
    LAB_INACTIVE(HttpStatus.CONFLICT, "Lab is not active"),
    LAB_STATUS_UNCHANGED(HttpStatus.CONFLICT, "Lab already has this status"),

    // equipment (S16, S17)
    EQUIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Equipment not found"),
    EQUIPMENT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "Equipment type not found"),
    EQUIPMENT_SERIAL_INVALID(HttpStatus.BAD_REQUEST, "Serial must be 3-50 characters: A-Z, 0-9, '-' or '_'"),
    EQUIPMENT_SERIAL_TAKEN(HttpStatus.CONFLICT, "Serial already exists"),
    EQUIPMENT_NAME_INVALID(HttpStatus.BAD_REQUEST, "Name must be 1-100 characters"),
    EQUIPMENT_INVALID_TRANSITION(HttpStatus.CONFLICT, "This status change is not allowed"),
    EQUIPMENT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "A reason (max 255 characters) is required for this status"),
    EQUIPMENT_BULK_INVALID(HttpStatus.BAD_REQUEST, "Select 1-50 different equipment items"),
    EQUIPMENT_RETIRED(HttpStatus.CONFLICT, "Retired equipment cannot be changed"),

    // operating calendar (S18)
    CALENDAR_HOURS_INVALID(HttpStatus.BAD_REQUEST, "Opening time must be before closing time, both on a 30-minute mark"),
    CALENDAR_DAY_INVALID(HttpStatus.BAD_REQUEST, "Day of week must be 1 (Monday) to 7 (Sunday)"),
    BLACKOUT_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Blackout must end after it starts and end in the future"),
    BLACKOUT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "A reason (max 255 characters) is required"),
    BLACKOUT_OVERLAP(HttpStatus.CONFLICT, "Another blackout already covers part of this period"),

    // settings (S19)
    SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "Unknown setting"),
    SETTING_VALUE_INVALID(HttpStatus.BAD_REQUEST, "Value is not valid for this setting"),

    // availability + reservation (S20, spike for S22)
    TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Start must be before end"),
    TIME_SLOT_MISALIGNED(HttpStatus.BAD_REQUEST, "Start and end must be on the booking slot grid"),
    TIME_TOO_FAR_AHEAD(HttpStatus.BAD_REQUEST, "Booking is too far in the future"),
    TIME_IN_PAST(HttpStatus.BAD_REQUEST, "Booking must start in the future"),
    TIME_OUTSIDE_OPENING_HOURS(HttpStatus.BAD_REQUEST, "The lab is closed during this period"),
    TIME_BLACKOUT(HttpStatus.CONFLICT, "The lab is unavailable (blackout) during this period"),
    RES_PURPOSE_REQUIRED(HttpStatus.BAD_REQUEST, "Purpose is required (max 255 characters)"),
    RES_IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Header Idempotency-Key (8-64 characters) is required"),
    RES_CONFLICT(HttpStatus.CONFLICT, "This slot is already booked");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
