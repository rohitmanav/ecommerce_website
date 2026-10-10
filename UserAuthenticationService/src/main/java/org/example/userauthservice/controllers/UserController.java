package org.example.userauthservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.userauthservice.config.DataInitializer;
import org.example.userauthservice.dtos.ChangePasswordRequestDto;
import org.example.userauthservice.dtos.MessageResponseDto;
import org.example.userauthservice.dtos.OrderSummaryDto;
import org.example.userauthservice.dtos.UpdateProfileRequestDto;
import org.example.userauthservice.dtos.UserDto;
import org.example.userauthservice.exceptions.ForbiddenException;
import org.example.userauthservice.exceptions.InvalidTokenException;
import org.example.userauthservice.models.Role;
import org.example.userauthservice.models.User;
import org.example.userauthservice.services.IAuthService;
import org.example.userauthservice.services.IUserOrderActivityService;
import org.example.userauthservice.services.UserService;
import org.example.userauthservice.utils.TokenUtil;
import org.example.userauthservice.utils.mappers.UserDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Tag(name = "Users", description = "View and update user profiles")
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private IAuthService authService;

    @Autowired
    private IUserOrderActivityService userOrderActivityService;

    @Operation(summary = "Get the profile of the logged-in user")
    @GetMapping("/me")
    public UserDto getMyProfile(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return UserDtoMapper.from(currentUser(authorizationHeader));
    }

    @Operation(summary = "Order activity of the logged-in user",
            description = "Built from order status events the order service publishes on Kafka")
    @GetMapping("/me/order-summary")
    public OrderSummaryDto getMyOrderSummary(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return userOrderActivityService.getOrderSummary(currentUser(authorizationHeader).getId());
    }

    @Operation(summary = "Update the name or phone number of the logged-in user")
    @PutMapping("/me")
    public UserDto updateMyProfile(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                   @Valid @RequestBody UpdateProfileRequestDto updateProfileRequestDto) {
        User user = currentUser(authorizationHeader);
        User updated = userService.updateProfile(user.getId(),
                updateProfileRequestDto.getName(), updateProfileRequestDto.getPhoneNumber());
        return UserDtoMapper.from(updated);
    }

    @Operation(summary = "Change the password of the logged-in user", description = "All sessions are logged out afterwards")
    @PutMapping("/me/password")
    public MessageResponseDto changeMyPassword(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                               @Valid @RequestBody ChangePasswordRequestDto changePasswordRequestDto) {
        User user = currentUser(authorizationHeader);
        userService.changePassword(user.getId(),
                changePasswordRequestDto.getCurrentPassword(), changePasswordRequestDto.getNewPassword());
        return new MessageResponseDto("Password changed, please login again");
    }

    @Operation(summary = "Get a user by id", description = "Allowed for the user themselves or an admin")
    @GetMapping("/{id}")
    public UserDto getUserById(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                               @PathVariable Long id) {
        User requester = currentUser(authorizationHeader);
        if (!requester.getId().equals(id) && !isAdmin(requester)) {
            throw new ForbiddenException("You can only view your own profile");
        }
        User user = userService.getUserById(id);
        return UserDtoMapper.from(user);
    }

    @Operation(summary = "Create a user record (admin only)")
    @PostMapping
    public ResponseEntity<UserDto> createUser(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                              @RequestBody UserDto userDto) {
        User requester = currentUser(authorizationHeader);
        if (!isAdmin(requester)) {
            throw new ForbiddenException("Only admins can create users directly");
        }
        User input = UserDtoMapper.from(userDto);
        User response  = userService.saveUser(input);
        return new ResponseEntity<>(UserDtoMapper.from(response), HttpStatus.CREATED);
    }

    private User currentUser(String authorizationHeader) {
        String token = TokenUtil.extractBearerToken(authorizationHeader);
        Optional<User> optionalUser = authService.validateToken(token);
        if (optionalUser.isEmpty()) {
            throw new InvalidTokenException("Token is invalid or expired");
        }
        return optionalUser.get();
    }

    private boolean isAdmin(User user) {
        for (Role role : user.getRoles()) {
            if (DataInitializer.ADMIN_ROLE.equals(role.getValue())) {
                return true;
            }
        }
        return false;
    }
}
