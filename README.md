# SupportSphere

SupportSphere is a support-ticket platform with two roles, **Manager** and **Client**.
Clients raise tickets, managers assign support agents, clients confirm the fix and leave feedback.

| Part | Tech | Port |
|------|------|------|
| `springapp/` | Java 17, Spring Boot 3.0.1, Spring Data JPA, Spring Security + JWT, Bean Validation | **8080** |
| `angularapp/` | Angular 16, TypeScript, template-driven forms | **8081** |
| Database | MySQL, database **`appdb`** (created automatically) | 3306 |
| AI (Phase 2, optional) | Google Gemini API via Java `HttpClient` | - |

---

## 1. Running the project

### Backend

```bash
cd springapp
mvn spring-boot:run
```

MySQL settings live in `springapp/src/main/resources/application.properties`
(user `root`, password `examly`, or set `DB_USERNAME` / `DB_PASSWORD`).
Hibernate creates the tables: `users`, `ticket`, `support_agent`, `feedback`, `ErrorLogs`, `faqs`, `chat_messages`.

| Property | Default | Environment variable |
|---|---|---|
| `jwt.secret` | development-only Base64 key | `JWT_SECRET` (Base64, at least 256 bits) |
| `jwt.expiration` | `86400000` (24 h) | `JWT_EXPIRATION` |
| `app.cors.allowed-origins` | `http://localhost:8081` | `CORS_ALLOWED_ORIGINS` |
| `app.security.public-read-endpoints` | `false` | `PUBLIC_READ_ENDPOINTS` |

> SRS "Platform Prerequisites": if the evaluation platform must call `GET /api/ticket` and
> `GET /api/feedback` without a token, start the backend with `PUBLIC_READ_ENDPOINTS=true`.
> Keep it `false` for normal use.

### Frontend

```bash
cd angularapp
npm install
npm start            # ng serve on port 8081
```

The backend URL is in `angularapp/src/environments/environment*.ts` (`apiBaseUrl`). In a hosted workspace,
replace `http://localhost:8080` with the workspace's port-8080 URL.

### Tests

```bash
cd springapp  && mvn clean test                                   # JUnit + MockMvc, H2 in-memory (no MySQL needed)
cd angularapp && npm run build
cd angularapp && npx ng test --watch=false --browsers=ChromeHeadlessCI
```

---

## 2. Backend structure

```
springapp/src/main/java/com/examly/springapp
├── config
│   ├── CorsConfig.java                 lets the Angular app call the API
│   ├── SecurityConfig.java             PasswordEncoder, DaoAuthenticationProvider, AuthenticationManager, URL role rules
│   ├── JwtAuthenticationFilter.java    checks "Authorization: Bearer <token>" on every request
│   ├── JwtAuthenticationEntryPoint.java  401 when the token is missing / invalid
│   ├── JwtAccessDeniedHandler.java     403 when the role is not allowed
│   ├── JwtUtils.java                   creates and validates the JWT
│   ├── MyUserDetailsService.java       loads the user from the database
│   └── UserPrinciple.java              the logged-in user (role -> ROLE_MANAGER / ROLE_CLIENT)
├── controller    AuthController, TicketController, SupportAgentController, FeedbackController
├── exceptions    AgentDeletionException, DuplicateAgentException, DuplicateTicketException,
│                 TicketDeletionException, GlobalExceptionHandler
├── model         User, Ticket, SupportAgent, Feedback, ErrorLog, LoginDTO
├── repository    UserRepo, TicketRepo, SupportAgentRepo, FeedbackRepo, ErrorLogRepo
├── service       UserService, TicketService, SupportAgentService, FeedbackService
│   └── impl      UserServiceImpl, TicketServiceImpl, SupportAgentServiceImpl, FeedbackServiceImpl
└── SpringappApplication.java
```

* **DTOs:** only `LoginDTO` (required by the SRS). Controllers use the entities directly.
  `User.password` is write-only in JSON, so it is accepted on register/login but never returned.
* **Exceptions:** the four SRS exceptions plus standard Java exceptions, all handled in `GlobalExceptionHandler`:
  400 validation / `IllegalArgumentException`, 401 wrong credentials, 403 `AccessDeniedException`,
  404 `NoSuchElementException`, 409 duplicates and blocked deletes, 500 anything else.
  Every handled error is saved in the **`ErrorLogs`** table.

---

## 3. Security

### Login - DAO authentication (from the Spring Security PPT)

```
POST /api/login {email, password}
  -> UserServiceImpl creates UsernamePasswordAuthenticationToken(email, password)
  -> AuthenticationManager
  -> DaoAuthenticationProvider
  -> MyUserDetailsService.loadUserByUsername(email) -> UserRepo.findByEmail()
  -> PasswordEncoder.matches(raw password, BCrypt hash)
  -> success: JwtUtils.generateToken() -> 201 LoginDTO {token, username, userRole, userId}
  -> failure: BadCredentialsException -> 401 "Invalid email or password"
```

Registration stores `passwordEncoder.encode(password)` - plain passwords are never stored or compared with `equals`.

### Every other request

```
Authorization: Bearer <JWT>
  -> JwtAuthenticationFilter -> JwtUtils.validateToken() -> MyUserDetailsService
  -> SecurityContext (this request only - SessionCreationPolicy.STATELESS)
  -> URL role rules in SecurityConfig (hasRole MANAGER / CLIENT)
  -> controller ownership checks (@AuthenticationPrincipal)
```

* CSRF is disabled because the API is stateless and uses a header token, not cookies.
* **Ownership:** a Client can only read or change their own tickets and feedback. The `userId` in a URL is
  never trusted - `GET /api/ticket/user/5` with another client's token returns **403**.

### Frontend

* `AuthService` stores the LoginDTO values in `localStorage` and exposes the role and id as `BehaviorSubject`s.
* `AuthInterceptor` is the only place that adds `Authorization: Bearer ...`; on 401 it logs out and opens `/login`.
* `AuthGuard` (`CanActivate`) sends anonymous users to `/login` and wrong-role users to the 404 page.
  The backend still checks every request.

---

## 4. API

| Method | URL | Role | Success |
|---|---|---|---|
| POST | `/api/register` | All | 201 user (409 duplicate email) |
| POST | `/api/login` | All | 201 LoginDTO (401 invalid credentials) |
| POST | `/api/ticket` | Client | 201 ticket (403 Manager, 409 duplicate title) |
| GET | `/api/ticket/{ticketId}` | Client (owner) | 200 (403 Manager, 404) |
| GET | `/api/ticket` | Manager, Client* | 200 / 204 |
| PUT | `/api/ticket/{ticketId}` | Manager, Client (owner) | 200 (404) |
| DELETE | `/api/ticket/{ticketId}` | Client (owner) | 200 deleted ticket (403 Manager, 404) |
| GET | `/api/ticket/user/{userId}` | Client (own id) | 200 (403 Manager, 404) |
| GET | `/api/ticket/agent/{agentId}` | Client | 200 - the client's tickets handled by the agent |
| POST | `/api/supportAgent` | Manager | 201 (403 Client, 409 duplicate email) |
| GET | `/api/supportAgent/{agentId}` | Manager, Client | 200 (404) |
| GET | `/api/supportAgent` | Manager | 200 / 204 (403 Client) |
| PUT | `/api/supportAgent/{agentId}` | Manager | 200 (403 Client, 404) |
| DELETE | `/api/supportAgent/{agentId}` | Manager | 200 deleted agent (403 Client, 404) |
| POST | `/api/feedback` | Client | 201 (403 Manager, 409 already reviewed) |
| GET | `/api/feedback/{feedbackId}` | Manager, Client (owner) | 200 (404) |
| GET | `/api/feedback` | Manager, Client* | 200 / 204 |
| GET | `/api/feedback/user/{userId}` | Client (own id) | 200 / 204 (403 Manager) |
| DELETE | `/api/feedback/{feedbackId}` | Client (owner) | 200 deleted feedback (403 Manager, 404) |

\* A Client calling the "all" endpoints receives only their own records.

Error body: `{ "timestamp", "status", "error", "message", "path" }` (+ `"errors"` per field for validation).

### Ticket lifecycle (business rules in `TicketServiceImpl`)

1. **Open** - created by a Client (status, dates and agent are always set by the server).
   The client can edit or delete it while it is Open and no agent is assigned (`TicketDeletionException` otherwise).
2. **Agent assigned** - the Manager assigns an **Available** agent; the status stays Open
   (the SRS shows "Agent Assigned" for an Open ticket with an agent).
3. **Resolved** - the Client adds a resolution summary + satisfaction, then marks it Resolved.
   Without a summary: 400 "Please provide resolution details before marking resolve."
4. **Closed** - only the Manager, and only from Resolved.

Feedback: rating 1-5, only for the client's own Resolved/Closed ticket, one per ticket.
An agent who has worked on tickets cannot be deleted (`AgentDeletionException`) - mark them Unavailable instead.

---

## 5. Frontend

```
angularapp/src/app
├── components
│   ├── authguard/            AuthGuard (CanActivate)
│   ├── login/ signup/        auth screens
│   ├── managernav/ clientnav/  role-based sidebar + mobile bottom bar
│   ├── home-page/            Home (both roles)
│   ├── manager-dashboard/ manager-view-tickets/ manager-view-agents/
│   │   support-agent-management/ managerviewfeedback/
│   ├── client-view-tickets/ ticket-management/ ticket-details/
│   │   supported-agents/ clientpostfeedback/ clientviewfeedback/
│   ├── error/                404 and "Something Went Wrong" pages
│   └── ui-helpers.ts         status / priority classes, avatars, date formatting
├── interceptors/auth.interceptor.ts
├── models/                   user, login, ticket, support-agent, feedback
├── services/                 auth, ticket, support-agent, feedback
├── app-routing.module.ts
└── app.module.ts             one module, no lazy loading
```

The visual design follows the supplied **Angular UI** reference. `src/styles.css` is the design system from that
reference (same colour tokens, Geist / Geist Mono typography, 6/8/12 px radii, shadows, buttons, badges,
priority bars, availability dots, tables, cards, modals, toasts, empty / skeleton / error states and the
desktop / tablet / phone breakpoints). Every screen uses real API data - nothing is mocked.

| Route | Role | Screen |
|---|---|---|
| `/login`, `/signup` | public | Login, Sign up |
| `/home` | both | Home |
| `/manager/dashboard`, `/manager/tickets`, `/manager/tickets/:id` | Manager | Dashboard, Tickets (assign / close), Ticket detail |
| `/manager/agents`, `/manager/agents/add`, `/manager/agents/edit/:id` | Manager | Support agents, Add / Edit agent |
| `/manager/feedbacks` | Manager | Feedback |
| `/client/tickets`, `/client/tickets/add`, `/client/tickets/edit/:id`, `/client/tickets/:id` | Client | Your tickets, Create / Edit ticket, Ticket detail |
| `/client/agents` | Client | View agents + Tickets worked |
| `/client/feedback/add`, `/client/feedbacks` | Client | Add feedback, My feedback |
| `/error`, `**` | all | Error pages |

### Deliberately not included

These appear in the UI mockup but are not part of the SRS functionality, so they were left out instead of being
shown as buttons that do nothing: notifications, settings, profile editing, forgot password and the prototype's
demo-account controls.

---

## 6. Phase 2 - AI FAQ chatbot

A floating **SupportSphere AI** widget (bottom-right, on every page, also before login) answers questions about
tickets, support agents, feedback and account actions **from the FAQ knowledge base only**. It never performs actions
(it cannot create, edit or delete anything).

### How it works

```
POST /api/chat {message, sessionId?}
  -> ChatService
       1. ConversationMemory: last 8 turns of the session
       2. follow-up -> standalone question ("What about editing it?" -> about tickets)
            Gemini enabled: Gemini rewrites it   |   no key: borrow the topic of the previous question
       3. FaqService.findBestMatch(question, embedding)
            Gemini enabled: cosine similarity with the FAQ embeddings (threshold 0.65)   -> source "semantic"
            no key / Gemini error: Jaccard word overlap with the FAQ questions (0.08)     -> source "lexical"
       4. reply: matched -> Gemini answer grounded in that FAQ (or the FAQ answer itself without Gemini)
                 no match -> "I couldn't find that in the SupportSphere FAQs ..."
       5. turn saved in ConversationMemory and in the chat_messages table
  -> ChatResponse {reply, matched, matchedQuestion, category, confidence, source, sessionId, resolvedQuestion}
```

* `faqs.json` (15 FAQs) is seeded into the `faqs` table on startup **once**, with embeddings when Gemini is enabled,
  then loaded into memory. FAQs seeded without a key get their embedding the first time the app starts with one.
* Gemini is called with Java's built-in `HttpClient` and Jackson - no SDK, Spring AI or LangChain.
  The API key is sent in the `x-goog-api-key` header and is never logged or stored.
* Embeddings are stored as text through `EmbeddingConverter` (JPA `AttributeConverter`) and are never returned by the API.

### Configuration

| Property | Default |
|---|---|
| `gemini.api.key` | `${GEMINI_API_KEY:}` - empty means lexical fallback |
| `gemini.embedding.model` | `gemini-embedding-2` |
| `gemini.generation.model` | `gemini-2.5-flash` |
| `chatbot.similarity.threshold` | `0.65` |
| `chatbot.lexical.threshold` | `0.08` |

```bash
export GEMINI_API_KEY=<your key>     # optional
cd springapp && mvn spring-boot:run
```

### Endpoints (all public - `/api/chat`, `/api/chat/**` and `/api/faqs` are in `permitAll()`)

| Method | URL | Response |
|---|---|---|
| POST | `/api/chat` | 200 ChatResponse (400 for an empty message) |
| GET | `/api/chat/history/{sessionId}` | 200 saved transcript (`List<ChatMessage>`), oldest first |
| DELETE | `/api/chat/memory/{sessionId}` | 204 - clears the short-term memory only (the transcript is kept) |
| GET | `/api/faqs` | 200 all FAQs, without embedding vectors |

The existing JWT security, roles and ownership checks are unchanged.

### Code

```
springapp
├── controller/ChatController.java
├── model/        FaqEntity (faqs), ChatMessage (chat_messages), Faq (faqs.json), EmbeddingConverter,
│                 ChatRequest, ChatResponse
├── repository/   FaqRepository, ChatMessageRepository
├── service/      GeminiService, FaqService, ConversationMemory, ChatService
└── resources/faqs.json

angularapp/src/app
├── components/chatbot/   floating widget, mounted once in app.component.html
├── models/chat.model.ts  ChatRequest, ChatResponse, ChatBubble
└── services/chat.service.ts  sendMessage(request), clearMemory(sessionId)
```

The widget keeps the `sessionId` in `sessionStorage` so follow-up questions keep their memory, shows the matched FAQ,
confidence and source (Semantic FAQ / Offline FAQ) under each answer, and **Clear** calls
`DELETE /api/chat/memory/{sessionId}` and starts a new conversation.
