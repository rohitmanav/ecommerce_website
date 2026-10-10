package org.example.userauthservice.services;

import org.example.userauthservice.exceptions.IncorrectPasswordException;
import org.example.userauthservice.exceptions.UserAlreadyExistException;
import org.example.userauthservice.exceptions.UserNotRegisteredException;
import org.example.userauthservice.models.User;
import org.example.userauthservice.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private AuthService authService;

    public User getUserById(Long id) {
        Optional<User> optionalUser = userRepo.findById(id);
        if(optionalUser.isEmpty()) {
            throw new UserNotRegisteredException("user with requested id not found");
        }

        return optionalUser.get();
    }

    public User saveUser(User user) {
        Optional<User> optionalUser = userRepo.findByEmail(user.getEmail());
        if(optionalUser.isPresent()) {
            throw new UserAlreadyExistException("user already exists");
        }

        return userRepo.save(user);
    }

    public User updateProfile(Long userId, String name, String phoneNumber) {
        User user = getUserById(userId);
        if (name != null && !name.isBlank()) {
            user.setName(name.trim());
        }
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            user.setPhoneNumber(phoneNumber.trim());
        }
        return userRepo.save(user);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getUserById(userId);
        if (!bCryptPasswordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IncorrectPasswordException("Current password is incorrect");
        }
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        userRepo.save(user);
        authService.logoutEverywhere(user);
    }
}
