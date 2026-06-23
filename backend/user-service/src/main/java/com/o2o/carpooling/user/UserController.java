package com.o2o.carpooling.user;

import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.domain.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserRepository userRepository;

    UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    UserAccount upsert(@RequestBody UpsertUserRequest request) {
        UserAccount user = new UserAccount(
            request.userId(),
            request.phone(),
            request.roles(),
            Instant.now()
        );
        userRepository.upsert(user);
        return userRepository.findByUserId(user.userId()).orElseThrow();
    }

    @GetMapping("/{userId}")
    UserAccount get(@PathVariable String userId) {
        return userRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
    }

    record UpsertUserRequest(String userId, String phone, Set<UserRole> roles) {
    }
}
