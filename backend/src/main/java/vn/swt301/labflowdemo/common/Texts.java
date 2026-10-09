package vn.swt301.labflowdemo.common;

/** Small string helpers shared by services (no Spring, easy to unit test). */
public final class Texts {

    private Texts() {
    }

    /** @return the trimmed text, or {@code null} when the input is null or only spaces. */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * @return the trimmed text, or "" when null/blank. Used for optional search keywords: a NULL parameter inside
     * {@code lower(concat('%', :q, '%'))} makes PostgreSQL fail ("function lower(bytea) does not exist"),
     * while "" simply matches every row.
     */
    public static String orEmpty(String raw) {
        String t = clean(raw);
        return t == null ? "" : t;
    }

    /** @return true when the trimmed text has between min and max characters. */
    public static boolean lengthBetween(String raw, int min, int max) {
        String t = clean(raw);
        int len = t == null ? 0 : t.length();
        return len >= min && len <= max;
    }
}
