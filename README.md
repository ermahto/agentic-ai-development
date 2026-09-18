```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "sessionId": "22222222-2222-2222-2222-222222222222",
  "answer": "..."
}
```
**Error responses**
| Status | When |
|---|---|
| `400` | Missing `question`, malformed JSON, or invalid UUID |
| `502` | ADK / Gemini orchestration failure |
| `500` | Unexpected server error |
---
## Test steps
Keep the app running (`mvn spring-boot:run`) in one terminal. Use a second terminal for the calls below.
### Test 1 — Validation (no LLM call)
Empty body / missing question should fail fast.
**PowerShell**
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/agents/interact `
  -ContentType "application/json" `
  -Body '{"question":""}'
```
**curl**
```bash
curl -s -X POST http://localhost:8080/api/agents/interact ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"\"}"
```
Expect HTTP **400** and a message that `question` is required.
### Test 2 — Non-Java question (orchestrator only)
The orchestrator should answer directly and **not** need the Java specialist.
**PowerShell**
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/agents/interact `
  -ContentType "application/json" `
  -Body '{"question":"What is the role of a project manager in a software team?"}'
```
Expect:
- HTTP **200**
- New `userId` and `sessionId`
- A managerial-style answer, not a Java class dump
### Test 3 — Java code generation (orchestrator + specialist)
This path should trigger `delegateToJavaSpecialist`.
**PowerShell**
```powershell
$response = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/agents/interact `
  -ContentType "application/json" `
  -Body '{"question":"Generate a production-ready Spring Boot REST controller for inventory items with Bean Validation and a record DTO."}'
$response | ConvertTo-Json -Depth 5
$response.userId
$response.sessionId
```
**curl**
```bash
curl -s -X POST http://localhost:8080/api/agents/interact ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"Generate a production-ready Spring Boot REST controller for inventory items with Bean Validation and a record DTO.\"}"
```
Expect:
- HTTP **200**
- `answer` containing Java/Spring code (controller, DTO, validation)
- Application logs similar to `Routing assignment to Java Specialist`
Save `userId` and `sessionId` for Test 4.
### Test 4 — Multi-turn session (conversation memory)
Reuse the IDs from Test 3.
**PowerShell**
```powershell
$body = @{
  userId    = $response.userId
  sessionId = $response.sessionId
  question  = "Now add a service layer and constructor injection for that controller."
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/agents/interact `
  -ContentType "application/json" `
  -Body $body | ConvertTo-Json -Depth 5
```
Expect:
- Same `userId` / `sessionId` echoed back
- Follow-up that refers to the inventory controller from the previous turn (not a brand-new unrelated API)
### Test 5 — Maven compile (no live LLM)
Does not call Gemini. Confirms the project still builds on JDK 17:
```bat
mvn -DskipTests compile
```
Expect `BUILD SUCCESS`.
There is no unit-test suite checked in yet; `spring-boot-starter-test` is on the classpath for future tests. Live agent checks are the HTTP steps above.
---
## Configuration reference
`src/main/resources/application.yml`
```yaml
agents:
  orchestrator:
    name: orchestrator_agent
    model: gemini-2.5-flash
    system-prompt: ...
  java-specialist:
    name: java_specialist_agent
    model: gemini-2.5-flash
    system-prompt: ...
```
Change models or prompts there without touching Java. Agent names must stay valid ADK identifiers (letters, digits, underscores).
Optional YAML fallback for the API key (prefer the environment variable):
```yaml
google:
  api-key: ${GOOGLE_API_KEY:}
```
---
## Troubleshooting
| Symptom | Likely cause | Fix |
|---|---|---|
| `class, interface, or enum expected` on `record` | Maven is using JDK 8 | Set `JAVA_HOME` to JDK 17 and confirm `mvn -version` |
| Enforcer: requires JDK 17+ | Same | Same |
| `GOOGLE_API_KEY is not set` / 502 from Gemini | Missing key | Export `GOOGLE_API_KEY` in the same shell that starts Spring Boot |
| 400 `question is required` | Empty or missing field | Send a non-blank `question` |
| Empty / generic answer | Gemini empty final event | Retry; check ADK logs |
| Slow first request | Cold start + two LLM hops | Normal when the specialist tool is invoked |
---
## License / keys
This sample is for development and integration of Google ADK in Spring Boot. Gemini usage is billed under your Google AI Studio / Cloud project. Keep API keys out of source control.
