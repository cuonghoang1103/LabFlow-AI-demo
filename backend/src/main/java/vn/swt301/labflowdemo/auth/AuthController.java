package vn.swt301.labflowdemo.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.swt301.labflowdemo.auth.AuthDtos.ChangePasswordRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.ForgotPasswordRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.LoginRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.MessageResponse;
import vn.swt301.labflowdemo.auth.AuthDtos.RegisterRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.RegisterResponse;
import vn.swt301.labflowdemo.auth.AuthDtos.ResetPasswordRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.TokenRequest;
import vn.swt301.labflowdemo.auth.AuthDtos.TokenResponse;
import vn.swt301.labflowdemo.common.ApiResponse;
import vn.swt301.labflowdemo.security.CurrentUser;

/** /api/v1/auth/** - controller only translates HTTP to a service call; rules live in AuthService. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest body) {
        return ApiResponse.ok(authService.register(body));
    }

    @PostMapping("/verify-email")
    public ApiResponse<MessageResponse> verifyEmail(@Valid @RequestBody TokenRequest body) {
        return ApiResponse.ok(authService.verifyEmail(body.token()));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest body) {
        return ApiResponse.ok(authService.login(body));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody TokenRequest body) {
        return ApiResponse.ok(authService.refresh(body.token()));
    }

    @PostMapping("/logout")
    public ApiResponse<MessageResponse> logout(@Valid @RequestBody TokenRequest body) {
        return ApiResponse.ok(authService.logout(body.token()));
    }

    @PostMapping("/change-password")
    public ApiResponse<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(authService.changePassword(CurrentUser.id(jwt), body));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        return ApiResponse.ok(authService.forgotPassword(body.email()));
    }

    @PostMapping("/reset-password")
    public ApiResponse<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        return ApiResponse.ok(authService.resetPassword(body));
    }
}
