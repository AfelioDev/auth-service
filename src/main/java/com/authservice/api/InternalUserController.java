package com.authservice.api;

import com.authservice.api.dto.InternalUserDto;
import com.authservice.domain.User;
import com.authservice.domain.UserRepository;
import com.authservice.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal service-to-service endpoint.
 * Accessible only within the one-timer-internal Docker network.
 * Traefik does NOT expose /internal/** — it is only reachable by other containers.
 */
@RestController
@RequestMapping("/internal")
public class InternalUserController {

    private final UserRepository userRepository;

    public InternalUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<InternalUserDto> getUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(new InternalUserDto(
                user.getId(),
                user.getName(),
                user.getWcaId(),
                null  // avatarUrl — not implemented yet
        ));
    }
}
