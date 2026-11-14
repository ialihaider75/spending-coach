package com.intellermatrix.spending_coach.user.service;

import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class CurrentUserService {

    private static final String DEFAULT_USER_DISPLAY_NAME = "Default User";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID resolveUserId() {
        return userRepository.findFirstByOrderByCreatedAtAsc()
            .orElseGet(() -> userRepository.save(new User(UUID.randomUUID(), DEFAULT_USER_DISPLAY_NAME, Instant.now())))
            .getId();
    }
}
