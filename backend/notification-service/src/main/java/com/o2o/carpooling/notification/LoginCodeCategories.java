package com.o2o.carpooling.notification;

import java.util.List;
import java.util.Set;

/**
 * The set of notification categories that carry login verification codes. These must NEVER be
 * stored as, nor surfaced through, normal user notifications — the demo login code lives only in
 * auth-service's challenge-bound store. This single source of truth is used by three guards that
 * must never drift apart:
 * <ul>
 *   <li>write rejection: {@link NotificationService#notify} refuses these categories;</li>
 *   <li>read exclusion: every inbox / unread / operator-console query filters them out;</li>
 *   <li>cleanup: the purge migration removes any historical rows in these categories.</li>
 * </ul>
 *
 * <p>Includes historical / equivalent spellings so a rename in an older component can never leak a
 * code into the Message Center.
 */
final class LoginCodeCategories {

    /** Canonical + historical login-code category names. */
    static final Set<String> ALL = Set.of(
        "AUTH_SMS_CODE",
        "SMS_CODE",
        "LOGIN_CODE",
        "VERIFICATION_CODE"
    );

    /** Same set as a list, for SQL {@code IN (:params)} binding. */
    static final List<String> AS_LIST = List.copyOf(ALL);

    private LoginCodeCategories() {
    }

    static boolean isLoginCode(String category) {
        return category != null && ALL.contains(category);
    }
}
