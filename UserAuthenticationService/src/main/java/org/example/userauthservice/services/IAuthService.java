package org.example.userauthservice.services;

import org.example.userauthservice.models.Session;
import org.example.userauthservice.models.User;

import java.util.Optional;

public interface IAuthService {

    User signup(String name,String email,String password);

    Session login(String email, String password);

    Session socialLogin(String provider, String idToken);

    Session refresh(String refreshToken);

    void logout(String token);

    Optional<User> validateToken(String token);

    void forgotPassword(String email);

    void resetPassword(String token, String newPassword);

    Long getAccessTokenValiditySeconds();
}
