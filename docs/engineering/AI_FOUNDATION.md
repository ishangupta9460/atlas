# AI foundation (setup only)

`Atlas backend -> AiService -> AiProvider -> Gemini HTTP API -> Java validation -> AiQuestion`

The provider owns the Gemini wire envelope. The service is the only intended entry
point for future callers. This package has no domain dependencies, repositories,
controllers, or state writes. `askQuestion` is an infrastructure proof shape, not
AI-003 onboarding or an AI-001 proposal contract. It accepts exactly the caller's
prompt; callers remain responsible for providing minimum necessary context.

## Configuration

All properties are under `atlas.ai` in application.yml:

| Property | Environment variable | Default / purpose |
| --- | --- | --- |
| enabled | ATLAS_AI_ENABLED | true |
| provider | none | gemini; only supported provider |
| api-key | ATLAS_AI_API_KEY | empty; supplied only by the process environment |
| model | ATLAS_AI_MODEL | gemini-3.5-flash-lite |
| base-url | none | https://generativelanguage.googleapis.com |
| connect-timeout-ms | none | 5000; positive |
| read-timeout-ms | none | 30000; positive |

Leave all AI variables unset to run unconfigured. The existing backend startup
command and database/JWT setup are unchanged (for local H2: `mvn spring-boot:run
-Plocal` from backend). No AI request happens at startup. Availability requires
enabled, a nonblank key, and provider gemini. Otherwise startup reports the safe
reason once and calls fail with NOT_CONFIGURED. Model and base URL must be
nonblank; timeouts must be positive. `unavailableReason()` is null when available.
A configured adapter is not a provider reachability or model-access check.

Never store real secrets in files, source, test resources, frontend, or logs,
including application-local.yml and application-aiven.yml. The key is sent only
in x-goog-api-key, never a URL. Redirects are disabled. Do not enable HTTP wire or
body logging. Prompt text and raw output are not logged; exceptions discard
unsafe underlying causes. Properties and content record toString methods omit
sensitive values. Service logs contain category, provider, model, status,
durationMs and retryable; existing MDC retains correlationId, also forwarded as
X-Correlation-ID. Status is `unreported` when unavailable from the minimal
provider-neutral result, including success and local validation failures.

Free-tier Gemini content may be used by Google to improve its products; send only
throwaway text during development. This is the engineering constraint supplied
in the setup brief, not a new product decision.

## Failures and output checks

| Category | Handling | Retryable metadata |
| --- | --- | --- |
| NOT_CONFIGURED | Disabled, missing key, unsupported provider | false |
| AUTHENTICATION | HTTP 401/403 | false |
| RATE_LIMITED | HTTP 429 | true |
| PROVIDER_REQUEST | Connection, timeout, IO failure | true |
| PROVIDER_RESPONSE | HTTP 5xx | true |
| PROVIDER_RESPONSE | Other non-2xx (including 400), empty/malformed envelope, blocked, non-STOP, missing text | false |
| VALIDATION | Invalid question JSON or shape | false |

No error body is parsed to distinguish invalid-key HTTP 400. Retryable is only
metadata: there are no automatic retries. Java validation requires one JSON
object with exactly type/question/reason strings, type equal to question,
nonblank content, and at most 2000 characters per string. Duplicate fields and
trailing JSON values are rejected too. The response schema is independently
validated; it is not trusted as enforcement.

## Verification and optional owner-run smoke test

Run `mvn -B -Datlas.ai.smoke=false test` from backend. Normal tests use
MockRestServiceServer bound to RestClient.Builder; no network or CI credential is
needed. The request factory is configured with timeouts before the mock replaces
it. When Boot supplies no RestClient.Builder bean, configuration constructs one
without adding a dependency. Existing H2 context tests remain unchanged.

The optional AiLiveSmokeTest requires BOTH ATLAS_AI_SMOKE=true and the JVM system
property atlas.ai.smoke=true, plus an externally provisioned ATLAS_AI_API_KEY.
It is also disabled when CI=true. The additional system-property gate prevents
an inherited smoke environment flag from causing network calls in ordinary runs.
The owner can explicitly run `mvn -Dtest=AiLiveSmokeTest -Datlas.ai.smoke=true test`
from backend after arranging those environment variables securely. It sends only
the throwaway Spring Boot question in the test source. Do not print the key or
full response. This test was not enabled or run during implementation.

Excluded: endpoints, frontend, domain integration, scheduling/planning behavior,
state changes, retries/backoff, quota tracking, caching, proposal schemas,
confidence banding, memory/preferences, and all Chunk 6 work. No dependency was
added. `.env.example` still omits ATLAS_JWT_SECRET as required by the brief.

## Engineering handoff

TASK: ATLAS-AI-FOUNDATION, owner-supplied setup brief; no Jira story completion claimed.
STATUS: Ready for human review. Independent review PASS after fixes; full backend
suite PASS. No stage, commit, push, or merge performed.
WHAT WAS DONE: Isolated Gemini adapter, validated configuration, strict question
validation, typed safe failures, startup/service logging, secret safeguards,
mocked tests and opt-in smoke test.
FILES CHANGED:
- `.gitignore`
- `.env.example`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/atlas/backend/ai/AiAuthenticationException.java`
- `backend/src/main/java/com/atlas/backend/ai/AiConfiguration.java`
- `backend/src/main/java/com/atlas/backend/ai/AiException.java`
- `backend/src/main/java/com/atlas/backend/ai/AiFailureCategory.java`
- `backend/src/main/java/com/atlas/backend/ai/AiNotConfiguredException.java`
- `backend/src/main/java/com/atlas/backend/ai/AiProperties.java`
- `backend/src/main/java/com/atlas/backend/ai/AiProvider.java`
- `backend/src/main/java/com/atlas/backend/ai/AiProviderRequestException.java`
- `backend/src/main/java/com/atlas/backend/ai/AiProviderResponseException.java`
- `backend/src/main/java/com/atlas/backend/ai/AiProviderResult.java`
- `backend/src/main/java/com/atlas/backend/ai/AiQuestion.java`
- `backend/src/main/java/com/atlas/backend/ai/AiQuestionValidator.java`
- `backend/src/main/java/com/atlas/backend/ai/AiRateLimitException.java`
- `backend/src/main/java/com/atlas/backend/ai/AiResponseValidationException.java`
- `backend/src/main/java/com/atlas/backend/ai/AiService.java`
- `backend/src/main/java/com/atlas/backend/ai/AiStartupReporter.java`
- `backend/src/main/java/com/atlas/backend/ai/AiStructuredRequest.java`
- `backend/src/main/java/com/atlas/backend/ai/GeminiProvider.java`
- `backend/src/test/java/com/atlas/backend/ai/AiLiveSmokeTest.java`
- `backend/src/test/java/com/atlas/backend/ai/AiPropertiesTest.java`
- `backend/src/test/java/com/atlas/backend/ai/AiServiceTest.java`
- `backend/src/test/java/com/atlas/backend/ai/GeminiProviderTest.java`
- `docs/engineering/AI_FOUNDATION.md`

DATABASE CHANGES: none.
API CHANGES: none.
TESTS RUN: `mvn -B -Dtest=AiPropertiesTest,GeminiProviderTest,AiServiceTest test`;
`mvn -B -Datlas.ai.smoke=false test` (full suite, run twice); `git diff --check`.
TEST RESULTS: Initial focused run: 55 passed. Final full suite: 417 total,
416 passed, zero failures/errors, one skipped (AiLiveSmokeTest). Includes all
358 pre-existing tests unchanged and 58 new automated tests. Final run output:
backend/target/ai-foundation-final.log. Independent review PASS after both fixes.
`git diff --check` passed; only normal LF/CRLF Git notices.
KNOWN FAILURES: None remaining. Initial full run: 414 tests, 215 errors, zero assertion failures,
one skipped; application contexts lacked a RestClient.Builder bean. Fixed with
an optional-builder fallback and regression test; final rerun passed. Independent
review also found response-body IO misclassification, now fixed and tested.
KNOWN RISKS: No live verification of the requested model, credentials, provider
availability, or free-tier access. Mock transport tests do not measure real
network timeout timing. No CI run or production database verification performed.
OPEN QUESTIONS: none blocking this authorized setup; supplied provider/topology
choices are not recorded as decisions by this implementation.
ARCHITECTURAL CONCERNS: Future callers need domain proposal validation and
minimum-context construction; this proof API must not become a state-write path.
NEXT STEP: Human review of the working-tree changes before any staging/commit/merge.
Stop here; do not start Chunk 6.
DO NOT REPEAT: Boot 4 web starter here does not auto-register a RestClient.Builder;
use the tested optional-builder fallback. JacksonIOException must be handled
before JacksonException to preserve retryable response-stream IO failures. Do not run the live test or inspect credentials during automated
verification. Do not implement Chunk 6. Do not modify Jira or numbered specs.

The handoff is embedded here to honor the brief's one-document limit. Proposed
owner decision-log entries (not written): record the supplied Gemini/provider
model choice and the in-process service to external-provider topology; record
the explicit separation of this setup from later AI/proposal/quota stories.

Safeguard evidence: `git ls-files "*.log" ".cursor"` returned no tracked files.
`git check-ignore -v .env .env.local backend/startup.log .cursor/x` matched
.gitignore lines 2, 3, 33, 34 respectively. `.env.example` check-ignore exit was 1
(not ignored). `rg -n '^import com\.atlas\.backend\.'` over the AI package
returned no matches. Credential-pattern scans (`AIza[0-9A-Za-z_-]{20,}` and
private-key headers) found no matches in the tracked diff or new files; no real
credential was read to perform the check. Local/Aiven profiles, pom.xml,
frontend and existing tests have no diff. Pre-existing untracked files were
untouched; logs and .cursor now disappear from ordinary status due to ignore
rules. All source edits are within backend/src, .gitignore, .env.example and
this one document; Maven generated only ignored build outputs under backend/target.
Branch stayed feature/ATLAS-AI-FOUNDATION at d4a18e5. Start had no tracked changes;
end has three modified tracked files plus the two new AI source/test directories
and this document. Other previously visible untracked documentation remains.
