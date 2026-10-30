package com.gymkhata.common.config;

import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.domain.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Seeds the single platform SUPER_ADMIN on first boot if no users exist.
 */
@Component
public class Bootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Bootstrapper.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String phone;
    private final String password;
    private final String name;

    public Bootstrapper(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        Clock clock,
                        @Value("${app.super-admin.phone:}") String phone,
                        @Value("${app.super-admin.password:}") String password,
                        @Value("${app.super-admin.name:Platform Admin}") String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.phone = phone;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            log.warn("No users exist and no super-admin configured (app.super-admin.*). " +
                    "Set app.super-admin.phone/password to seed the platform admin.");
            return;
        }
        String e164 = PhoneUtils.normalize(phone);
        if (e164 == null) {
            log.error("Configured super-admin phone is invalid: {}", PhoneUtils.mask(phone));
            return;
        }
        User admin = new User();
        admin.setRole(User.ROLE_SUPER_ADMIN);
        admin.setGymId(null);
        admin.setName(name);
        admin.setPhone(e164);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setStatus(User.STATUS_ACTIVE);
        admin.setCreatedAt(clock.now());
        admin.setUpdatedAt(clock.now());
        userRepository.save(admin);
        log.info("Seeded SUPER_ADMIN {}", PhoneUtils.mask(e164));
    }
}
