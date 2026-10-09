package vn.swt301.labflowdemo.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * One {@link Clock} for the whole app. Services call {@code Instant.now(clock)}, never {@code Instant.now()},
 * so a unit test can pass {@code Clock.fixed(...)} and control "now" exactly (ADR-002).
 */
@Configuration
public class TimeConfig {

    @Bean
    public ZoneId businessZone(@Value("${app.zone}") String zone) {
        return ZoneId.of(zone);
    }

    @Bean
    public Clock clock(ZoneId businessZone) {
        return Clock.system(businessZone);
    }
}
