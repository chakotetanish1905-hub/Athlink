# SupportSphere

SupportSphere is a support-ticket management platform with two roles, **Manager** and **Client**.

| Part | Tech | Port |
|------|------|------|
| `springapp/` | Java 17, Spring Boot 3.0.1, Spring Data JPA, Spring Security + JWT, Bean Validation, springdoc-openapi (Swagger) | **8080** |
| `angularapp/` | Angular 16, TypeScript, Reactive Forms, lazy-loaded feature modules, `jwt-decode` | **8081** |
| Database | MySQL, database **`appdb`** (created automatically) | 3306 |

---

## 1. Running the project

### Backend

```bash
cd springapp
mvn spring-boot:run
```

MySQL settings (`springapp/src/main/resources/application.properties`):

| Property | Default | Override with env var |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/appdb?createDatabaseIfNotExist=true...` | — |
| `spring.datasource.username` | `root` | `DB_USERNAME` |
| `spring.datasource.password` | `examly` | `DB_PASSWORD` |
| `jwt.secret` | dev-only Base64 key | `JWT_SECRET` (Base64, ≥ 256 bits) |
| `jwt.expiration` | `86400000` (24 h, ms) | `JWT_EXPIRATION` |
| `app.cors.allowed-origins` | `http://localhost:8081` | `CORS_ALLOWED_ORIGINS` |
| `app.security.public-read-endpoints` | `false` | `PUBLIC_READ_ENDPOINTS` |

Tables are created by Hibernate (`ddl-auto=update`): `users`, `ticket`, `support_agent`, `feedback`, `ErrorLogs`.

To work with MySQL in the workspace: `mysql -u root --protocol=tcp -p` (password `examly`).

> **Auto-evaluation note (SRS "Platform Prerequisites")** – if the evaluation platform needs `GET /api/ticket`
> and `GET /api/feedback` without a token, start the backend with `PUBLIC_READ_ENDPOINTS=true`.
> Keep it `false` for normal, secure use.

Generate a production secret, for example: `openssl rand -base64 48`.

### Frontend

```bash
nvm use 20
cd angularapp
npm install
ng serve --port 8081        # or: npx ng serve (port 8081 is already set in angular.json)
```

The backend URL is set once, in `angularapp/src/environments/environment*.ts` (`apiBaseUrl`). In a hosted
workspace, replace `http://localhost:8080` with that workspace's port-8080 URL.

**Images:** copy `background.webp` (login/signup) and `homepage.webp` (home) from the SRS asset pack into
`angularapp/src/assets/images/`. Until then a matching gradient is shown.

### Tests

```bash
cd springapp  && mvn test                                          # JUnit + MockMvc (H2 in-memory, no MySQL needed)
cd angularapp && npx ng test --watch=false --browsers=ChromeHeadlessCI   # Jasmine/Karma
```

---

## 2. Roles

| Role (DB value) | Spring authority | Can do |
|---|---|---|
| `Manager` | `ROLE_MANAGER` | Dashboard, add/edit/delete/toggle support agents, view all tickets, assign agents, close resolved tickets, view all feedback |
| `Client` | `ROLE_CLIENT` | Create/edit/delete own open tickets, view own tickets, provide resolution summary + mark resolved, view agents who worked their tickets, post/view/delete own feedback |

The database stores `Manager` / `Client`; `UserPrinciple` maps it to `ROLE_MANAGER` / `ROLE_CLIENT`.

---

## 3. Security design

### 3.1 Registration
```
POST /api/register → @Valid UserRequestDTO → duplicate-email check (409)
  → passwordEncoder.encode(password) (BCrypt) → UserRepo.save() → 201 Created (user without password)
```

### 3.2 Login – DAO authentication
```
POST /api/login {email, password}
  → UserServiceImpl builds UsernamePasswordAuthenticationToken(email, password)
  → AuthenticationManager (ProviderManager)
  → DaoAuthenticationProvider
  → MyUserDetailsService.loadUserByUsername(email) → UserRepo.findByEmail()
  → PasswordEncoder.matches(raw, bcryptHash)
  → authenticated Authentication (principal = UserPrinciple)
  → JwtUtils.generateToken() → 201 Created LoginDTO {token, username, userRole, userId}
```
Wrong email or password → `401 {"message":"Invalid email or password"}`. Passwords are never compared with `equals`.

### 3.3 Every protected request – JWT filter
```
Authorization: Bearer <jwt>
  → JwtAuthenticationFilter (OncePerRequestFilter)
  → JwtUtils.validateToken() (signature + expiry)
  → MyUserDetailsService → UserDetails
  → SecurityContextHolder (current request only; sessions are STATELESS)
  → URL role rules in SecurityConfig (hasRole) → 403 via JwtAccessDeniedHandler
  → controller → service ownership check (authenticated userId vs resource owner) → 403
```
No / invalid / expired token on a protected URL → `JwtAuthenticationEntryPoint` → `401` (the controller is never reached).

**JWT claims:** `sub` (email), `userId`, `role`, `username`, `iat`, `exp`.

**CSRF** is disabled on purpose: the API is stateless, the JWT travels in the `Authorization` header and there is no
authentication cookie or server session, so a forged cross-site request has no ambient credential to ride on.

**Resource ownership:** a Client can only read/modify their own tickets and feedback. `GET /api/ticket/user/5` with a
token for user 10 returns `403`, even though the URL role rule allows Clients. Managers have the broader access the SRS
grants them.

### 3.4 Frontend
* `TokenService` is the only code that stores and decodes the JWT (`jwt-decode`).
* `AuthService` (`register`, `login`, `logout`, `isLoggedIn`, `getToken`, `getUserRole`, `getUserId`) publishes state
  through `BehaviorSubject`s; the role and id are read from the token claims.
* `AuthInterceptor` is the **only** place that adds `Authorization: Bearer …` (never on login/register). It handles
  `401` (clears the session and redirects to `/login`), `403`, `5xx` and network errors centrally.
* `AuthGuard` (`CanActivate`, in `components/authguard/`) redirects anonymous users to `/login` and wrong-role users
  to `/error/403` using route data `{ role: 'Manager' | 'Client' }`. This is navigation convenience only; the backend
  always enforces JWT + role + ownership.

---

## 4. API endpoints

| Method | URL | Role | Success | Errors |
|---|---|---|---|---|
| POST | `/api/register` | All | 201 user | 400, 409 |
| POST | `/api/login` | All | 201 LoginDTO | 400, 401 |
| POST | `/api/ticket` | Client | 201 ticket | 400, 401, 403, 409 |
| GET | `/api/ticket/{ticketId}` | Client (owner) | 200 | 401, 403, 404 |
| GET | `/api/ticket` | Manager, Client* | 200 / 204 | 401 |
| PUT | `/api/ticket/{ticketId}` | Manager, Client (owner) | 200 | 400, 401, 403, 404, 409 |
| DELETE | `/api/ticket/{ticketId}` | Client (owner) | 200 deleted ticket | 401, 403, 404, 409 |
| GET | `/api/ticket/user/{userId}` | Client (own id) | 200 | 401, 403, 404 |
| GET | `/api/ticket/agent/{agentId}` | Client | 200 (own tickets for that agent) | 401, 403, 404 |
| POST | `/api/supportAgent` | Manager | 201 | 400, 401, 403, 409 |
| GET | `/api/supportAgent/{agentId}` | Manager, Client | 200 | 401, 404 |
| GET | `/api/supportAgent` | Manager | 200 / 204 | 401, 403 |
| PUT | `/api/supportAgent/{agentId}` | Manager | 200 | 400, 401, 403, 404, 409 |
| DELETE | `/api/supportAgent/{agentId}` | Manager | 200 deleted agent | 401, 403, 404, 409 |
| POST | `/api/feedback` | Client | 201 | 400, 401, 403, 404, 409 |
| GET | `/api/feedback/{feedbackId}` | Manager, Client (owner) | 200 | 401, 403, 404 |
| GET | `/api/feedback` | Manager, Client* | 200 / 204 | 401 |
| GET | `/api/feedback/user/{userId}` | Client (own id) | 200 / 204 | 401, 403, 404 |
| DELETE | `/api/feedback/{feedbackId}` | Client (owner) | 200 deleted feedback | 401, 403, 404 |

\* A Client calling the "all" endpoints receives only their own records.

Every error uses one body shape (no stack traces), and is also saved in the `ErrorLogs` table:
```json
{ "timestamp": "...", "status": 409, "error": "Conflict",
  "message": "A ticket with this title already exists", "path": "/api/ticket" }
```
Validation failures add `"validationErrors": { "field": "message" }`.

### Business rules (in the services)
* New tickets always start `Open`, unassigned, `createdDate = today`.
* Ticket title must be unique per client (409).
* Status `Resolved` requires a non-blank `resolutionSummary`; `resolutionDate` is set then and cannot be in the future
  or before `createdDate`.
* Only a Manager assigns agents (agent must be `Available`) and closes tickets; only a `Resolved` ticket can be closed.
* A Client can edit/delete a ticket only while it is `Open` and unassigned.
* Feedback: rating 1–5, only for the client's own `Resolved`/`Closed` ticket, one per ticket (409).
* An agent with tickets or feedback cannot be deleted (`AgentDeletionException`, 409). Duplicate agent email/phone → 409.

---

## 5. Swagger

* UI: <http://localhost:8080/swagger-ui.html>  ·  Spec: <http://localhost:8080/v3/api-docs>
* Click **Authorize**, paste the `token` from `/api/login` (without the `Bearer ` prefix).
* Configuration lives only in `config/SwaggerConfig.java` (bearer scheme, tags, common 400/401/403/500 responses);
  each endpoint documents its summary, description, parameters, request body and status codes.

---

## 6. Testing authentication & authorization by hand

```bash
API=http://localhost:8080

# register a manager and two clients
curl -i -X POST $API/api/register -H 'Content-Type: application/json' \
  -d '{"email":"manager@test.com","password":"Password1","username":"demomanager","mobileNumber":"9876543210","userRole":"Manager"}'   # 201
curl -i -X POST $API/api/register -H 'Content-Type: application/json' \
  -d '{"email":"alice@test.com","password":"Password1","username":"alice","mobileNumber":"9876543210","userRole":"Client"}'          # 201
curl -i -X POST $API/api/register -H 'Content-Type: application/json' \
  -d '{"email":"bob@test.com","password":"Password1","username":"bob","mobileNumber":"9876543210","userRole":"Client"}'              # 201
# same email again → 409 "A user with this email already exists"

# login
curl -s -X POST $API/api/login -H 'Content-Type: application/json' -d '{"email":"alice@test.com","password":"Password1"}'
# → 201 {"token":"...","username":"alice","userRole":"Client","userId":2}
curl -i -X POST $API/api/login -H 'Content-Type: application/json' -d '{"email":"alice@test.com","password":"nope12345"}'   # 401

ALICE=<alice token>; BOB=<bob token>; MANAGER=<manager token>

curl -i $API/api/ticket                                          # 401 no token
curl -i $API/api/ticket -H 'Authorization: Bearer garbage'       # 401 invalid token
curl -i -X POST $API/api/ticket -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' \
  -d '{"title":"VPN down","description":"Cannot connect","priority":"High","issueCategory":"Connectivity"}'    # 201, status Open
curl -i -X POST $API/api/ticket -H "Authorization: Bearer $MANAGER" -H 'Content-Type: application/json' \
  -d '{"title":"x","description":"x","priority":"Low","issueCategory":"General"}'                              # 403 wrong role
curl -i $API/api/ticket/user/2 -H "Authorization: Bearer $BOB"   # 403 Bob reading Alice's tickets
curl -i $API/api/supportAgent -H "Authorization: Bearer $ALICE"  # 403 client on manager endpoint
curl -i $API/api/ticket/9999 -H "Authorization: Bearer $ALICE"   # 404
```
An expired token returns `401 "JWT token has expired. Please login again."` (set `JWT_EXPIRATION=5000`, wait, retry).

The same matrix is automated in `springapp/src/test/java/com/examly/springapp/controller/SecurityIntegrationTest.java`.

---

## 7. Project structure

```
springapp/src/main/java/com/examly/springapp/
├── config/       SecurityConfig, CorsConfig (WebMvcConfigurer), JwtUtils, JwtAuthenticationFilter,
│                 JwtAuthenticationEntryPoint, JwtAccessDeniedHandler, MyUserDetailsService, UserPrinciple, SwaggerConfig
├── controller/   AuthController, TicketController, SupportAgentController, FeedbackController
├── exceptions/   SupportSphereException ← ResourceNotFound, DuplicateResource ← (DuplicateAgent, DuplicateTicket),
│                 AgentDeletion, TicketDeletion, Validation, ForbiddenOperation; GlobalExceptionHandler
├── model/        User, Ticket, SupportAgent, Feedback, ErrorLog (entities) + request/response DTOs, LoginDTO
├── repository/   UserRepo, TicketRepo, SupportAgentRepo, FeedbackRepo, ErrorLogRepo
├── service/      *Service interfaces + *ServiceImpl, CurrentUserService, ErrorLogService
└── SpringappApplication.java

angularapp/src/
├── apiconfig.ts                 backend base URL (from environment)
├── environments/                environment.ts / .development.ts / .production.ts
└── app/
    ├── components/              login, signup, home-page, managernav, clientnav, manager-dashboard,
    │                            manager-view-agents, manager-view-tickets, managerviewfeedback,
    │                            support-agent-management, client-view-tickets, ticket-management,
    │                            ticket-details, supported-agents, clientpostfeedback, clientviewfeedback,
    │                            authguard, error, confirm-dialog, message-dialog, toast
    ├── constants/constant.ts    every API URL + dropdown values
    ├── features/                auth / manager / client lazy-loaded modules (routes)
    ├── interceptors/            auth.interceptor.ts
    ├── models/                  user, login, ticket, support-agent, feedback (interfaces)
    ├── services/                auth, token, ticket, support-agent, feedback, notification, error-handler
    └── shared/shared.module.ts
```

### Routes
| Path | Who | Component |
|---|---|---|
| `/login`, `/signup` | public | login, signup |
| `/home` | logged in | home-page |
| `/manager/dashboard` · `/manager/agents` · `/manager/agents/add` · `/manager/agents/edit/:id` · `/manager/tickets` · `/manager/feedbacks` | Manager | dashboard, agents, agent form, tickets, feedback |
| `/client/tickets` · `/client/tickets/add` · `/client/tickets/edit/:id` · `/client/tickets/:id` · `/client/agents` · `/client/feedback/add/:ticketId` · `/client/feedbacks` | Client | tickets, ticket form, ticket details, supported agents, add feedback, my feedback |
| `/error/:code`, `**` | all | error |

---

## 8. Phase 2 (not included)

The optional Gemini FAQ chatbot from the SRS appendix is intentionally **not** implemented in this phase; the core
application does not depend on it.
