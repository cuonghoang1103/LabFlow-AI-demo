package vn.swt301.labflowdemo.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.jwt.* from application.yml. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, int accessMinutes, int refreshDays) {
}
