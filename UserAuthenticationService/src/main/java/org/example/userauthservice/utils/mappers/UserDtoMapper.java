package org.example.userauthservice.utils.mappers;

import org.example.userauthservice.dtos.LoginResponseDto;
import org.example.userauthservice.dtos.UserDto;
import org.example.userauthservice.dtos.ValidateTokenResponseDto;
import org.example.userauthservice.models.Role;
import org.example.userauthservice.models.Session;
import org.example.userauthservice.models.User;

public class UserDtoMapper {
    public static UserDto from(User user) {
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setPhoneNumber(user.getPhoneNumber());
        if (user.getAuthProvider() != null) {
            userDto.setAuthProvider(user.getAuthProvider().name());
        }
        for (Role role : user.getRoles()) {
            userDto.getRoles().add(role.getValue());
        }
        return userDto;
    }

    public static User from(UserDto userDto) {
        User user = new User();
        user.setEmail(userDto.getEmail());
        user.setName(userDto.getName());
        user.setPhoneNumber(userDto.getPhoneNumber());
        return user;
    }

    public static LoginResponseDto toLoginResponse(Session session, Long expiresInSeconds) {
        LoginResponseDto loginResponseDto = new LoginResponseDto();
        loginResponseDto.setAccessToken(session.getToken());
        loginResponseDto.setRefreshToken(session.getRefreshToken());
        loginResponseDto.setExpiresInSeconds(expiresInSeconds);
        loginResponseDto.setUser(from(session.getUser()));
        return loginResponseDto;
    }

    public static ValidateTokenResponseDto toValidateTokenResponse(User user) {
        ValidateTokenResponseDto validateTokenResponseDto = new ValidateTokenResponseDto();
        validateTokenResponseDto.setUserId(user.getId());
        validateTokenResponseDto.setEmail(user.getEmail());
        validateTokenResponseDto.setName(user.getName());
        for (Role role : user.getRoles()) {
            validateTokenResponseDto.getRoles().add(role.getValue());
        }
        return validateTokenResponseDto;
    }

}
