package com.authservice.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives Apple's form POST after Sign in with Apple on the Android web flow and
 * 302-redirects to an Android intent URL that hands the token back to the
 * `sign_in_with_apple` plugin inside the app.
 *
 * Why this exists: on Android the plugin opens a Chrome Custom Tab pointing at
 * Apple's authorization URL. Apple only supports HTTPS return URLs — so it posts
 * the result to this endpoint, which must bounce back into the app via the
 * `signinwithapple://` scheme registered by the plugin.
 */
@RestController
public class AppleCallbackController {

    @Value("${apple.callback-android-package:com.onespeed.vitezapp}")
    private String androidPackage;

    /**
     * Apple's authorization server may perform a GET/HEAD preflight on the return URL
     * before posting the result. Returning 200 here signals the URL is reachable.
     */
    @RequestMapping(path = "/auth/apple/callback",
                    method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<String> preflight() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("OK");
    }

    @PostMapping(path = "/auth/apple/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(name = "id_token", required = false) String idToken,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String error) {

        Map<String, String> params = new LinkedHashMap<>();
        if (code != null) params.put("code", code);
        if (idToken != null) params.put("id_token", idToken);
        if (state != null) params.put("state", state);
        if (user != null) params.put("user", user);
        if (error != null) params.put("error", error);

        StringBuilder query = new StringBuilder();
        params.forEach((k, v) -> {
            if (!query.isEmpty()) query.append('&');
            query.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                 .append('=')
                 .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });

        String intentUrl = "intent://callback?" + query
                + "#Intent;package=" + androidPackage
                + ";scheme=signinwithapple;end";

        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", intentUrl)
                .build();
    }
}
