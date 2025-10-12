package com.distributedmq.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT Utility for token generation and validation.
 */
@Slf4j
@Component
public class JwtUtil {
    
    @Value("${dmq.gateway.jwt.secret}")
    private String secret;
    
    @Value("${dmq.gateway.jwt.expiration-ms}")
    private long expirationMs;
    
    @Value("${dmq.gateway.jwt.issuer}")
    private String issuer;
    
    private Key signingKey;
    
    @PostConstruct
    public void init() {
        // Create signing key from secret
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT signing key initialized");
    }
    
    /**
     * Generate JWT token for user.
     */
    public String generateToken(String username, List<String> roles, String tier) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);
        claims.put("tier", tier);
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * Validate JWT token.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract username from token.
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }
    
    /**
     * Extract roles from token.
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractClaims(token);
        return (List<String>) claims.get("roles");
    }
    
    /**
     * Extract user tier from token.
     */
    public String extractTier(String token) {
        Claims claims = extractClaims(token);
        return (String) claims.get("tier");
    }
    
    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        Date expiration = extractClaims(token).getExpiration();
        return expiration.before(new Date());
    }
    
    /**
     * Extract all claims from token.
     */
    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
