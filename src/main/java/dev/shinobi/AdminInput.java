package dev.shinobi;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.regex.Pattern;

/** Pure validation of admin command arguments. Nothing from a command line is trusted. */
public final class AdminInput {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private AdminInput() {}

    /** Normalised lower-case id, or null if it is not a plain identifier (blocks paths, spaces, unicode tricks). */
    public static String id(String s) {
        if (s == null) return null;
        String t = s.toLowerCase(Locale.ROOT); // no trimming: command tokens never contain whitespace
        return ID.matcher(t).matches() ? t : null;
    }

    public static boolean validName(String s) {
        return s != null && NAME.matcher(s).matches();
    }

    public static UUID uuid(String s) {
        if (s == null || s.length() != 36) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Parses a finite number within [min, max]. Rejects NaN, infinities, hex/exponent tricks and out-of-range values. */
    public static OptionalDouble number(String s, double min, double max) {
        if (s == null || s.length() > 18 || !s.matches("-?[0-9]+(\\.[0-9]+)?")) return OptionalDouble.empty();
        double d;
        try {
            d = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
        if (Double.isNaN(d) || Double.isInfinite(d) || d < min || d > max) return OptionalDouble.empty();
        return OptionalDouble.of(d);
    }
}
