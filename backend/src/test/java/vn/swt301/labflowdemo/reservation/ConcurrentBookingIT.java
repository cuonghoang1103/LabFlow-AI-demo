package vn.swt301.labflowdemo.reservation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.swt301.labflowdemo.catalog.LabRepository;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.reservation.ReservationDtos.CreateReservationRequest;
import vn.swt301.labflowdemo.user.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G0 transaction spike (E1 day 11): N requests race for the SAME lab slot on REAL PostgreSQL.
 * Expected: exactly ONE succeeds, all others get RES_CONFLICT. Runs with `mvn verify` (needs Docker);
 * skipped automatically when Docker is not available.
 * <p>
 * Vì sao không chạy trên H2: H2 không có exclusion constraint - test đồng thời trên H2 là test vô nghĩa.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ConcurrentBookingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ReservationService reservationService;
    @Autowired UserRepository userRepository;
    @Autowired LabRepository labRepository;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest(name = "{0} concurrent requests -> exactly one CONFIRMED")
    @ValueSource(ints = {2, 10, 50})
    void onlyOneRequestWinsTheSlot(int requests) throws Exception {
        jdbc.update("DELETE FROM reservation_items");
        jdbc.update("DELETE FROM reservations");
        Long userId = userRepository.findByEmailIgnoreCase("student1@fpt.edu.vn").orElseThrow().getId();
        Integer labId = labRepository.findByCodeIgnoreCase("AL-301").orElseThrow().getId();
        // next Tuesday 09:00-11:00 Vietnam time: always open, always within 14 days
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate tuesday = LocalDate.now(vn).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        var request = new CreateReservationRequest(labId,
                tuesday.atTime(LocalTime.of(9, 0)).atZone(vn).toInstant(),
                tuesday.atTime(LocalTime.of(11, 0)).atZone(vn).toInstant(), "Spike test");

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            Callable<String> call = () -> {
                start.await();   // every thread waits here, then all fire together
                try {
                    return reservationService.createReservation(userId, request, UUID.randomUUID().toString()).status();
                } catch (BusinessException e) {
                    return e.getCode().name();
                }
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).as("outcomes %s", outcomes).filteredOn("CONFIRMED"::equals).hasSize(1);
        assertThat(outcomes).filteredOn(ErrorCode.RES_CONFLICT.name()::equals).hasSize(requests - 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_items WHERE active", Integer.class)).isEqualTo(1);
    }
}
