# Research: Calling an arbitrary OpenAI-compatible endpoint for strict structured JSON (Android/Kotlin)

- **Query**: How should an Android app call an arbitrary, user-configured OpenAI-compatible endpoint and reliably get back strict structured JSON, degrading gracefully?
- **Scope**: external (server behaviour) + internal (repo constraints, existing deps)
- **Date**: 2026-09-28
- **Author**: trellis-research sub-agent

> Verification note: outbound network is unavailable in this environment (`curl` to docs hosts returns proxy `403`/no route), so server-behaviour claims below are from the official docs cited per row and from documented release notes, not re-fetched live. **Every row's behaviour is version-sensitive** — treat the cited URLs as the source of truth and re-check the installed server version before hard-coding a capability assumption. Claims I am not fully certain about are marked `(uncertain)`.

---

## 0. Internal context (constraints that shape the answer)

| Fact | Source |
|---|---|
| Target: `POST {baseUrl}/chat/completions`, strict JSON with `amountCents`, `type`, `categoryId`, `accountId`, `occurredAt`, `title`, `remark`, `confidence`; "用 JSON Schema 约束 + 解析失败重试一次" | `docs/ARCHITECTURE.md` §6 (lines 92–106) |
| Three-level fallback: rule layer (offline) → on-device classifier → cloud LLM (opt-in, default off); no level may throw to UI | `docs/ARCHITECTURE.md` §6, `.trellis/spec/android/ai-ml.md` |
| Network stack: OkHttp 4.12.0 (hand-written WebDAV client). No new third-party SDKs policy (§8 reasoning for WebDAV). | `docs/ARCHITECTURE.md` §8; `gradle/libs.versions.toml:17` |
| `:core:data` already depends on `okhttp`, `kotlinx-serialization-json`, `kotlinx-datetime`; `MockWebServer` is a test dep; `org.json` is used for WebDAV + snapshots and is part of the Android platform. | `core/data/build.gradle.kts:41-53`; `WebDavClient.kt`, `SnapshotJsonCodec.kt`, `EncryptedSettingsStore.kt` |
| `Money` is `@JvmInline value class Money(val cents: Long)`; use `Money.ofCents(Long)`. Amounts never use floating point. | `core/model/.../money/Money.kt:13,94` |
| `TransactionType { EXPENSE, INCOME, TRANSFER, DEBT_REPAY, ADJUST }`; `Txn.categoryId: String?`, `accountId: String`, `occurredAt: Long`, `title/remark: String`. | `core/model/.../LedgerEnums.kt:4`; `core/domain/.../model/Txn.kt` |
| Errors modelled as `AppResult.Failure(AppError)` with `AppError.Parse / Network / Auth / Validation`; no exceptions to UI. | `.trellis/spec/android/error-handling.md` |
| Secrets (AI `apiKey`) encrypted via AndroidKeyStore + AES/GCM into `shared_prefs` — pattern already exists in `EncryptedSettingsStore`. | `core/data/.../backup/EncryptedSettingsStore.kt:13-54`; ARCHITECTURE §10 |
| Seam: `:feature:ai` currently only depends on `:core:designsystem/domain/model/ml:classifier` — it does **not** depend on `:core:data`, so the HTTP client must live in `:core:data` and be reached via a `:core:domain` interface. | `feature/ai/build.gradle.kts:29-39` |

---

## 1. Structured-output mechanisms across OpenAI-compatible servers

The three mechanisms in increasing strength:

1. **Prompt-only** — system prompt says "return only a JSON object with these fields". No API-level guarantee; the model may add prose or fences.
2. **JSON mode** — `"response_format": {"type": "json_object"}`. Guarantees *syntactically valid JSON*, **not** schema conformance (any JSON value, any keys).
3. **Structured Outputs / strict schema** — `"response_format": {"type": "json_schema", "json_schema": {"name": ..., "strict": true, "schema": {...}}}`. Server-side constrained decoding (or grammar) guarantees schema conformance.
4. **Forced tool call** — one function whose `parameters` are the schema, `tool_choice: {"type":"function","function":{"name":"..."}}`. On servers that implement tool calling but *not* `json_schema`, this is the strongest available guarantee.

### 1.1 Support matrix (as commonly deployed)

Legend: **Y** = documented supported; **Y\*** = supported in recent versions only; **~** = accepted but silently ignored / best-effort; **N** = not documented / rejected; **400** = unsupported value produces an HTTP 4xx.

| Server | prompt-only | `json_object` | `json_schema` strict | forced tool | Failure mode when unsupported | Cite |
|---|---|---|---|---|---|---|
| OpenAI (`api.openai.com`) | Y | Y (needs literal "JSON" in messages; gpt-4-1106+/3.5-1106+) | Y (gpt-4o-2024-08-06+; `strict:true`, subset of JSON Schema) | Y | `json_schema` on an unsupported model → **400** `invalid_request_error`; `json_object` without "json" in prompt → **400** | https://platform.openai.com/docs/guides/structured-outputs , https://platform.openai.com/docs/api-reference/chat/create (see `response_format`) |
| Azure OpenAI | Y | Y | Y\* (needs api-version ≥ `2024-08-01-preview` and a compatible deployment) | Y | Older `api-version` rejects/ignores newer `response_format` values | https://learn.microsoft.com/azure/ai-services/openai/how-to/structured-outputs , https://learn.microsoft.com/azure/ai-services/openai/api-version-deprecation |
| Ollama (`/v1/chat/completions`) | Y | Y | Y\* (0.5.x+; native `/api/chat` uses `format: <schema>`) | Y\* (recent) | Older builds ignore unknown `response_format` or error; native API uses `format` not `response_format` | https://github.com/ollama/ollama/blob/main/docs/openai.md , https://github.com/ollama/ollama/blob/main/docs/api.md |
| vLLM (`vllm serve`) | Y | Y | Y\* via guided decoding (xgrammar/outlines); `guided_json` is the older param | Y\* | Unsupported schema → 400; older `guided_json` path must be used instead | https://docs.vllm.ai/en/latest/serving/openai_compatible_server.html , https://docs.vllm.ai/en/latest/features/structured_outputs.html |
| LM Studio | Y | Y | Y\* (recent; JSON schema via `response_format`) | Y\* | Older builds ignore `response_format` | https://lmstudio.ai/docs/app/api/structured-output |
| llama.cpp `llama-server` | Y | Y | Y via GBNF grammar (`json_schema` → grammar) | Y\* | Grammar build can reject unsupported schema keywords | https://github.com/ggml-org/llama.cpp/blob/master/examples/server/README.md |
| DeepSeek (`api.deepseek.com`) | Y | Y (requires "json" in prompt; recommend generous `max_tokens`; empty content can raise an error) | N (only JSON Output / `json_object` is documented) `(uncertain whether newer models add json_schema)` | Y | `json_schema` likely rejected/ignored | https://api-docs.deepseek.com/guides/json_mode , https://api-docs.deepseek.com/guides/function_calling |
| Moonshot / Kimi | Y | Y | Y\* (newer Kimi models advertise structured output) `(uncertain)` | Y | Depends on model | https://platform.moonshot.cn/docs/api/chat |
| Qwen / DashScope compatible-mode | Y | Y | Partial `(uncertain)` | Y | `response_format` support varies by model | https://help.aliyun.com/zh/model-studio/developer-reference/use-qwen-by-calling-api |
| Together AI | Y | Y | Y\* (structured outputs on supported models) | Y | Unsupported model → 400/unsupported | https://docs.together.ai/docs/json-mode |
| OpenRouter (gateway) | Y | Y | Y* (pass-through; per-upstream support varies) | Y | Routing to a provider that lacks support may error or downgrade; `require_parameters` can filter | https://openrouter.ai/docs/features/structured-outputs |
| one-api / new-api (gateway) | Y | pass-through | pass-through `(uncertain)` | pass-through | Proxies forward the body largely verbatim; outcome = upstream's. Unknown fields may be dropped by some builds | https://github.com/songquanpeng/one-api , https://github.com/Calcium-Ion/new-api |
| LiteLLM (proxy/SDK) | Y | Y | Y\* (maps to native where supported, else prompt-injects) | Y | Translates per provider; unsupported → it falls back to instruction-style | https://docs.litellm.ai/docs/completion/json_mode |

### 1.2 The degradation problem, stated precisely

- `json_schema` unsupported on OpenAI/Azure-family APIs is a **hard 400**, not a silent ignore. A client that unconditionally sends `json_schema` will hard-fail against every older/以-custom server.
- `json_object` is more widely accepted, but the population is mixed: modern servers implement it, older/minimal servers may silently ignore it, and a few reject it.
- Prompt-only is the **only** mechanism every server accepts, because it uses no special parameter. Its cost is reliability of the *content*, which is exactly what the tolerant-parsing layer (section 2) addresses.
- Forced tool calling is a good fallback but not a baseline: some minimal compatibility servers implement `response_format` but not `tools`, and vice versa. Adding it as a third mechanism doubles the compatibility surface without being universal.

### 1.3 Recommendation (safe baseline)

**Recommendation: prompt-embedded schema + `response_format: {"type":"json_object"}` as a best-effort enhancement, a bounded fallback chain, always followed by tolerant extraction + one repair retry. Treat `json_schema` (strict) as an opt-in "strict mode" for endpoints known to support it.**

Concretely, the call ladder:

1. **Attempt 1 (default):** send `response_format: {"type":"json_object"}` **and** put the full field spec + examples in the system prompt (so prompt-only servers still work). This is the widest-accepted API-level hint with the smallest hard-fail risk.
   - For **strict mode** (per-endpoint toggle, or user knows their server): use `json_schema` with `strict:true` and `enum` arrays for `categoryId`/`accountId`/`type` instead.
2. **On HTTP 400/422 whose error body mentions `response_format`** (server doesn't understand it), retry **once without `response_format`**, prompt-only. *(This can be folded into the repair retry below to keep a 2-request budget.)*
3. **On content parse/validation failure:** one **repair retry** that omits `response_format`, re-states the schema, appends the previous bad output and the concrete parse error (section 2.4).
4. **On any remaining failure, or timeout, or non-2xx:** return `AppResult.Failure(AppError.Parse / Network / Auth)` and let the pipeline fall back to level 1 (rule layer). Never surface an exception to UI.

**Why this and not "always send `json_schema`":** the user's `baseUrl` is arbitrary and un-testable at build time; `json_schema` 400s on a large share of real deployments, which would convert a graceful-degradation requirement into a hard failure. JSON mode + prompt is the lowest-variance choice; strict schema is a quality upgrade, not a correctness prerequisite, for an 8-field flat object.

**Why not "prompt-only with no `response_format` at all":** `json_object` materially reduces fence/prose noise on servers that honour it, at near-zero compatibility cost, and the prompt still contains everything needed if it's ignored.

> Alignment with `ARCHITECTURE.md` §6 ("用 JSON Schema 约束"): implement §6's intent as the strict-mode branch of the ladder. The schema still lives in the codebase as the single source of truth (used for the `json_schema` branch **and** rendered into the prompt text); the difference is that a 400 from an unsupported server downgrades rather than aborts. Worth flagging to the main agent as a small spec nuance.

---

## 2. Robustness patterns

### 2.1 What actually goes wrong (observed classes)

1. JSON wrapped in prose: `Sure! Here is the JSON: {...} Hope this helps!`
2. JSON inside a fence: ```` ```json\n{...}\n``` ````
3. Truncation: `finish_reason == "length"` → the object is cut off mid-way (common on DeepSeek reasoning models if `max_tokens` is small; the server may also return empty `content`).
4. Trailing commas / single quotes: model emits JSON5-ish text. `org.json` is **strict** and rejects both.
5. Wrong scalar types: `"amountCents": "3500"` (string), `"confidence": 0.8` vs `"high"`, `occurredAt` as ISO string.
6. Missing optional-ish fields (`remark`, `title`) or extra hallucinated keys.
7. Empty `content` with the answer placed elsewhere (`reasoning_content`, or `tool_calls`).

### 2.2 Extraction ladder (cheap → expensive)

1. Trim; strip a leading/trailing markdown fence (```` ```json ... ``` ```` or ```` ``` ... ``` ````).
2. If direct `JSONObject(text)` fails, scan for the **first balanced `{...}`**, tracking string state and escapes, and parse that substring. This is what defeats both prose-wrapping and trailing prose.
3. If that fails, apply a *minimal* normaliser: remove trailing commas before `}`/`]`. Do **not** rewrite quotes or values — silently "fixing" types hides model errors and can corrupt Chinese text.
4. Parse with `org.json` and then **validate types explicitly** (don't trust `optInt` coercion blindly for money).

Note on alternative parsers: `kotlinx-serialization-json` (already in `:core:data`) has `isLenient` and `ignoreUnknownKeys`, which help with some malformed input but **do not** accept trailing commas or leading prose; `org.json` is equally strict but is already used across this repo (`WebDavClient`, `SnapshotJsonCodec`) and is the "already-a-dependency" choice the task calls out. Library options that are genuinely lenient exist (Gson's `JsonReader.setLenient(true)`, Moshi), but adding a new dependency for leniency is not warranted when a ~40-line extractor closes the gap — consistent with the repo's no-new-SDK policy. **Hand-rolled extraction on `org.json` is the norm here and is sufficient.**

### 2.3 The "one repair retry" pattern

Budget: **at most one** repair network call (so ≤2 LLM calls total per user input, matching §6's "重试一次").

Retry prompt shape (append to the original messages):

```text
assistant: <the exact previous raw content>
user: 你上一条回复无法解析。错误：<concrete error, e.g. "JSONObject 解析失败：Expected ',' or '}' after value at character 42">
请只返回一个符合下列结构的 JSON 对象，不要任何解释、不要 Markdown 代码块：
{ "amountCents": <integer 分>, "type": "expense|income|transfer",
  "categoryId": <string|null>, "accountId": <string|null>,
  "occurredAt": <integer epoch millis>, "title": <string>,
  "remark": <string>, "confidence": <number 0..1> }
```

Rules for the retry: restate only the *contract* (schema + "JSON only"), not the whole task; include the concrete parse error so the model can self-correct; **omit `response_format`** on the repair call (it is the most likely cause of a first-attempt 400, and prompt-only is universally accepted); keep `temperature` low (0–0.2).

### 2.4 Kotlin sketch (on existing deps: OkHttp + `org.json`)

Illustrative only — not a spec of the final class.

```kotlin
// --- extraction: fence-strip -> first balanced object -> parse -> validate ---
internal object StrictJson {
    fun extractObject(raw: String): JSONObject? {
        val deFenced = stripCodeFence(raw.trim())
        firstBalancedObject(deFenced)?.let { if (it.isNotBlank()) runCatching { return JSONObject(it) } }
        // last resort: drop trailing commas, retry once
        return runCatching { JSONObject(firstBalancedObject(deFenced)!!.replace(TRAILING_COMMA, "")) }.getOrNull()
    }

    private fun stripCodeFence(s: String): String {
        val fenced = Regex("(?s)^```[a-zA-Z]*\\s*(.*?)\\s*```$").find(s)
        return fenced?.groupValues?.get(1) ?: s
    }

    /** Scan for the first '{' and return up to its matching '}', ignoring braces inside strings/escapes. */
    private fun firstBalancedObject(s: String): String? {
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0; var inStr = false; var esc = false
        for (i in start until s.length) {
            val c = s[i]
            when {
                esc -> esc = false
                c == '\\' && inStr -> esc = true
                c == '"' -> inStr = !inStr
                !inStr && c == '{' -> depth++
                !inStr && c == '}' -> { depth--; if (depth == 0) return s.substring(start, i + 1) }
            }
        }
        return null // unbalanced => truncated
    }
    private val TRAILING_COMMA = Regex(",(\\s*[}\\]])")
}

// --- the call ladder (one class in :core:data, reached via a :core:domain interface) ---
suspend fun parse(text: String, ctx: ParseContext): AppResult<ParsedDraft> {
    val first = call(api, text, ctx, responseFormat = jsonObjectMode())   // attempt 1
    val firstObj = first.contentOrNull()?.let { StrictJson.extractObject(it) }
    if (firstObj != null) return validate(firstObj, ctx)                  // happy path
    val reason = parseErrorOf(first)                                      // 400 / truncated / bad JSON
    val retry = call(api, text, ctx, responseFormat = null,               // attempt 2, prompt-only
        extraMessages = repairMessages(first.rawContent, reason))
    val retryObj = retry.contentOrNull()?.let { StrictJson.extractObject(it) }
    return retryObj?.let { validate(it, ctx) } ?: AppResult.Failure(AppError.Parse(reason))
}

// --- per-field validation (see section 3): reject/coerce before it becomes a draft ---
private fun validate(o: JSONObject, ctx: ParseContext): AppResult<ParsedDraft> { /* 3.x rules */ }
```

`call(...)` builds the request with `OkHttpClient.newCall(...)`, JSON body via `JSONObject` (`Content-Type: application/json; charset=utf-8`, `Authorization: Bearer <key>`), and maps non-2xx to `AppError.Auth` (401/403) / `AppError.Network` (5xx, timeout) / `AppError.Parse` (400/422). Use `JSONObject.quote(...)` for any user text so Chinese is escaped correctly.

---

## 3. Field-level constraints for our use case and their gotchas

Target object: `amountCents`, `type` (`expense|income|transfer`), `categoryId`, `accountId`, `occurredAt`, `title`, `remark`, `confidence`.

| Field | Gotcha observed in practice | Mitigation |
|---|---|---|
| `amountCents` | Models emit **yuan**, not cents ("35" or `35.0` instead of `3500`). Some emit a string. Some emit `12.5` for ¥12.50. | Instruct explicitly: "整数，单位为**分**（cents），不是元"; give worked examples ("35 元 → 3500", "12 块 5 → 1250"). In strict mode add `"type":"integer"`. On parse: reject non-integer; **do not silently ×100** (a wrong assumption either way is worse than showing the raw number for the user to confirm). Plausibility-guard (≤ 0 rejected, absurd upper bound flagged). Feed `Money.ofCents(Long)`. |
| `type` | Model returns a value outside our 3 (`DEBT_REPAY`, `ADJUST`) or Chinese ("支出"). | Prompt enumerates exactly `expense|income|transfer`; strict mode uses `enum`. Map case-insensitively; unknown → default `EXPENSE` and lower `confidence` (draft still user-confirmed). |
| `categoryId` | **Hallucination is the headline risk** — model invents plausible IDs not in the ledger. | Caller supplies the real top-level IDs (and names) in the prompt. Strict mode: `"enum": [<actual ids>]`. **Always post-validate against the caller's set**; on miss, set `categoryId = null` and let the rule/classifier layer or the user pick. Never write a foreign ID into `Txn.categoryId`. |
| `accountId` | Same as category; also the model may guess when the text has no account cue. | Include the account ID list; if the text is silent, prefer `null`/default account over a guess. Validate against the caller's set. |
| `occurredAt` | Models return **ISO-8601 strings** ("2026-09-27T12:00:00+08:00"), or relative text ("昨天"). Epoch is timezone-sensitive if the model assumes UTC. | Prompt states "epoch 毫秒（UTC）", gives `System.currentTimeMillis()` **and the user's local timezone offset** + a few reference timestamps. Require an integer. Fallback: if it clearly parses as ISO, convert; if relative ("昨天"), resolve in Kotlin against the device zone. Sanity-check range (reject far-future / pre-2000) and fall back to "now". |
| `title` / `remark` | Model omits them, or duplicates the raw sentence. | Treat as best-effort; default `title` to a truncated form of the input. |
| `confidence` | Emits `0..100`, `"high"`, or omits it. | Strict mode: `{"type":"number","minimum":0,"maximum":1}`. Coerce: if `>1` and `≤100`, divide by 100; non-numeric → 0.0. Clamp to `0.0..1.0`. Persisted separately from the draft; used to decide whether to show a suggestion. |
| Chinese text | Not a JSON problem per se (`org.json` and UTF-8 handle it; models may emit `\uXXXX`, which decodes fine). But **JSON mode's "must contain the word `json`" gate is case-insensitive on the literal** — if the system prompt is entirely Chinese and never contains "JSON", some servers 400. | Include the literal token `JSON` (and the field names) in the system prompt even when the rest is Chinese. Encode the request body as UTF-8. When logging for debugging, mask user text (privacy red line). |

Cross-cutting: every field ends up in a **PENDING draft** the user confirms or corrects, so mitigation philosophy is "coerce conservatively, blank-and-let-user-fix rather than guess" — a wrong-but-confident value is worse than a blank the user fills in. Corrections also become `ml_training_sample` rows.

---

## 4. Practical API-shape notes (what varies in the wild)

| Aspect | Reality | Practical rule |
|---|---|---|
| Auth header | `Authorization: Bearer <key>` on OpenAI/Ollama-compat/vLLM/LM Studio/Together/OpenRouter/one-api/new-api. **Azure** uses `api-key: <key>` (or Bearer). Self-hosted llama.cpp/LM Studio/Ollama often need **no key**. | Send `Authorization: Bearer <key>` when a key is configured; allow an empty key (skip the header) for local servers. Flag Azure's `api-key` as a known exception in the UI hint. Never log the key. |
| `model` required? | OpenAI/Azure/Together/OpenRouter require it. Ollama/LM Studio/llama.cpp accept it but may ignore or require a *loaded* model name. | Always send `model` from settings; surface a clear error on 404/"model not found". |
| Path / base URL | The OpenAI convention is `baseUrl` ends in `/v1` → `POST {baseUrl}/chat/completions`. Azure uses a deployment path + `?api-version=`. Users paste servers **with or without** a trailing slash, and sometimes without `/v1`. | Normalise (trim trailing `/`); document that `baseUrl` must include the version segment (`.../v1`). A 404 is the common symptom of a missing `/v1`. |
| Streaming | For a one-shot object parse, **use non-streaming** (`stream` omitted/false): SSE parsing adds complexity and no benefit for a small completion. Default is non-streaming on all listed servers. | `stream: false`; read the whole JSON body. |
| Timeouts | Small structured completion: ~1–5 s hosted, **5–60 s+ local** (first token load). Non-streaming waits for the entire generation. WebDAV here already uses connect 20 s / read 60 s / write 60 s. | connect ~15–20 s, read ~60 s, write ~15 s; total wall-clock cap ~60 s so the UI can fall back to the rule layer promptly. |
| Response envelope | `choices[0].message.content` (string). Reasoning models (DeepSeek-R1 family) may put text in `message.reasoning_content` and leave `content` empty, or exhaust `max_tokens` on reasoning → `finish_reason == "length"` and truncated JSON. | Read `choices[0].message.content`; treat empty/truncated as a parse failure and trigger the repair retry. Consider a modest `max_tokens` floor. |
| Error codes | 400 (bad request incl. unsupported `response_format`), 401/403 (auth), 404 (path/model), 429 (rate limit), 5xx (server). One-api/new-api may wrap errors in its own envelope. | Map 401/403 → `AppError.Auth`, 5xx/timeout → `AppError.Network`, 400/422/parse → `AppError.Parse`, 429 → `AppError.Network` (retryable hint). All per `error-handling.md`. |
| Extra knobs | `temperature`, `max_tokens`, `top_p` are accepted broadly but not universally; reasoning models may reject `temperature`. | Keep the payload minimal: `model`, `messages`, `stream:false`, optional `response_format`, optional `temperature:0`. Don't send exotic params to an unknown server. |
| Gateways | one-api/new-api forward the body (mostly verbatim); LiteLLM translates. | No client-side special-casing needed; the ladder in §1.3 already tolerates whatever comes back. |

---

## 5. What we should implement (mapped onto the existing-deps constraint)

1. **Transport:** a small `OpenAiCompatibleClient` in `:core:data` on the existing `OkHttpClient` + `RequestBody.toRequestBody("application/json")` pattern already used by `WebDavClient`. Exposed to `:feature:ai` through a `:core:domain` interface (since `:feature:ai` has no `:core:data` dependency today). No OpenAI SDK, no Retrofit — same reasoning as §8's hand-written WebDAV.
2. **Request build:** `JSONObject` for the body (`model`, `messages`, `stream:false`, optional `response_format`); UTF-8; `Authorization: Bearer` only when a key is set; never log the key or the raw user sentence (privacy red line, ARCHITECTURE §10).
3. **Schema as one source of truth:** define the 8-field contract once in Kotlin (field names, types, the `type` enum, the `confidence` range). Render it into (a) the system prompt text and (b) the `json_schema` body used only in strict mode. Embed the caller's real `categoryId`/`accountId` lists.
4. **Ladder (§1.3):** attempt 1 = `json_object` + prompt schema; strict mode toggle upgrades to `json_schema`. On `response_format`-related 400 or content parse failure → one prompt-only repair call. Budget ≤ 2 network calls.
5. **Extraction + validation:** `StrictJson` (fence strip → first balanced object → trailing-comma normalise) then per-field validation from §3, producing either a `ParsedDraft` or `AppResult.Failure(AppError.Parse)`.
6. **Fallback:** on `AppResult.Failure`, `:feature:ai` continues down the pipeline to the rule layer; no exception escapes to UI (`error-handling.md`).
7. **Config + privacy:** extend the `EncryptedSettingsStore` pattern (AndroidKeyStore AES/GCM) with AI `baseUrl`/`apiKey`/`model`, plus a default-**off** network switch; only call out when opted in.
8. **Tests:** `MockWebServer` (already a dep) to simulate: fence-wrapped JSON, prose-wrapped JSON, trailing comma, truncated (`finish_reason:length`), 400 on `response_format`, 401, timeout, and a valid response — asserting the ladder downgrades rather than throws.
9. **Threshold note for the main agent:** §6 currently reads "用 JSON Schema 约束"; the research recommends implementing that as strict-mode within a downgrade ladder. Confirm whether to update the spec wording.

---

## 6. Caveats / Not found

- **No live verification** was possible (no web search tool exposed to this sub-agent, and `curl` egress was blocked). All server-behaviour rows are from official docs and release notes; version drift is the main risk. Re-verify against the exact deployed version (especially Ollama/LM Studio/vLLM `json_schema` support, and whether a given DeepSeek/Moonshot/Qwen model supports it).
- Exact **error codes** for unsupported `response_format` were not re-confirmed per server; the OpenAI 400 is well documented; Ollama/llama.cpp/vLLM behaviour on an unknown `response_format` value is the least certain and is folded into the "retry without `response_format`" branch regardless.
- **`kotlinx-serialization` vs `org.json` for the response**: both are available in `:core:data`. This doc recommends `org.json` for consistency with the repo's existing JSON handling; not a correctness requirement.
- **`:feature:ai` ↔ `:core:data` seam** (`:feature:ai` does not currently depend on `:core:data`) is an architecture decision for the implementer, not resolved here.
- No internal spec currently documents the LLM wire protocol beyond `ARCHITECTURE.md` §6 / `ai-ml.md`; `.trellis/spec/android/ai-ml.md` is the closest existing spec and only states the three-level pipeline + "strict JSON, retry once".
