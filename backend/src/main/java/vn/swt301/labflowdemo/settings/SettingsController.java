package vn.swt301.labflowdemo.settings;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.swt301.labflowdemo.common.ApiResponse;
import vn.swt301.labflowdemo.security.CurrentUser;

import java.util.List;

/** S19 Settings / booking policy. */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<List<AppSetting>> list() {
        return ApiResponse.ok(settingsService.listSettings());
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AppSetting> update(@PathVariable String key, @RequestBody UpdateSettingRequest body,
                                          @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(settingsService.updateSetting(key, body.value(), CurrentUser.id(jwt)));
    }

    public record UpdateSettingRequest(@NotNull String value) {
    }
}
