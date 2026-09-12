package com.kitchensys.auth.controller;

import com.kitchensys.auth.dto.AuthDto;
import com.kitchensys.auth.security.EmployeePrincipal;
import com.kitchensys.auth.service.AuthService;
import com.kitchensys.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

/** 06-api-design.md Auth API — 인증 관련 엔드포인트 전체. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(30);

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDto.RegisterResponse>> register(@RequestBody @Valid AuthDto.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(authService.register(request)));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestBody @Valid AuthDto.VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDto.LoginResponse>> login(
        @RequestBody @Valid AuthDto.LoginRequest request, HttpServletResponse response
    ) {
        AuthService.LoginResult result = authService.login(request);
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.of(result.body()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthDto.RefreshResponse>> refresh(
        @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken, HttpServletResponse response
    ) {
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.of(result.body()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken, HttpServletResponse response
    ) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@RequestBody @Valid AuthDto.PasswordResetRequestRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@RequestBody @Valid AuthDto.PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthDto.MeResponse>> me(@AuthenticationPrincipal EmployeePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(authService.me(UUID.fromString(principal.employeeId()))));
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
            .httpOnly(true).secure(true).sameSite("Strict").path("/api/auth").maxAge(REFRESH_COOKIE_MAX_AGE).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true).secure(true).sameSite("Strict").path("/api/auth").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
