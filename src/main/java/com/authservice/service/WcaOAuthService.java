package com.authservice.service;

import com.authservice.exception.AppException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class WcaOAuthService {

    @Value("${wca.client-id}")
    private String clientId;

    @Value("${wca.client-secret}")
    private String clientSecret;

    @Value("${wca.redirect-uri}")
    private String redirectUri;

    @Value("${wca.auth-url}")
    private String authUrl;

    @Value("${wca.token-url}")
    private String tokenUrl;

    @Value("${wca.me-url}")
    private String meUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * @param wcaAccountId WCA numeric internal id — always present for authenticated WCA users
     * @param wcaId        WCA competitor id (e.g. "2009ZEMD01") — null if the user has not competed yet
     * @param name         Display name from WCA profile
     * @param email        Email (present when the "email" scope was granted)
     */
    public record WcaUserInfo(Long wcaAccountId, String wcaId, String name, String email) {}

    public String buildAuthorizationUrl(String state) {
        return authUrl +
                "?client_id=" + enc(clientId) +
                "&redirect_uri=" + enc(redirectUri) +
                "&response_type=code" +
                "&scope=" + enc("public email") +
                "&state=" + enc(state);
    }

    public String exchangeCodeForToken(String code) {
        String body = "grant_type=authorization_code" +
                "&code=" + enc(code) +
                "&redirect_uri=" + enc(redirectUri) +
                "&client_id=" + enc(clientId) +
                "&client_secret=" + enc(clientSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AppException(HttpStatus.BAD_GATEWAY,
                        "WCA token exchange failed: " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            if (!json.has("access_token")) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "WCA did not return an access token");
            }
            return json.get("access_token").asText();
        } catch (AppException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(HttpStatus.BAD_GATEWAY, "Failed to contact WCA: " + e.getMessage());
        }
    }

    public WcaUserInfo getWcaUser(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(meUrl))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AppException(HttpStatus.BAD_GATEWAY,
                        "Failed to fetch WCA user info: " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            // /api/v0/me wraps the user object under the "me" key
            JsonNode me = root.path("me");
            Long wcaAccountId = me.has("id") && !me.get("id").isNull()
                    ? me.get("id").asLong() : null;
            String wcaId = me.has("wca_id") && !me.get("wca_id").isNull()
                    ? me.get("wca_id").asText() : null;
            String name = me.path("name").asText(null);
            String email = me.has("email") && !me.get("email").isNull()
                    ? me.get("email").asText() : null;
            return new WcaUserInfo(wcaAccountId, wcaId, name, email);
        } catch (AppException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(HttpStatus.BAD_GATEWAY, "Failed to contact WCA: " + e.getMessage());
        }
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
