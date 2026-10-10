package org.example.userauthservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.security.Keys;
import org.example.userauthservice.clients.GoogleAuthClient;
import org.example.userauthservice.clients.KafkaProducerClient;
import org.example.userauthservice.exceptions.IncorrectPasswordException;
import org.example.userauthservice.exceptions.InvalidTokenException;
import org.example.userauthservice.exceptions.UserAlreadyExistException;
import org.example.userauthservice.models.PasswordResetToken;
import org.example.userauthservice.models.Role;
import org.example.userauthservice.models.Session;
import org.example.userauthservice.models.State;
import org.example.userauthservice.models.User;
import org.example.userauthservice.repos.PasswordResetTokenRepo;
import org.example.userauthservice.repos.RoleRepo;
import org.example.userauthservice.repos.SessionRepo;
import org.example.userauthservice.repos.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepo userRepo;

    @Mock
    private RoleRepo roleRepo;

    @Mock
    private SessionRepo sessionRepo;

    @Mock
    private PasswordResetTokenRepo passwordResetTokenRepo;

    @Mock
    private KafkaProducerClient kafkaProducerClient;

    @Mock
    private GoogleAuthClient googleAuthClient;

    @Spy
    private BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder(4);

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private SecretKey secretKey = Keys.hmacShaKeyFor(
            "unit-test-secret-key-that-is-long-enough-123".getBytes(StandardCharsets.UTF_8));

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "accessTokenValiditySeconds", 3600L);
        ReflectionTestUtils.setField(authService, "refreshTokenValiditySeconds", 86400L);
        ReflectionTestUtils.setField(authService, "passwordResetUrl", "http://localhost/reset");
        ReflectionTestUtils.setField(authService, "passwordResetValidityMinutes", 15L);
        ReflectionTestUtils.setField(authService, "logPasswordResetLink", false);
        ReflectionTestUtils.setField(authService, "mailFrom", "no-reply@test.local");
    }

    @Test
    void signupRejectsExistingEmail() {
        when(userRepo.findByEmail("a@b.com")).thenReturn(Optional.of(new User()));

        assertThrows(UserAlreadyExistException.class, () -> authService.signup("A", "a@b.com", "secret1"));
        verify(userRepo, never()).save(any());
    }

    @Test
    void signupHashesPasswordAssignsDefaultRoleAndPublishesWelcomeEmail() {
        when(userRepo.findByEmail("a@b.com")).thenReturn(Optional.empty());
        Role role = new Role();
        role.setValue(AuthService.DEFAULT_ROLE);
        when(roleRepo.findByValue(AuthService.DEFAULT_ROLE)).thenReturn(Optional.of(role));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = authService.signup("A", "a@b.com", "secret1");

        assertNotEquals("secret1", user.getPassword());
        assertTrue(bCryptPasswordEncoder.matches("secret1", user.getPassword()));
        assertEquals(1, user.getRoles().size());
        verify(kafkaProducerClient).sendMessage(eq(AuthService.SIGNUP_TOPIC), anyString());
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = userWithPassword("right-password");
        when(userRepo.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertThrows(IncorrectPasswordException.class, () -> authService.login("a@b.com", "wrong-password"));
    }

    @Test
    void loginIssuesTokensThatValidate() {
        User user = userWithPassword("right-password");
        when(userRepo.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(sessionRepo.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session session = authService.login("a@b.com", "right-password");

        assertNotNull(session.getToken());
        assertNotNull(session.getRefreshToken());
        assertTrue(session.getExpiresAt().after(new Date()));

        when(sessionRepo.findByTokenAndState(session.getToken(), State.ACTIVE)).thenReturn(Optional.of(session));
        Optional<User> optionalUser = authService.validateToken(session.getToken());
        assertTrue(optionalUser.isPresent());
        assertEquals(user.getId(), optionalUser.get().getId());
    }

    @Test
    void expiredTokenIsRejectedAndSessionDeactivated() {
        ReflectionTestUtils.setField(authService, "accessTokenValiditySeconds", -60L);
        User user = userWithPassword("right-password");
        when(userRepo.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(sessionRepo.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Session session = authService.login("a@b.com", "right-password");
        when(sessionRepo.findByTokenAndState(session.getToken(), State.ACTIVE)).thenReturn(Optional.of(session));

        Optional<User> optionalUser = authService.validateToken(session.getToken());

        assertTrue(optionalUser.isEmpty());
        assertEquals(State.INACTIVE, session.getState());
    }

    @Test
    void refreshRotatesTokens() {
        User user = userWithPassword("right-password");
        Session session = new Session();
        session.setUser(user);
        session.setToken("old-token");
        session.setRefreshToken("old-refresh");
        session.setRefreshTokenExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        when(sessionRepo.findByRefreshTokenAndState("old-refresh", State.ACTIVE)).thenReturn(Optional.of(session));
        when(sessionRepo.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Session refreshed = authService.refresh("old-refresh");

        assertNotEquals("old-token", refreshed.getToken());
        assertNotEquals("old-refresh", refreshed.getRefreshToken());
    }

    @Test
    void resetPasswordRejectsUsedToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("t1");
        token.setUsed(true);
        token.setExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        when(passwordResetTokenRepo.findByToken("t1")).thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> authService.resetPassword("t1", "new-password"));
    }

    @Test
    void resetPasswordUpdatesPasswordAndLogsOutSessions() {
        User user = userWithPassword("old-password");
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("t1");
        token.setUsed(false);
        token.setUser(user);
        token.setExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        Session activeSession = new Session();
        activeSession.setState(State.ACTIVE);
        when(passwordResetTokenRepo.findByToken("t1")).thenReturn(Optional.of(token));
        when(sessionRepo.findAllByUserAndState(user, State.ACTIVE)).thenReturn(List.of(activeSession));

        authService.resetPassword("t1", "new-password");

        assertTrue(bCryptPasswordEncoder.matches("new-password", user.getPassword()));
        assertTrue(token.getUsed());
        assertEquals(State.INACTIVE, activeSession.getState());
    }

    private User userWithPassword(String rawPassword) {
        User user = new User();
        user.setId(7L);
        user.setEmail("a@b.com");
        user.setName("A");
        user.setPassword(bCryptPasswordEncoder.encode(rawPassword));
        Role role = new Role();
        role.setValue(AuthService.DEFAULT_ROLE);
        user.getRoles().add(role);
        return user;
    }
}
