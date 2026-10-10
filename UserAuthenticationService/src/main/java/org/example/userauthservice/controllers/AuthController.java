package org.example.userauthservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.userauthservice.dtos.*;
import org.example.userauthservice.models.Session;
import org.example.userauthservice.models.User;
import org.example.userauthservice.services.IAuthService;
import org.example.userauthservice.utils.TokenUtil;
import org.example.userauthservice.utils.mappers.UserDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Optional;

@Tag(name = "Authentication", description = "Signup, login, token refresh, logout and password reset")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private IAuthService authService;

    @Operation(summary = "Register a new account with email and password")
    @PostMapping("/signup")
    public ResponseEntity<UserDto> signup(@Valid @RequestBody SignupRequestDto signupRequestDto){
        User user = authService.signup(signupRequestDto.getName(), signupRequestDto.getEmail(), signupRequestDto.getPassword());
        return new ResponseEntity<>(UserDtoMapper.from(user), HttpStatus.CREATED);
    }

    @Operation(summary = "Login with email and password", description = "Returns a JWT access token and a refresh token")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        Session session = authService.login(loginRequestDto.getEmail(), loginRequestDto.getPassword());
        return loginResponse(session);
    }

    @Operation(summary = "Login with a social account", description = "Accepts a Google ID token from Google Sign-In; creates the account on first login")
    @PostMapping("/social-login")
    public ResponseEntity<LoginResponseDto> socialLogin(@Valid @RequestBody SocialLoginRequestDto socialLoginRequestDto) {
        Session session = authService.socialLogin(socialLoginRequestDto.getProvider(), socialLoginRequestDto.getIdToken());
        return loginResponse(session);
    }

    @Operation(summary = "Get a new access token using a refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
        Session session = authService.refresh(refreshTokenRequestDto.getRefreshToken());
        return loginResponse(session);
    }

    @Operation(summary = "Logout and end the current session")
    @PostMapping("/logout")
    public ResponseEntity<MessageResponseDto> logout(
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        authService.logout(TokenUtil.extractBearerToken(authorizationHeader));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, ResponseCookie.from("token", "").path("/").maxAge(0).build().toString())
                .body(new MessageResponseDto("Logged out successfully"));
    }

    @Operation(summary = "Validate a token", description = "Used by other services; returns the token owner when the token is valid, 401 otherwise")
    @PostMapping("/validateToken")
    public ResponseEntity<ValidateTokenResponseDto> validateToken(@Valid @RequestBody ValidateTokenDto validateTokenDto) {
        Optional<User> optionalUser = authService.validateToken(validateTokenDto.getToken());

        if(optionalUser.isPresent()) {
            return new ResponseEntity<>(UserDtoMapper.toValidateTokenResponse(optionalUser.get()), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @Operation(summary = "Request a password reset link by email")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponseDto> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto) {
        authService.forgotPassword(forgotPasswordRequestDto.getEmail());
        return new ResponseEntity<>(new MessageResponseDto(
                "If the email is registered, a password reset link has been sent"), HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Set a new password using the token from the reset link")
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponseDto> resetPassword(@Valid @RequestBody ResetPasswordRequestDto resetPasswordRequestDto) {
        authService.resetPassword(resetPasswordRequestDto.getToken(), resetPasswordRequestDto.getNewPassword());
        return ResponseEntity.ok(new MessageResponseDto("Password has been reset, please login again"));
    }

    private ResponseEntity<LoginResponseDto> loginResponse(Session session) {
        Long validity = authService.getAccessTokenValiditySeconds();
        ResponseCookie cookie = ResponseCookie.from("token", session.getToken())
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofSeconds(validity))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(UserDtoMapper.toLoginResponse(session, validity));
    }
}
