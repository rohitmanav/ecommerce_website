package org.example.userauthservice.config;

import lombok.extern.slf4j.Slf4j;
import org.example.userauthservice.models.AuthProvider;
import org.example.userauthservice.models.Role;
import org.example.userauthservice.models.User;
import org.example.userauthservice.repos.RoleRepo;
import org.example.userauthservice.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    public static final String ADMIN_ROLE = "ADMIN";
    public static final String NON_ADMIN_ROLE = "NON_ADMIN";

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.name}")
    private String adminName;

    @Override
    public void run(String... args) {
        Role adminRole = getOrCreateRole(ADMIN_ROLE);
        Role nonAdminRole = getOrCreateRole(NON_ADMIN_ROLE);

        if (adminEmail == null || adminEmail.isBlank() || userRepo.findByEmail(adminEmail).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setName(adminName);
        admin.setPassword(bCryptPasswordEncoder.encode(adminPassword));
        admin.setAuthProvider(AuthProvider.LOCAL);
        admin.getRoles().add(adminRole);
        admin.getRoles().add(nonAdminRole);
        userRepo.save(admin);
        log.info("Created admin account {}", adminEmail);
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
}
