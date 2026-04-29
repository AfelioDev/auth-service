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
        if (user.isCurrentlyBanned()) {
            throw new com.authservice.exception.BannedException(user.getBanReason(), user.getBanUntil());
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
    /**
     * @param wcaAccountId WCA numeric internal id (always present)
     * @param wcaId        WCA competitor id, null if user has not competed yet
     */
    public User handleWcaCallback(Long wcaAccountId, String wcaId, String name, String email,
                                  String accessToken, Long linkUserId) {
        if (linkUserId != null) {
            return linkWcaToUser(linkUserId, wcaAccountId, wcaId, accessToken);
        }
        return loginOrRegisterWithWca(wcaAccountId, wcaId, name, email, accessToken);
    }

    private User linkWcaToUser(Long userId, Long wcaAccountId, String wcaId, String accessToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        userRepository.findByWcaAccountId(wcaAccountId).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new AppException(HttpStatus.CONFLICT,
                        "This WCA account is already linked to another account");
            }
        });

        userRepository.updateWcaLink(userId, wcaAccountId, wcaId, accessToken);
        user.setWcaAccountId(wcaAccountId);
        user.setWcaId(wcaId);
        user.setWcaAccessToken(accessToken);
        return user;
    }

    private User loginOrRegisterWithWca(Long wcaAccountId, String wcaId, String name,
                                        String email, String accessToken) {
        // 1. Already registered via WCA — just refresh the token
        Optional<User> byWcaAccountId = userRepository.findByWcaAccountId(wcaAccountId);
        if (byWcaAccountId.isPresent()) {
            User user = byWcaAccountId.get();
            userRepository.updateWcaLink(user.getId(), wcaAccountId, wcaId, accessToken);
            user.setWcaId(wcaId);
            return user;
        }

        // 2. Email matches an existing account — auto-link (merges accounts)
        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User user = byEmail.get();
                userRepository.updateWcaLink(user.getId(), wcaAccountId, wcaId, accessToken);
                user.setWcaAccountId(wcaAccountId);
                user.setWcaId(wcaId);
                user.setWcaAccessToken(accessToken);
                return user;
            }
        }

        // 3. New user — create account from WCA profile
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setWcaAccountId(wcaAccountId);
        user.setWcaId(wcaId);
        user.setWcaAccessToken(accessToken);
        userRepository.save(user);
        return user;
    }

    public void setPassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getPasswordHash() != null) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new AppException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
            }
        }

        userRepository.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    public void unlinkWca(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getWcaAccountId() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "No WCA account linked");
        }
        if (user.getPasswordHash() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Set a password before unlinking WCA to avoid losing access to your account");
        }
        userRepository.clearWcaLink(userId);
    }

    public UserDto getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        // Tarea 5 / ONE-9: a banned user with a stale JWT must be kicked out the
        // next time they hit any authenticated read. The structured 403 lets the
        // client show the same blocking dialog as on login.
        if (user.isCurrentlyBanned()) {
            throw new com.authservice.exception.BannedException(user.getBanReason(), user.getBanUntil());
        }
        return toDto(user);
    }

    /**
     * Admin operation behind {@code /internal/users/{id}/ban} (Tarea 5 / ONE-9).
     * {@code reason == null} clears the ban; {@code banUntil == null} with a
     * non-null reason creates a permanent ban.
     */
    public void setBan(Long userId, String reason, java.time.OffsetDateTime banUntil) {
        if (reason != null && reason.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "reason cannot be empty");
        }
        int n = userRepository.setBan(userId, reason, banUntil);
        if (n == 0) {
            throw new AppException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getResolvedName(),
                user.getEmail(),
                user.getWcaAccountId(),
                user.getWcaId(),
                user.getWcaAccountId() != null,
                user.getPasswordHash() != null,
                user.getCreatedAt()
        );
    }
}
