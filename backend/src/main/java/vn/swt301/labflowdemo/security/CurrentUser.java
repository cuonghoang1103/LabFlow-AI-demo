package vn.swt301.labflowdemo.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the logged-in user id from the access token (subject = user id). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
