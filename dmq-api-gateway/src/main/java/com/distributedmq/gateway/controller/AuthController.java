package com.distributedmq.gateway.controller;

import com.distributedmq.gateway.security.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authentication Controller.
 * Provides endpoints for user authentication and token generation.
 * 
 * In production, this would integrate with:
 * - User database
 * - Password hashing (BCrypt)
 * - OAuth2/OpenID Connect
 * - External identity providers (LDAP, AD, etc.)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final JwtUtil jwtUtil;
    
    /**
     * Login endpoint.
     * POST /api/auth/login
     * 
     * In production, validate credentials against database.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());
        
        // TODO: Validate credentials against database
        // For demo purposes, accept any credentials
        if (request.getUsername() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, "Invalid credentials", null));
        }
        
        // Determine user role and tier
        List<String> roles = determineRoles(request.getUsername());
        String tier = determineTier(request.getUsername());
        
        // Generate JWT token
        String token = jwtUtil.generateToken(request.getUsername(), roles, tier);
        
        log.info("Login successful for user: {}, roles: {}, tier: {}", 
                request.getUsername(), roles, tier);
        
        LoginResponse response = new LoginResponse(token, "Login successful", roles);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Validate token endpoint.
     * POST /api/auth/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody TokenRequest request) {
        boolean valid = jwtUtil.validateToken(request.getToken());
        
        Map<String, Object> response = new HashMap<>();
        response.put("valid", valid);
        
        if (valid) {
            response.put("username", jwtUtil.extractUsername(request.getToken()));
            response.put("roles", jwtUtil.extractRoles(request.getToken()));
            response.put("tier", jwtUtil.extractTier(request.getToken()));
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Refresh token endpoint.
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@RequestBody TokenRequest request) {
        if (!jwtUtil.validateToken(request.getToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, "Invalid token", null));
        }
        
        String username = jwtUtil.extractUsername(request.getToken());
        List<String> roles = jwtUtil.extractRoles(request.getToken());
        String tier = jwtUtil.extractTier(request.getToken());
        
        // Generate new token
        String newToken = jwtUtil.generateToken(username, roles, tier);
        
        LoginResponse response = new LoginResponse(newToken, "Token refreshed", roles);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Register new user endpoint.
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        log.info("Registration attempt for user: {}", request.getUsername());
        
        // TODO: Validate and store user in database
        // For demo purposes, just return success
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "User registered successfully");
        response.put("username", request.getUsername());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Determine roles based on username (demo logic).
     * In production, fetch from database.
     */
    private List<String> determineRoles(String username) {
        if (username.startsWith("admin")) {
            return Arrays.asList("USER", "ADMIN");
        }
        return Arrays.asList("USER");
    }
    
    /**
     * Determine tier based on username (demo logic).
     * In production, fetch from database.
     */
    private String determineTier(String username) {
        if (username.contains("premium")) {
            return "premium";
        } else if (username.contains("free")) {
            return "free";
        }
        return "standard";
    }
    
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
    
    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private String email;
    }
    
    @Data
    public static class TokenRequest {
        private String token;
    }
    
    @Data
    @RequiredArgsConstructor
    public static class LoginResponse {
        private final String token;
        private final String message;
        private final List<String> roles;
    }
}
