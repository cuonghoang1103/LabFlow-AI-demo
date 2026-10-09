package vn.swt301.labflowdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.swt301.labflowdemo.catalog.LabRepository;
import vn.swt301.labflowdemo.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke test: the whole Spring context starts on H2 and Flyway V1..V7 ran (seed data is there). */
@SpringBootTest
@ActiveProfiles("test")
class LabflowDemoApplicationTests {

    @Autowired
    UserRepository userRepository;

    @Autowired
    LabRepository labRepository;

    @Test
    void contextLoadsAndSeedDataExists() {
        assertThat(userRepository.findByEmailIgnoreCase("admin@fpt.edu.vn")).isPresent();
        assertThat(labRepository.findByCodeIgnoreCase("AL-301")).isPresent();
    }
}
