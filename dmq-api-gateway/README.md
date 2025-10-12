# DMQ API Gateway

The **API Gateway** is the single entry point for all client requests to the DMQ system. It provides routing, authentication, rate limiting, circuit breaking, and load balancing across microservices.

## 📋 Table of Contents
- [Architecture Overview](#architecture-overview)
- [Key Features](#key-features)
- [Routing Configuration](#routing-configuration)
- [Authentication (JWT)](#authentication-jwt)
- [Rate Limiting](#rate-limiting)
- [Circuit Breaker](#circuit-breaker)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Deployment](#deployment)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)

---

## 🏗️ Architecture Overview

### Request Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                      API Gateway Flow                            │
└─────────────────────────────────────────────────────────────────┘

Client Request
     │
     ↓
┌──────────────────────┐
│  Authentication      │  ← JWT validation
│  Filter              │  ← Extract user info
└──────────┬───────────┘  ← Check admin paths
           │
           ↓
┌──────────────────────┐
│  Logging Filter      │  ← Log request details
└──────────┬───────────┘  ← Track request time
           │
           ↓
┌──────────────────────┐
│  Rate Limiting       │  ← Check Redis counter
│  (Redis-backed)      │  ← Token bucket algorithm
└──────────┬───────────┘  ← User/IP based
           │
           ↓
┌──────────────────────┐
│  Circuit Breaker     │  ← Check service health
│  (Resilience4j)      │  ← Fail fast if down
└──────────┬───────────┘  ← Fallback response
           │
           ↓
┌──────────────────────┐
│  Load Balancer       │  ← Consul discovery
│  (Ribbon/LoadBalancer)│ ← Round-robin routing
└──────────┬───────────┘  ← Health-aware
           │
           ↓
    Microservice
    (Producer/Consumer/Metadata/etc.)
```

### Components

```
dmq-api-gateway/
├── security/
│   └── JwtUtil.java                    # JWT token generation/validation
├── filter/
│   ├── AuthenticationFilter.java      # JWT-based authentication
│   └── LoggingFilter.java             # Request/response logging
├── config/
│   ├── RateLimitConfig.java           # Rate limiting setup
│   └── GatewayConfig.java             # Route configuration
└── controller/
    ├── AuthController.java            # Login, register, validate
    ├── FallbackController.java        # Circuit breaker fallbacks
    └── GatewayInfoController.java     # Route information
```

---

## 🎯 Key Features

### 1. **Routing**
- **Consul-based service discovery**: Automatic instance detection
- **Load balancing**: Round-robin across healthy instances
- **Path-based routing**: `/api/produce/**` → Producer Ingestion Service
- **Dynamic route updates**: No restart required when services scale

### 2. **Authentication (JWT)**
- **Token-based**: Bearer token in Authorization header
- **Role-based access**: USER, ADMIN roles
- **Tier-based rate limiting**: premium, standard, free tiers
- **Public paths**: No auth for /actuator/**, /api/auth/**
- **Admin paths**: Restricted access to admin endpoints

### 3. **Rate Limiting**
- **Redis-backed**: Distributed token bucket algorithm
- **User-based**: Extract user from JWT token
- **Tier-based limits**:
  - **Premium**: 5000 req/s, 10000 burst
  - **Standard**: 1000 req/s, 2000 burst
  - **Free**: 100 req/s, 200 burst
- **IP fallback**: Rate limit by IP for unauthenticated requests

### 4. **Circuit Breaker (Resilience4j)**
- **Automatic failure detection**: 50% failure rate threshold
- **Fast fail**: Return fallback immediately when circuit is open
- **Auto-recovery**: Half-open state allows testing recovery
- **Per-service configuration**: Different settings for each service

### 5. **Retry Logic**
- **Automatic retries**: 3 retries with exponential backoff
- **Idempotent requests**: Only retry GET/POST produce
- **Backoff strategy**: 100ms → 200ms → 400ms → 800ms

### 6. **CORS Support**
- **Global configuration**: Allow all origins (configurable)
- **Preflight handling**: OPTIONS method support
- **Custom headers**: X-Request-Id, X-Response-Time

---

## 🛣️ Routing Configuration

### Route Definitions

| Path Pattern | Target Service | Methods | Rate Limit | Circuit Breaker |
|-------------|----------------|---------|------------|-----------------|
| `/api/produce/**` | Producer Ingestion | POST | 1000/s | ✅ Yes |
| `/api/consume/**` | Consumer Egress | GET | 2000/s | ✅ Yes |
| `/api/metadata/**` | Metadata Service | GET, POST | 500/s | ❌ No |
| `/api/storage/**` | Storage Service | GET, POST | 100/s (admin) | ❌ No |
| `/api/controller/**` | Controller Service | GET, POST | 50/s (admin) | ❌ No |
| `/api/auth/**` | Gateway Auth | POST | N/A (public) | ❌ No |

### Example Routes (application.yml)

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Producer Ingestion
        - id: producer-ingestion
          uri: lb://dmq-producer-ingestion
          predicates:
            - Path=/api/produce/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: producerCircuitBreaker
                fallbackUri: forward:/fallback/producer
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1000
                redis-rate-limiter.burstCapacity: 2000
```

**How it works**:
1. Request: `POST /api/produce/user-events`
2. StripPrefix=1: Remove `/api`, forward to `/produce/user-events`
3. Circuit breaker: Check if producer service is healthy
4. Rate limiter: Check Redis counter for user
5. Load balancer: Route to healthy producer instance via Consul

---

## 🔐 Authentication (JWT)

### JWT Token Structure

```json
{
  "sub": "alice",                    // Username
  "iss": "dmq-gateway",              // Issuer
  "iat": 1697123456,                 // Issued at
  "exp": 1697209856,                 // Expires (24 hours)
  "roles": ["USER"],                 // User roles
  "tier": "standard"                 // Rate limit tier
}
```

### Authentication Flow

```
1. Client Login
   POST /api/auth/login
   {"username": "alice", "password": "secret"}
   
2. Gateway validates credentials
   (In production: check database, hash password)
   
3. Generate JWT token
   token = jwtUtil.generateToken("alice", ["USER"], "standard")
   
4. Return token to client
   {"token": "eyJhbGc...", "message": "Login successful"}
   
5. Client uses token for subsequent requests
   Authorization: Bearer eyJhbGc...
   
6. AuthenticationFilter validates token
   - Extract username: "alice"
   - Extract roles: ["USER"]
   - Extract tier: "standard"
   - Add headers: X-User-Id, X-User-Roles, X-User-Tier
   
7. Downstream services receive user context
   Request headers:
   - X-User-Id: alice
   - X-User-Roles: USER
   - X-User-Tier: standard
```

### API Endpoints

#### Login
```bash
POST /api/auth/login
Content-Type: application/json

{
  "username": "alice",
  "password": "secret"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "message": "Login successful",
  "roles": ["USER"]
}
```

#### Validate Token
```bash
POST /api/auth/validate
Content-Type: application/json

{
  "token": "eyJhbGc..."
}

Response:
{
  "valid": true,
  "username": "alice",
  "roles": ["USER"],
  "tier": "standard"
}
```

#### Refresh Token
```bash
POST /api/auth/refresh
Content-Type: application/json

{
  "token": "eyJhbGc..."
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",  // New token
  "message": "Token refreshed",
  "roles": ["USER"]
}
```

### Role-Based Access Control

**Public Paths** (no auth required):
- `/actuator/**`
- `/fallback/**`
- `/api/auth/**`

**Admin Paths** (require ADMIN role):
- `/api/storage/**`
- `/api/controller/**`
- `/api/metadata/topics/create`
- `/api/metadata/topics/delete`

**User Paths** (require USER role):
- `/api/produce/**`
- `/api/consume/**`
- `/api/metadata/**` (read-only)

---

## ⏱️ Rate Limiting

### Token Bucket Algorithm

```
Rate Limiter (Redis-backed)
───────────────────────────

Bucket: user:alice:standard
├── Capacity: 2000 tokens (burst)
├── Refill rate: 1000 tokens/second
└── Current tokens: 1500

Request arrives:
1. Check bucket: 1500 tokens available
2. Consume 1 token
3. Bucket now has 1499 tokens
4. Allow request

After 1 second:
1. Refill: 1499 + 1000 = 2499 (capped at 2000)
2. Bucket has 2000 tokens
3. Ready for burst traffic
```

### Tier-Based Limits

| Tier | Replenish Rate | Burst Capacity | Monthly Quota |
|------|----------------|----------------|---------------|
| **Premium** | 5000 req/s | 10000 | Unlimited |
| **Standard** | 1000 req/s | 2000 | 100M requests |
| **Free** | 100 req/s | 200 | 1M requests |

### Configuration

```yaml
dmq:
  gateway:
    rate-limit:
      enabled: true
      default-replenish-rate: 1000
      default-burst-capacity: 2000
      
      user-limits:
        premium:
          replenish-rate: 5000
          burst-capacity: 10000
        standard:
          replenish-rate: 1000
          burst-capacity: 2000
        free:
          replenish-rate: 100
          burst-capacity: 200
```

### Rate Limit Response

```bash
# Request exceeds rate limit
HTTP/1.1 429 Too Many Requests
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1697123500

{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Please try again later.",
  "retryAfter": 5
}
```

---

## 🔌 Circuit Breaker

### Resilience4j Configuration

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 100              # Last 100 requests
        minimumNumberOfCalls: 10            # Min calls before tripping
        failureRateThreshold: 50            # 50% failure rate
        slowCallRateThreshold: 50           # 50% slow calls
        slowCallDurationThreshold: 5s       # >5s is slow
        waitDurationInOpenState: 30s        # Stay open for 30s
        permittedNumberOfCallsInHalfOpenState: 5  # Test with 5 calls
```

### Circuit States

```
┌─────────────────────────────────────────────────────────┐
│              Circuit Breaker State Machine               │
└─────────────────────────────────────────────────────────┘

CLOSED (Normal operation)
  ├── Monitor failure rate
  ├── Allow all requests
  └── If failure rate > 50% → OPEN
        │
        ↓
OPEN (Fail fast)
  ├── Reject all requests immediately
  ├── Return fallback response
  └── After 30 seconds → HALF_OPEN
        │
        ↓
HALF_OPEN (Testing recovery)
  ├── Allow 5 test requests
  ├── If success rate > 50% → CLOSED
  └── If still failing → OPEN
```

### Fallback Responses

**Producer Service Fallback**:
```json
{
  "error": "Producer service temporarily unavailable",
  "message": "Please try again later. Messages are being queued.",
  "status": 503,
  "timestamp": 1697123456789
}
```

**Consumer Service Fallback**:
```json
{
  "error": "Consumer service temporarily unavailable",
  "message": "Please try again later. Your offset has been preserved.",
  "status": 503,
  "timestamp": 1697123456789
}
```

---

## 📡 API Reference

### Gateway Endpoints

#### Get All Routes
```bash
GET /api/gateway/routes

Response:
{
  "routes": [
    {
      "id": "producer-ingestion",
      "uri": "lb://dmq-producer-ingestion",
      "predicates": "Path: /api/produce/**",
      "filters": ["StripPrefix=1", "CircuitBreaker", "RequestRateLimiter"],
      "order": 0
    },
    ...
  ],
  "count": 5
}
```

#### Get Gateway Status
```bash
GET /api/gateway/status

Response:
{
  "gateway": "DMQ API Gateway",
  "version": "1.0.0",
  "status": "UP",
  "timestamp": 1697123456789
}
```

### Example Client Requests

#### Produce Message (with JWT)
```bash
# 1. Login first
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret"}'

# Response: {"token": "eyJhbGc..."}

# 2. Use token to produce message
curl -X POST http://localhost:8080/api/produce/user-events \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "Content-Type: application/json" \
  -d '{
    "records": [
      {"key": "user-123", "value": {"action": "login", "timestamp": 1697123456}}
    ]
  }'
```

#### Consume Messages (with JWT)
```bash
curl -X GET http://localhost:8080/api/consume/my-group/user-events \
  -H "Authorization: Bearer eyJhbGc..."
```

---

## ⚙️ Configuration

### Environment Variables

```bash
# Consul discovery
SPRING_CLOUD_CONSUL_HOST=consul
SPRING_CLOUD_CONSUL_PORT=8500

# Redis for rate limiting
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379

# JWT secret (MUST change in production)
DMQ_JWT_SECRET=your-secret-key-min-256-bits-long

# Authentication
DMQ_GATEWAY_AUTH_ENABLED=true
```

### JWT Secret Generation

```bash
# Generate secure secret (256 bits for HS256)
openssl rand -base64 32

# Example output:
# dGhpcy1pcy1hLXZlcnktc2VjdXJlLXNlY3JldC1rZXk=
```

---

## 🚀 Deployment

### Docker Compose

```bash
# Build the service
mvn clean package -DskipTests

# Start gateway + Redis
docker-compose up -d

# Check logs
docker-compose logs -f api-gateway

# Test health
curl http://localhost:8080/actuator/health
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dmq-api-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: dmq-api-gateway
  template:
    metadata:
      labels:
        app: dmq-api-gateway
    spec:
      containers:
      - name: gateway
        image: dmq-api-gateway:1.0.0
        env:
        - name: SPRING_CLOUD_CONSUL_HOST
          value: "consul-server"
        - name: SPRING_REDIS_HOST
          value: "redis-cluster"
        - name: DMQ_JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: dmq-secrets
              key: jwt-secret
        ports:
        - containerPort: 8080
```

---

## 📊 Monitoring

### Metrics (Prometheus format)

```bash
# Gateway metrics
curl http://localhost:8080/actuator/prometheus

# Key metrics:
gateway.requests.total{route="producer-ingestion"}
gateway.requests.duration{route="producer-ingestion",quantile="0.99"}
gateway.circuit.breaker.state{name="producerCircuitBreaker"}
gateway.rate.limit.rejected{tier="standard"}
```

### Health Checks

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Detailed health
curl http://localhost:8080/actuator/health | jq

Response:
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"},
    "redis": {"status": "UP"},
    "consul": {"status": "UP"}
  }
}
```

---

## 🔧 Troubleshooting

### Issue 1: Rate Limit Not Working

**Symptom**: All requests allowed regardless of tier
**Cause**: Redis not connected
**Solution**:
```bash
# Check Redis
docker logs dmq-redis

# Test Redis connection
docker exec -it dmq-api-gateway redis-cli -h redis ping

# Expected: PONG
```

### Issue 2: JWT Validation Failing

**Symptom**: 401 Unauthorized for valid tokens
**Cause**: JWT secret mismatch or expired tokens
**Solution**:
```bash
# Check JWT secret environment variable
docker exec dmq-api-gateway env | grep DMQ_JWT_SECRET

# Validate token manually
curl -X POST http://localhost:8080/api/auth/validate \
  -H "Content-Type: application/json" \
  -d '{"token": "your-token-here"}'
```

### Issue 3: Circuit Breaker Always Open

**Symptom**: All requests return fallback
**Cause**: Downstream service unhealthy
**Solution**:
```bash
# Check circuit breaker state
curl http://localhost:8080/actuator/health | jq .components.circuitBreakers

# Check downstream service health via Consul
curl http://localhost:8500/v1/health/service/dmq-producer-ingestion
```

---

## 🎉 Features Summary

✅ **Spring Cloud Gateway** - Reactive, non-blocking routing  
✅ **JWT Authentication** - Token-based security with roles  
✅ **Redis Rate Limiting** - Distributed token bucket algorithm  
✅ **Circuit Breaker** - Resilience4j with automatic fallback  
✅ **Service Discovery** - Consul-based dynamic routing  
✅ **Load Balancing** - Round-robin across instances  
✅ **CORS Support** - Global CORS configuration  
✅ **Request Logging** - Comprehensive request/response logging  
✅ **Retry Logic** - Exponential backoff for transient failures  
✅ **Health Checks** - Actuator endpoints for monitoring  

---

**Version**: 1.0.0  
**Last Updated**: 2025-10-12
