package vn.swt301.labflowdemo.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Day 3 skeleton: GET /api/v1/ping answers without login - quick "is it running?" check. */
@RestController
public class PingController {

    @GetMapping("/api/v1/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("app", "labflow-demo", "status", "UP"));
    }
}
