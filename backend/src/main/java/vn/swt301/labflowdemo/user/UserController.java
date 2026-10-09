package vn.swt301.labflowdemo.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.swt301.labflowdemo.common.ApiResponse;
import vn.swt301.labflowdemo.common.PageResponse;
import vn.swt301.labflowdemo.security.CurrentUser;
import vn.swt301.labflowdemo.user.UserDtos.ChangeStatusRequest;
import vn.swt301.labflowdemo.user.UserDtos.CreateUserRequest;
import vn.swt301.labflowdemo.user.UserDtos.ProfileRequest;
import vn.swt301.labflowdemo.user.UserDtos.UpdateUserRequest;
import vn.swt301.labflowdemo.user.UserDtos.UserView;

/** S10/S11 (/api/v1/users, ADMIN only) and S06 (/api/v1/me, any logged-in user). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserView>> search(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) RoleCode role,
                                                      @RequestParam(required = false) UserStatus status,
                                                      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(userService.searchUsers(q, role, status, pageable));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserView> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.getUser(id));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.createUser(body, CurrentUser.id(jwt)));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserView> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest body,
                                        @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.updateUser(id, body, CurrentUser.id(jwt)));
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserView> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest body,
                                              @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.changeStatus(id, body.status(), CurrentUser.id(jwt)));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.getProfile(CurrentUser.id(jwt)));
    }

    @PutMapping("/me")
    public ApiResponse<UserView> updateMe(@Valid @RequestBody ProfileRequest body, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.updateProfile(CurrentUser.id(jwt), body));
    }
}
