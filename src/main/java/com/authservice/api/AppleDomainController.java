package com.authservice.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the Sign in with Apple domain association file.
 *
 * When a Services ID is configured with a web domain, Apple crawls
 * https://{domain}/.well-known/apple-developer-domain-association.txt to verify
 * ownership. The file content is a JSON blob signed by Apple, generated in the
 * Apple Developer portal and provided via the APPLE_DOMAIN_ASSOCIATION env var.
 *
 * Returns 404 when not configured — this lets the rest of Apple Sign-In work
 * (iOS native flow uses bundle-id as audience and doesn't require this file).
 * Android/web flow won't verify until the env var is set.
 */
@RestController
public class AppleDomainController {

    @Value("${apple.domain-association:}")
    private String domainAssociation;

    @GetMapping(path = "/.well-known/apple-developer-domain-association.txt",
                produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> domainAssociation() {
        if (domainAssociation == null || domainAssociation.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(domainAssociation);
    }
}
