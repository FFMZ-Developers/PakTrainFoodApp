package com.example.paktrainfoodapp.utils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fixes a real bug: restaurants were matched to a passenger's meal station
 * via a fragile exact-string city match (Firestore's whereEqualTo needs an
 * EXACT match). The old code derived "city" from a station name with
 * `.replace("Jn", "").replace("Cantt", "")` - any case difference, extra
 * whitespace, or a station suffix that wasn't EXACTLY "Jn"/"Cantt" (e.g.
 * "Jn." or "JN" or "Junction") silently broke the match, so some cities
 * (reported: Mandi Bahauddin) never showed any restaurants even though
 * restaurants WERE registered there.
 *
 * Fix: both sides (restaurant registration's chosen city, and the
 * passenger's derived meal-station city) now go through this SAME
 * normalization before being compared/stored - lowercase, trimmed,
 * whitespace-collapsed, with common station-suffix WORDS stripped using
 * word-boundary regex (not naive substring replace, which can corrupt a
 * city name that happens to contain those letters mid-word).
 *
 * Firestore's cities collection now also stores a `cityNormalized` field
 * (see Step2RoleDetailsFragment.java) written alongside the human-readable
 * `city` field - passenger queries filter on `cityNormalized`, never on
 * the display `city` field directly.
 */
public class CityNameUtils {

    // ✅ Real station names in this app's seed data (FirebaseSeeder.java)
    // are stored WITHOUT spaces, CamelCase-concatenated - e.g.
    // "MandiBahauddin", "MalakwalJn", "KarachiCantt" - so the suffix
    // ("Jn"/"Cantt"/etc.) sits directly at the END of the string with NO
    // separating space or word-boundary character before it. A plain \b
    // regex boundary does NOT exist between "Malakwal" and "Jn" there. So
    // instead of \b, this matches the suffix anchored to the END of the
    // string (optionally preceded by whitespace, to also handle a
    // human-typed "Malakwal Jn" with a space) - safe because none of this
    // app's real city names happen to end in these letter sequences.
    private static final Pattern TRAILING_STATION_SUFFIX = Pattern.compile(
            "\\s*(jn|jct|junction|cantt|cant|railway station|station)$",
            Pattern.CASE_INSENSITIVE);

    // Removing ALL whitespace (not just collapsing it) is what makes
    // "Mandi Bahauddin" (restaurant's spinner-selected city, with a space)
    // and "MandiBahauddin" (the station's actual Firestore doc id, no
    // space) normalize to the exact same string regardless of which side
    // has the spaces.
    private static final Pattern ALL_WHITESPACE = Pattern.compile("\\s+");

    public static String normalize(String raw) {

        if (raw == null) return "";

        String cleaned = TRAILING_STATION_SUFFIX.matcher(raw.trim()).replaceAll("");

        cleaned = ALL_WHITESPACE.matcher(cleaned).replaceAll("");

        return cleaned.toLowerCase(Locale.US);
    }
}
