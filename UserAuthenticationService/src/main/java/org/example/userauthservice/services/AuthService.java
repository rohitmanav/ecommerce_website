package org.example.userauthservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.example.userauthservice.clients.GoogleAuthClient;
import org.example.userauthservice.clients.KafkaProducerClient;
import org.example.userauthservice.dtos.EmailDto;
import org.example.userauthservice.dtos.GoogleTokenInfoDto;
import org.example.userauthservice.exceptions.IncorrectPasswordException;
import org.example.userauthservice.exceptions.InvalidTokenException;
import org.example.userauthservice.exceptions.UserAlreadyExistException;
import org.example.userauthservice.exceptions.UserNotRegisteredException;
import org.example.userauthservice.models.AuthProvider;
import org.example.userauthservice.models.PasswordResetToken;
import org.example.userauthservice.models.Role;
import org.example.userauthservice.models.Session;
import org.example.userauthservice.models.State;
import org.example.userauthservice.models.User;
import org.example.userauthservice.repos.PasswordResetTokenRepo;
import org.example.userauthservice.repos.RoleRepo;
import org.example.userauthservice.repos.SessionRepo;
import org.example.userauthservice.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.*;

@Slf4j
@Service
public class AuthService implements IAuthService {

    public static final String SIGNUP_TOPIC = "signup";
    public static final String PASSWORD_RESET_TOPIC = "password_reset";
    public static final String DEFAULT_ROLE = "NON_ADMIN";

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private SessionRepo sessionRepo;

    @Autowired
    private PasswordResetTokenRepo passwordResetTokenRepo;

    @Autowired
    private SecretKey secretKey;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private GoogleAuthClient googleAuthClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.access-token-validity-seconds}")
    private Long accessTokenValiditySeconds;

    @Value("${jwt.refresh-token-validity-seconds}")
    private Long refreshTokenValiditySeconds;

    @Value("${app.password-reset.url}")
    private String passwordResetUrl;

    @Value("${app.password-reset.token-validity-minutes}")
    private Long passwordResetValidityMinutes;

    @Value("${app.password-reset.log-link:false}")
    private Boolean logPasswordResetLink;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Override
    public User signup(String name, String email, String password) {
       Optional<User> optionalUser = userRepo.findByEmail(email);
       if (optionalUser.isPresent()) {
          throw new UserAlreadyExistException("Please try different email Id");
       }

       User user = new User();
       user.setEmail(email);
       user.setName(name);
       user.setPassword(bCryptPasswordEncoder.encode(password));
       user.setAuthProvider(AuthProvider.LOCAL);
       user.getRoles().add(getOrCreateRole(DEFAULT_ROLE));
       User savedUser = userRepo.save(user);

       sendWelcomeEmail(savedUser);
       return savedUser;
    }

    @Override
    public Session login(String email, String password) {
        Optional<User> optionalUser = userRepo.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new UserNotRegisteredException("Please register first");
        }

        User user = optionalUser.get();
        if (user.getPassword() == null || !bCryptPasswordEncoder.matches(password,user.getPassword())) {
            throw new IncorrectPasswordException("Incorrect password passed");
        }

        return createSession(user);
    }

    @Override
    public Session socialLogin(String provider, String idToken) {
        if (!"GOOGLE".equalsIgnoreCase(provider)) {
            throw new IllegalArgumentException("Unsupported social login provider: " + provider);
        }

        GoogleTokenInfoDto googleTokenInfoDto = googleAuthClient.verifyIdToken(idToken);

        Optional<User> optionalUser = userRepo.findByEmail(googleTokenInfoDto.getEmail());
        User user;
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
        } else {
            user = new User();
            user.setEmail(googleTokenInfoDto.getEmail());
            String name;
            if (googleTokenInfoDto.getName() != null) {
                name = googleTokenInfoDto.getName();
            } else {
                name = googleTokenInfoDto.getEmail();
            }
            user.setName(name);
            // Social accounts sign in through Google, so they get an unusable random password.
            user.setPassword(bCryptPasswordEncoder.encode(UUID.randomUUID().toString()));
            user.setAuthProvider(AuthProvider.GOOGLE);
            user.getRoles().add(getOrCreateRole(DEFAULT_ROLE));
            user = userRepo.save(user);
            sendWelcomeEmail(user);
        }

        return createSession(user);
    }

    @Override
    public Session refresh(String refreshToken) {
        Optional<Session> optionalSession = sessionRepo.findByRefreshTokenAndState(refreshToken, State.ACTIVE);
        if (optionalSession.isEmpty()) {
            throw new InvalidTokenException("Refresh token is invalid");
        }
        Session session = optionalSession.get();

        if (session.getRefreshTokenExpiresAt().before(new Date())) {
            session.setState(State.INACTIVE);
            sessionRepo.save(session);
            throw new InvalidTokenException("Refresh token has expired, please login again");
        }

        issueTokens(session, session.getUser());
        return sessionRepo.save(session);
    }

    @Override
    public void logout(String token) {
        Optional<Session> optionalSession = sessionRepo.findByTokenAndState(token, State.ACTIVE);
        if (optionalSession.isEmpty()) {
            throw new InvalidTokenException("Token is invalid or already logged out");
        }
        Session session = optionalSession.get();
        session.setState(State.INACTIVE);
        sessionRepo.save(session);
    }

    @Override
    public Optional<User> validateToken(String token) {

        Optional<Session> optionalSession = sessionRepo.findByTokenAndState(token, State.ACTIVE);

        if(optionalSession.isEmpty()) {
            return Optional.empty();
        }

        Session session = optionalSession.get();

        JwtParser jwtParser  = Jwts.parser().verifyWith(secretKey).build();
        try {
            jwtParser.parseSignedClaims(token);
        } catch (ExpiredJwtException exception) {
            log.debug("Token has expired for session {}", session.getId());
            session.setState(State.INACTIVE);
            sessionRepo.save(session);
            return Optional.empty();
        } catch (JwtException exception) {
            return Optional.empty();
        }

        return Optional.of(session.getUser());
    }

    @Override
    public void forgotPassword(String email) {
        Optional<User> optionalUser = userRepo.findByEmail(email);
        // Unknown emails return silently so the endpoint cannot be used to discover registered accounts.
        if (optionalUser.isEmpty()) {
            return;
        }

        User user = optionalUser.get();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setUsed(false);
        resetToken.setExpiresAt(new Date(System.currentTimeMillis() + passwordResetValidityMinutes * 60_000));
        passwordResetTokenRepo.save(resetToken);

        String link = passwordResetUrl + "?token=" + resetToken.getToken();
        if (Boolean.TRUE.equals(logPasswordResetLink)) {
            log.info("Password reset link for {}: {}", email, link);
        }

        EmailDto emailDto = new EmailDto();
        emailDto.setTo(email);
        emailDto.setFrom(mailFrom);
        emailDto.setSubject("Reset your password");
        emailDto.setBody("Hi " + user.getName() + ",\n\nUse the link below to reset your password. "
                + "It is valid for " + passwordResetValidityMinutes + " minutes.\n\n" + link
                + "\n\nIf you did not request this, you can ignore this email.");
        publishEmail(PASSWORD_RESET_TOPIC, emailDto);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> optionalPasswordResetToken = passwordResetTokenRepo.findByToken(token);
        if (optionalPasswordResetToken.isEmpty()) {
            throw new InvalidTokenException("Password reset link is invalid");
        }
        PasswordResetToken resetToken = optionalPasswordResetToken.get();

        if (Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.getExpiresAt().before(new Date())) {
            throw new InvalidTokenException("Password reset link has expired or was already used");
        }

        User user = resetToken.getUser();
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        userRepo.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepo.save(resetToken);

        logoutEverywhere(user);
    }

    public void logoutEverywhere(User user) {
        List<Session> sessions = sessionRepo.findAllByUserAndState(user, State.ACTIVE);
        for (Session session : sessions) {
            session.setState(State.INACTIVE);
        }
        sessionRepo.saveAll(sessions);
    }

    @Override
    public Long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    private Session createSession(User user) {
        Session session = new Session();
        session.setUser(user);
        session.setState(State.ACTIVE);
        issueTokens(session, user);
        return sessionRepo.save(session);
    }

    private void issueTokens(Session session, User user) {
        Date now = new Date();
        Date accessTokenExpiry = new Date(now.getTime() + accessTokenValiditySeconds * 1000);

        Map<String,Object> claims = new HashMap<>();
        claims.put("user_id" ,user.getId());
        List<String> roles = new ArrayList<>();
        for(Role role : user.getRoles()) {
            roles.add(role.getValue());
        }
        claims.put("access",roles);

        String token = Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuer("scaler")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(accessTokenExpiry)
                .signWith(secretKey)
                .compact();

        session.setToken(token);
        session.setExpiresAt(accessTokenExpiry);
        session.setRefreshToken(UUID.randomUUID().toString());
        session.setRefreshTokenExpiresAt(new Date(now.getTime() + refreshTokenValiditySeconds * 1000));
    }

    private Role getOrCreateRole(String value) {
        Optional<Role> optionalRole = roleRepo.findByValue(value);
        if (optionalRole.isPresent()) {
            return optionalRole.get();
        }

        Role role = new Role();
        role.setValue(value);
        return roleRepo.save(role);
    }

    private void sendWelcomeEmail(User user) {
        EmailDto emailDto = new EmailDto();
        emailDto.setTo(user.getEmail());
        emailDto.setFrom(mailFrom);
        emailDto.setSubject("Welcome to our store");
        emailDto.setBody("Hi " + user.getName() + ",\n\nYour account has been created. Happy shopping!");
        publishEmail(SIGNUP_TOPIC, emailDto);
    }

    private void publishEmail(String topic, EmailDto emailDto) {
        try {
            kafkaProducerClient.sendMessage(topic, objectMapper.writeValueAsString(emailDto));
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception);
        }
    }
}
