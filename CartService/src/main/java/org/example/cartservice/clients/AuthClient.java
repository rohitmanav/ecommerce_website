package org.example.cartservice.clients;

import org.example.cartservice.dtos.UserDto;
import org.example.cartservice.dtos.ValidateTokenDto;
import org.example.cartservice.exceptions.ServiceUnavailableException;
import org.example.cartservice.exceptions.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AuthClient {

    private static final String VALIDATE_TOKEN_URL = "http://userservice/auth/validateToken";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private RestTemplate restTemplate;

    public UserDto validateToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new UnauthorizedException("Authorization header is missing");
        }
        String token;
        if (authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        } else {
            token = authorizationHeader.trim();
        }

        try {
            UserDto userDto = restTemplate.postForEntity(VALIDATE_TOKEN_URL, new ValidateTokenDto(token), UserDto.class).getBody();
            if (userDto == null) {
                throw new UnauthorizedException("Token is invalid or expired");
            }
            return userDto;
        } catch (HttpClientErrorException exception) {
            throw new UnauthorizedException("Token is invalid or expired");
        } catch (RestClientException | IllegalStateException exception) {
            throw new ServiceUnavailableException("User service is unavailable");
        }
    }
}
