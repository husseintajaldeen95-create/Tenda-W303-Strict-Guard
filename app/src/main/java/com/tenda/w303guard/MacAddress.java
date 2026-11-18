package com.tenda.w303guard;

import java.util.Locale;
import java.util.regex.Pattern;

/** Small, strict MAC-address helper used by the device inventory screen. */
public final class MacAddress {
    private static final Pattern MAC = Pattern.compile("(?i)^[0-9a-f]{2}([:-])[0-9a-f]{2}\\1[0-9a-f]{2}\\1[0-9a-f]{2}\\1[0-9a-f]{2}\\1[0-9a-f]{2}$");

    private MacAddress() { }

    public static String normalize(String value) {
        if (value == null) return "";
        String candidate = value.trim().replace(' ', ':');
        if (!MAC.matcher(candidate).matches()) return "";
        return candidate.replace('-', ':').toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        return !normalize(value).isEmpty();
    }
}
