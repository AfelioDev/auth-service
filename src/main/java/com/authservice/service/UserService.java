package com.authservice.service;

import com.authservice.api.dto.LoginRequest;
import com.authservice.api.dto.RegisterRequest;
import com.authservice.api.dto.UserDto;
import com.authservice.domain.User;
import com.authservice.domain.UserRepository;
import com.authservice.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public String register(RegisterRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new AppException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepository.save(user);
        return jwtService.generateToken(user);
    }

    public String login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return jwtService.generateToken(user);
    }

    /**
     * Handles the WCA OAuth2 callback for both the login/register flow and the link flow.
     *
     * @param wcaId       WCA competitor ID from WCA API
     * @param name        Full name from WCA API
     * @param email       Email from WCA API (may be null)
     * @param accessToken WCA access token
     * @param linkUserId  If non-null, link the WCA ID to this existing user instead of creating/finding one
     * @return JWT for the resolved user
     */
    public String handleWcaCallback(String wcaId, String name, String email,
                                    String accessToken, Long linkUserId) {
        if (linkUserId != null) {
            return linkWcaToUser(linkUserId, wcaId, accessToken);
        }
        return loginOrRegisterWithWca(wcaId, name, email, accessToken);
    }

    private String linkWcaToUser(Long userId, String wcaId, String accessToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        // Ensure the WCA ID isn't already linked to a different account
        userRepository.findByWcaId(wcaId).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new AppException(HttpStatus.CONFLICT,
                        "This WCA ID is already linked to another account");
            }
        });

        userRepository.updateWcaLink(userId, wcaId, accessToken);
        user.setWcaId(wcaId);
        user.setWcaAccessToken(accessToken);
        return jwtService.generateToken(user);
    }

    private String loginOrRegisterWithWca(String wcaId, String name, String email, String accessToken) {
        // 1. Already registered via WCA — just refresh the token
        Optional<User> byWcaId = userRepository.findByWcaId(wcaId);
        if (byWcaId.isPresent()) {
            User user = byWcaId.get();
            userRepository.updateWcaLink(user.getId(), wcaId, accessToken);
            return jwtService.generateToken(user);
        }

        // 2. Email matches an existing account — auto-link (merges accounts)
        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User user = byEmail.get();
                userRepository.updateWcaLink(user.getId(), wcaId, accessToken);
                user.setWcaId(wcaId);
                user.setWcaAccessToken(accessToken);
                return jwtService.generateToken(user);
            }
        }

        // 3. New user — create account from WCA profile
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setWcaId(wcaId);
        user.setWcaAccessToken(accessToken);
        userRepository.save(user);
        return jwtService.generateToken(user);
    }

    public void unlinkWca(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getPasswordHash() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Cannot unlink WCA: no password set. Set a password before unlinking.");
        }
        userRepository.clearWcaLink(userId);
    }

    public UserDto getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        return toDto(user);
    }

    private UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getWcaId(),
                user.getWcaId() != null,
                user.getPasswordHash() != null,
                user.getCreatedAt()
        );
    }
}
