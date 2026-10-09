package vn.swt301.labflowdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.swt301.labflowdemo.settings.SettingsService;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke test: the Spring context starts on H2 and Flyway V1 ran (BR settings are there). */
@SpringBootTest
@ActiveProfiles("test")
class LabflowDemoApplicationTests {

    @Autowired
    SettingsService settingsService;

    @Test
    void contextLoadsAndSettingsExist() {
        assertThat(settingsService.getInt("booking.slot-minutes")).isEqualTo(30);
    }
}
