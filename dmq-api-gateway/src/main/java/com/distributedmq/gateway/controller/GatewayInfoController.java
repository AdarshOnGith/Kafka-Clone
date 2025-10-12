package com.distributedmq.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gateway Information Controller.
 * Provides information about configured routes and gateway status.
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway")
public class GatewayInfoController {
    
    @Autowired
    private RouteLocator routeLocator;
    
    /**
     * Get all configured routes.
     * GET /api/gateway/routes
     */
    @GetMapping("/routes")
    public ResponseEntity<Map<String, Object>> getRoutes() {
        Flux<Route> routes = routeLocator.getRoutes();
        
        List<Map<String, Object>> routeList = routes
                .map(route -> {
                    Map<String, Object> routeInfo = new HashMap<>();
                    routeInfo.put("id", route.getId());
                    routeInfo.put("uri", route.getUri().toString());
                    routeInfo.put("predicates", route.getPredicate().toString());
                    routeInfo.put("filters", route.getFilters().stream()
                            .map(f -> f.toString())
                            .collect(Collectors.toList()));
                    routeInfo.put("order", route.getOrder());
                    return routeInfo;
                })
                .collectList()
                .block();
        
        Map<String, Object> response = new HashMap<>();
        response.put("routes", routeList);
        response.put("count", routeList != null ? routeList.size() : 0);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get gateway status.
     * GET /api/gateway/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("gateway", "DMQ API Gateway");
        status.put("version", "1.0.0");
        status.put("status", "UP");
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }
}
