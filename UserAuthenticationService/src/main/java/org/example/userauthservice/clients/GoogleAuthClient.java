package org.example.userauthservice.clients;

import org.example.userauthservice.dtos.GoogleTokenInfoDto;
import org.example.userauthservice.exceptions.InvalidTokenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class GoogleAuthClient {

    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Value("${google.client-id:}")
    private String googleClientId;

    public GoogleTokenInfoDto verifyIdToken(String idToken) {
        GoogleTokenInfoDto googleTokenInfoDto;
        try {
            googleTokenInfoDto = restTemplateBuilder.build()
                    .getForEntity(TOKEN_INFO_URL, GoogleTokenInfoDto.class, idToken)
                    .getBody();
        } catch (RestClientException exception) {
            throw new InvalidTokenException("Google token could not be verified");
        }

        if (googleTokenInfoDto == null || googleTokenInfoDto.getEmail() == null) {
            throw new InvalidTokenException("Google token does not contain an email");
        }
        if (!"true".equalsIgnoreCase(googleTokenInfoDto.getEmailVerified())) {
            throw new InvalidTokenException("Google email is not verified");
        }
        if (googleClientId != null && !googleClientId.isBlank() && !googleClientId.equals(googleTokenInfoDto.getAud())) {
            throw new InvalidTokenException("Google token was issued for a different application");
        }
        return googleTokenInfoDto;
    }
}
