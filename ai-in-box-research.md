# AI in Box — research + proposal

Date: 2026-11-09. Research only — no code changed.
Anthony's idea: build AI **into Box itself, running in the background, for
everyone** who uses the app — to make Box way better and unlock more
possibilities.

Box today (verified in `~/workspace/box-app`): native Kotlin/Compose,
minSdk 26 / targetSdk 36, package `com.ppp65isking.box`, PUBLIC repo,
**no backend server, no budget for one**. It has a `NotificationListenerService`
(`service/BoxNotificationListener.kt`, inbox + Deals feed), a Music tab (YT Music
via InnerTube), per-app Hubs (Gmail, Spotify, Discord, GitHub, Reddit,
Messages, Steam), Health Connect steps, and a Home grid of 115 apps.
**No WorkManager, no foreground service, no AI code exists yet.**
Anthony's phone: Pixel 9 Pro (Tensor G4, 16 GB RAM — published spec).

His standing rule for Box going public matters here: users connect THEIR OWN
accounts — one OAuth client per service, nobody pastes client IDs; secrets
(like the Steam API key) are user-entered in the app, never embedded in
code. Any AI approach that needs an API key has to fit that rule.

## 1. On-device AI options

### 1.1 Gemini Nano via AICore (ML Kit GenAI APIs) — the flagship path

**What it is:** Gemini Nano is Google's smallest Gemini model, built to run
on the phone. It lives in AICore, an Android system service that manages
the model — the app never downloads or bundles it. Apps reach it through
Google's **ML Kit GenAI APIs** (Play Services client libraries):
Summarization (1–3 bullet points), Proofreading, Rewriting, Image
Description, and the **Prompt API** (beta: free-form prompts, system
instructions, structured output). There is also an Agentic-workflows API
and an Embeddings API on the same page.

Sources: developers.google.com/ml-kit/genai/prompt (official ML Kit pages,
fetched 2026-11-09); Capawesome blog on Gemini Nano in Capacitor apps;
Android Developers blog, July 2026 (Nano on 140M+ devices).

**Which phones have it:** only specific flagships. Per Google's ML Kit
GenAI device list as reported Oct–Nov 2026:
- Nano v2: OnePlus 13, Xiaomi 15, Galaxy Z Fold7, Honor Magic 7, realme GT 7 Pro
- Nano v3: **Pixel 9 and Pixel 10 (incl. Pro/Pro XL/Pro Fold)**,
  Galaxy S26 series, OnePlus 15, OPPO Find X9, vivo X300
- Nano v4: Pixel 11, Galaxy Z Flip8/Fold8
- Older coverage: Pixel 8 Pro, Galaxy S24/S25 series
- Requirements: Android API 26+, **locked bootloader** (unlocked-bootloader
  devices are excluded), Tensor / Snapdragon / Dimensity flagship chips.

**Anthony's Pixel 9 Pro has Nano v3 — he is on the list.** Most other
people's phones are not. Always feature-detect at runtime
(`checkFeatureStatus`); never assume Nano is there.

**The big catch for this idea — background:** ML Kit GenAI inference is
allowed **only while the app is the top foreground app**. Background calls
return `BACKGROUND_USE_BLOCKED`. So "AI running in the background"
**cannot be Gemini Nano**. It can only run while Box is open, or be
triggered the moment the user opens Box. (Source: dev.to "On-Device AI on
Android (2026)"; consistent with AICore's design.)

**Cost / performance:** $0, no API key, no server, fully private and
offline once the model is on the device. One third-party guide reports a
500-word article summarized in under 2 seconds on a Pixel 9 Pro.
Latency/battery are NPU-accelerated and cheap per call.

**minSdk note (unresolved):** one ML Kit source says the GenAI APIs need
min API 26 (Box is 26 — fine); a separate AICore guide says min SDK 31
for the raw AICore client. Box would use the ML Kit path, but verify at
build time. Sideloading should not matter — these are Play Services
client libraries, not Play Store install checks — but verify on Anthony's
phone with a `checkFeatureStatus` call, since Box is sideloaded.

### 1.2 MediaPipe LLM Inference API + Gemma — the "any phone" on-device LLM

**What it is:** Google's MediaPipe GenAI framework (LiteRT runtime) loads
an open model file (Gemma, Phi, Llama) inside the app and runs it with
CPU/GPU/NPU acceleration. The app downloads the model once (not bundled
in the APK) and runs it forever after, offline.

Sources: ai.google.dev/edge/mediapipe/solutions/genai/llm_inference
(official docs, cited in the guides below); Gemma 4 on-device guides,
Oct 2026.

**Model sizes (the part that decides everything):**

| Model | Download | RAM at runtime | Quality |
|---|---|---|---|
| Gemma 2B INT4 | ~1.3 GB | ~4 GB | Basic chat |
| Gemma 4 E2B INT4 (mobile QAT build) | **~840 MB** (`.task` file) | ~1 GB | Good for its size |
| Gemma 4 E4B INT4 (mobile build) | ~2.2 GB | ~2–3 GB | Better reasoning |
| Gemma 3n E2B INT4 | ~2 GB | ~4 GB | Multimodal |

One guide reports Gemma 4 E4B at 25–40 tokens/second on a Pixel 9 Pro.
Minimums reported: Android 10+, ~4–6 GB RAM, ~2 GB free storage.
Emulators are not supported — physical device only.

**Background:** unlike Nano, this runs in Box's own process, so it *can*
run in a foreground service or a WorkManager worker. But: loading a ~1 GB
model takes time and RAM, sustained generation burns battery, and Android
can still kill the process under memory pressure. It is possible, not free.

**Friction nobody mentions:** the Gemma model files live on HuggingFace
and require accepting the Gemma license (free account) to download. An app
can't silently bundle a 1 GB model in the APK, and auto-downloading it
in-app means a Wi-Fi-only first-run download plus license/attribution
handling. Doable, but it is a real onboarding step, not invisible.

**Verdict:** the only on-device LLM that works on non-Pixel phones — but
the ~1 GB download and RAM floor make it an opt-in for power users, not
the default for everyone.

### 1.3 ML Kit (classic, non-generative) — the quiet workhorse

**What exists:** on-device, task-specific APIs, no LLM: **Smart Reply**
(up to 3 suggested replies from the last 10 messages, English only,
skips sensitive topics), **Entity Extraction** (dates, times, addresses,
phone numbers, etc. in text — 15 languages), Language ID, Translation
(59 languages, models downloaded on demand), Text Recognition (OCR),
Barcode, Face Detection, Image Labeling.

Source: developers.google.com/ml-kit (official docs, fetched 2026-11-09);
ML Kit migration guide (Firebase ML shuts down June 15, 2027 — use the
standalone ML Kit SDK, not Firebase ML).

**Limits:** no general LLM, no open-ended generation, no chat, no
reasoning. Smart Reply is casual-conversation only. But: works on
**essentially every Android phone (API 16+)**, no model download for
Smart Reply / Entity Extraction, no key, no server, free, offline.
This is the only AI on this page that is truly "for everyone" with zero
conditions.

## 2. Cloud AI options

### 2.1 Gemini API (Google AI Studio) — free tier is real, with catches

**Free tier:** free input + output tokens on Flash and Flash-Lite models
(Gemini 3.8/3.6 Flash, 3.5/3.1 Flash-Lite, 2.5 Pro/Flash/Flash-Lite,
Gemma 4, per Oct 2026 checks). Pro/Argon and image/video models are
paid-only. Limits are per **Google Cloud project** (RPM / input TPM /
requests per day) and are **not published per model** — Google's
rate-limits page sends you to AI Studio to see your own project's numbers,
and says limits are not guaranteed. RPD resets at midnight Pacific.
Exceeding a limit = HTTP 429, not a bill.

**Paid rates (standard tier, checked Oct 2026 — treat as dated, Google
changes these often):** Gemini 3.8 Flash $0.75 input / $3.75 output per
1M tokens through Dec 31, 2026, then $1.50 / $7.50. Flash-Lite is
$0.30 / $2.50 (3.5) and $0.25 / $1.50 (3.1). Tier 1 needs a linked
billing account (min $5 prepayment).

**Data terms (important):** on the free tier, prompts and responses can
be used to improve Google's products; on the paid tier they are not.

**What cloud unlocks that on-device can't:** a much bigger model (long
context, real reasoning, tool use, following complex instructions),
consistent quality on every phone, no model download, no RAM floor.
Natural-language search, thread summarization, and deal ranking all get
noticeably better with a cloud model than with Nano/Gemma-2B.

Sources: ai.google.dev/gemini-api/docs/pricing and /rate-limits (via
third-party checks Oct 2026: iimagined.ai, infro.io, cometapi.com,
twitterapi.io — all agree on the structure above; exact numbers vary by
project so verify in AI Studio).

### 2.2 Key handling — the problem that decides the architecture

**Embedding a key in the app is broken.** An API key shipped in an APK
can be extracted with standard decompiling tools (apktool/jadx) in
minutes, and Box's repo is PUBLIC — the key would be in the git history
too. Anyone could then burn Anthony's quota or bill. This directly
violates his public rule (no embedded secrets). Do not do it.

**Option A — per-user key (fits his rule exactly).** Each user creates
their own free Gemini API key in AI Studio (2 minutes, no billing) and
pastes it into Box once, stored in EncryptedSharedPreferences — the same
pattern as the Steam API key today. Cost to users: $0 on the free tier.
Cost to Anthony: $0. Downside: a setup step per user, and if a user's
free quota 429s, that feature pauses until the next day.

**Option B — backend proxy (a server holds the key).** Box calls
Anthony's server; the server calls Gemini with his key. Cheapest real
options (verified Sep–Oct 2026):
- **Cloudflare Workers free:** 100,000 requests/day, 10 ms CPU per
  request, no card required. Paid step $5/mo. A thin proxy fits easily.
- Google Cloud Run free: 2M requests/month, 180k vCPU-seconds.
- Firebase Spark: no Cloud Functions on the free plan (Blaze/pay-as-you-go
  only) — not the free route.
- Oracle Always Free: real VM forever, but needs a card.

The proxy itself is cheap ($0). The problem is **abuse**: if the endpoint
is open, anyone with the app can hammer Anthony's key and quota. A safe
proxy needs per-install auth + per-user rate limiting (e.g. Cloudflare KV
or D1 to count calls per install). That is a real small backend to build
and own — and Anthony becomes the person paying if usage ever exceeds
the free tier. Recommend **against** a proxy until Box has real users
asking for keyless AI.

**Option C — Firebase AI Logic (formerly Vertex AI in Firebase).** The
app calls Gemini through the Firebase client SDK with App Check. No
per-user key in the app — but the calls still bill to **Anthony's**
Firebase project, so it has the same "Anthony pays for everyone" shape
as a proxy, plus a Firebase project to own. Not a free lunch.

## 3. Background execution — what can actually run in the background

This is the part of the idea that hits Android's rules hardest.

**What exists:**
- **WorkManager** (Google's recommended path): deferrable, guaranteed
  work that survives app closure and reboots. Periodic work minimum
  interval is **15 minutes**. It respects Doze mode and App Standby —
  jobs are deferred until maintenance windows when the phone is idle.
  Good for: "every 15–60 min, catch up on new notifications and
  pre-compute a digest."
- **Foreground service**: continuous work with a persistent notification
  the user can see. Android 15 caps `dataSync` and `mediaProcessing`
  foreground services at **6 hours per 24 hours** unless the user brings
  the app to the foreground. `BOOT_COMPLETED` can no longer auto-start
  several service types. A foreground service running LLM inference
  would also be a visible battery cost.
- **NotificationListenerService** (Box already has this): the system
  delivers notifications to it in the background as they arrive. It can
  *capture* everything, but it should do only cheap work (store the
  notification, regex/keyword deal detection). Heavy inference inside
  the listener risks ANRs and battery blame — hand off to WorkManager.
- **Doze / App Standby**: when the phone is idle and unplugged, network
  and jobs are deferred; infrequently used apps get stricter limits.

**The honest mapping:**
- **Gemini Nano: cannot run in the background at all**
  (`BACKGROUND_USE_BLOCKED`). Summaries must happen while Box is open,
  or be computed the moment the user opens Box (fast — ~2s for a digest).
- **MediaPipe Gemma: can run in background** (foreground service or
  WorkManager worker) but costs battery + RAM + a ~1 GB model, and the
  system can kill it. Realistic only as a periodic batch job
  ("summarize the last hour's inbox every 30 min while charging"), not
  continuous.
- **Cloud API: can run in background** via WorkManager — a short API call
  per batch of new notifications is cheap and reliable, as long as the
  phone has network in the maintenance window. This is the only approach
  where "AI worked while you were away" is straightforward.

Sources: Android background execution guidance as summarized Oct 2026
(WorkManager recommended path, Doze/App Standby behavior, Android 15
6-hour foreground-service cap, Android 16 job quotas); dev.to on-device
AI 2026 for the Nano foreground-only restriction.

## 4. Feature mapping — which approach powers what

| Box feature | Best approach | Why / notes |
|---|---|---|
| **"What did I miss?" inbox digest** | Nano Summarization (foreground, on open) — or cloud via WorkManager for true background | Nano: 1–3 bullets, instant, private. True background digest needs cloud + per-user key or proxy |
| **Smart replies / draft messages** (Messages, SMS, Discord) | **ML Kit Smart Reply** — works on every phone, no Nano needed | English only, casual only; good enough for quick replies. Nano Prompt API can draft longer replies on supported phones |
| **Natural-language search** ("open the food app with the best deal") | Nano Prompt API, or cloud | Needs real language understanding + structured output; Nano Prompt API (beta) can do it on Pixel 9 Pro; cloud does it everywhere |
| **Auto-categorize the inbox** (deals vs people vs system) | ML Kit Entity Extraction + keyword rules (every phone) → Nano/cloud classifier for the hard cases | Start with rules + entities; a classifier is overkill for v1 |
| **Deal detection + ranking** | Rules + entities first (price/date/percent detection), Nano/cloud to rank "best deal" | The Deals feed already exists; AI's job is ranking and de-duping, not detection |
| **Smarter "Jump back in"** | UsageStats (already planned, Phase 2 quick-links) + recency — no LLM needed | Don't spend model calls on what a timestamp answers |
| **Summarize long Discord / Gmail threads** | Nano Summarization on supported phones; cloud for everyone else | Long threads are exactly where Nano's 1–3 bullets shine; cloud handles the 100+ message threads |
| **"Get info with permission" (order status, points)** | Accessibility Service only — fragile, heavy, Phase 4 at most | No AI changes this; it is a screen-scraping problem, not a model problem |

## 5. "For everyone" reality check

| Approach | Works on whose phone? | Needs a key? | Needs a server? | Costs money? |
|---|---|---|---|---|
| ML Kit classic (Smart Reply, entities) | **Every Android phone** (API 16+) | No | No | $0 |
| Gemini Nano (ML Kit GenAI) | Flagships only (Pixel 8 Pro/9/10, S24/S25/S26, OnePlus 13/15, etc.); ~140M devices mid-2026 | No | No | $0 |
| MediaPipe Gemma (opt-in download) | Any Android 10+ phone with ~4–6 GB RAM | No | No | $0 (but ~1 GB download + battery) |
| Cloud Gemini, per-user key | **Every phone with internet** | Yes — the user's own free key | No | $0 on free tier |
| Cloud Gemini, proxy server | Every phone with internet | No (server holds it) | **Yes** — Anthony owns it, abuse risk | $0 at Cloudflare free tier; Anthony pays if it grows |

No single approach is "for everyone" by itself. The honest
"for everyone" design is a **ladder with graceful fallback**:
Nano where the phone has it → per-user-key cloud where it doesn't →
plain rules/ML Kit where neither is set up. Every feature must degrade
cleanly, never show a dead button on an unsupported phone.

## 6. Recommendation

**Start with: ML Kit classic + Gemini Nano, foreground, no key, no server.**

Phase 1 — the quick win (no key, no server, no model download):
1. **Smart Reply drafts** in the Messages/SMS hub — ML Kit Smart Reply.
   Works on every phone, including Anthony's friends' phones. This is
   the first AI feature that is genuinely "for everyone."
2. **"Catch me up" digest in the Inbox** — ML Kit GenAI Summarization
   on Nano devices (his Pixel 9 Pro works today), computed when Box
   opens. On phones without Nano, show the plain notification list
   (no dead button, no error).
3. **Entity Extraction quick actions** — dates/addresses/phone numbers
   in notifications become tappable (add to calendar, open in Maps, call).
   Every phone, no model.

Phase 2 — Nano Prompt API (still no key/server):
4. Natural-language search + "best deal" ranking + thread summaries,
   on Nano devices only, with a clear "needs a newer phone" note
   elsewhere. This is where the Prompt API earns its keep.
5. UsageStats "most used" ordering (already planned) — no model needed.

Phase 3 — per-user cloud key (unlocks it for everyone):
6. **"Bring your own Gemini key"** in Settings — same pattern as the
   Steam key: paste once, stored encrypted on the phone, never leaves
   the device except to call Google. Every Phase 1–2 feature then works
   on every phone, using the user's own free-tier quota. This is the
   step that makes the AI truly "for everyone," and it fits his public
   rule exactly (users bring their own credentials).
7. Optional: MediaPipe Gemma as an opt-in offline mode for people who
   don't want a key and have the RAM/storage — only if someone asks.

Phase 4 — only if Box gets real users:
8. Cloudflare Worker proxy (free tier) so users get AI with zero setup.
   Only after deciding who pays when usage grows and building per-install
   rate limiting. Do not build this speculatively.
9. Background pre-digests via WorkManager + cloud — only once Phase 3
   exists, since Nano can't do background at all.

**Do not build:** a proxy server now (no users to serve, abuse risk,
Anthony would own the bill); Accessibility-based reading (fragile,
high friction, not an AI problem); bundling a 1 GB Gemma model into the
app (APK bloat + license friction).

## 7. What would need Anthony

- **Phase 1–2: nothing to pay, no key, no account.** Only his on-device
  test: does Summarization work in sideloaded Box on his Pixel 9 Pro
  (a `checkFeatureStatus` call will say), and does he like the digest.
- **Phase 3: each user creates their own free Gemini API key** in AI
  Studio (2 minutes, no billing). Anthony's own step to test it: create
  one key, paste it into Box, confirm the cloud path works.
- **Phase 4 (not recommended yet):** a Cloudflare account (free, no card)
  and a decision about abuse limits — his call, only if Box gets users.
- **Never needed:** embedding any key in the public repo, or paying for
  a server to start.

## 8. What I could not verify

- The exact per-model free-tier RPM/RPD numbers: Google no longer
  publishes them; they are shown per-project in AI Studio. Any number
  quoted elsewhere is unverified.
- Whether ML Kit GenAI needs minSdk 26 or 31: sources disagree; verify
  at build time (Box is minSdk 26).
- Whether a sideloaded app can call ML Kit GenAI on a Pixel 9 Pro:
  should work (Play Services client libs), but unproven until a
  `checkFeatureStatus` call runs on his phone.
- Gemma license/redistribution terms for in-app download: HuggingFace
  requires accepting the Gemma license; whether an app may fetch the
  `.task` file on the user's behalf without them clicking through on
  HuggingFace is unconfirmed — verify before building Phase 3's Gemma
  option.
- Android 17's App Functions / on-device MCP (system agent calling app
  functions) was mentioned in one 2026 guide as an emerging option —
  too new to build on; watch only.
- All prices are from Oct 2026 third-party checks of Google's pages;
  Google changes pricing often — re-check in AI Studio before quoting
  a price to anyone.

## Sources

- ML Kit GenAI APIs (Prompt, Summarization, Proofreading, Rewriting,
  Image Description, Embeddings): developers.google.com/ml-kit/genai/prompt
  (official, fetched 2026-11-09)
- ML Kit Smart Reply / Entity Extraction / language APIs:
  developers.google.com/ml-kit (official, fetched 2026-11-09); ML Kit
  migration guide (Firebase ML shutdown June 15, 2027)
- Gemini Nano device coverage + AICore + foreground-only inference:
  Capawesome, "How to Use Gemini Nano in a Capacitor App";
  Android Developers blog, July 2026 (140M+ devices); dev.to,
  "On-Device AI on Android (2026): LiteRT, Gemini Nano, and Edge
  Deployment"; DeviceBench platform map, Sep 2026
- MediaPipe LLM Inference API + Gemma sizes/speed:
  ai.google.dev/edge/mediapipe/solutions/genai/llm_inference (official);
  Gemma 4 on-device guides (baeseokjae.github.io, Medium, GitHub
  on-device-gemma3-lm, smartspend-app research doc, Oct 2026)
- Gemini API free tier + pricing: ai.google.dev/gemini-api/docs/pricing
  and /rate-limits via Oct 2026 checks (iimagined.ai, infro.io,
  cometapi.com, twitterapi.io, creditforstartups.com)
- Cloudflare Workers free tier (100k req/day, no card):
  cloudflare.com/pricing + perspectives, checked Oct 2026;
  free-hosting roundups Sep–Oct 2026 (kurums.com, snapdeploy.dev)
- Android background execution (WorkManager, Doze, App Standby,
  Android 15 6-hour foreground-service cap, Android 16 job quotas):
  Android background guidance as summarized Oct 2026
  (copyprogramming.com 2026 guides)

---

# 9. Multi-model fallback router — refinement (2026-11-09, Anthony's clarification)

Anthony clarified the idea: **multiple free AI models built into Box, with
automatic fallback switching** — if one model isn't available, Box switches
to the next one. He named "Meta Muse or AI models" as examples, plus
Gemini Nano on Pixels or other models if available. So the architecture is
a **model router with an ordered fallback chain**, all free, built into the
app. This section refines §§1–8 for that shape. It does not rewrite them.

## 9.1 "Meta Muse" — the verdict (read this first)

There is **no free way to call Muse from Box**, and no free Muse API tier.

- **Muse Spark is on the Meta Model API** (dev.meta.ai) — public preview,
  US developers, OpenAI SDK–compatible, base URL `api.meta.ai/v1`,
  model `muse-spark-1.1`. It is **pay-as-you-go: $1.25 input / $4.25
  output per million tokens** (Meta's own dev blog, Jul 2026). It needs
  an API key from dev.meta.ai. There is no free tier on the official
  pages; one third-party aggregator lists a "contributor tier" at
  $0.10 / $0.20 (used to improve Meta's products) — unverified which
  tier a self-serve signup gets. Treat Muse as a **paid leg**.
- **Meta's Llama API is effectively gone as a self-serve product.**
  llama.com and llama.developer.meta.com now redirect to dev.meta.ai,
  and the Meta Model API serves Muse, not Llama. The Groq-accelerated
  "official Llama API" at `api.llama.com/compat/v1/` is early-access
  gated (unauthenticated requests return 401). Meta does not offer a
  free self-serve Llama API as of Oct 2026 (flatkey.ai, Oct 22, 2026).
- **Muse Spark Open** (open weights, released Oct 15, 2026) is real but
  useless on a phone: 816B total / 42B active MoE, 1M-token context,
  checkpoint ~**1.5 TB** on Hugging Face. **Muse Glimmer 30B** (open
  weights, Aug 2026) is also on HF — still far too big for a phone.
- **Closest real things to "Meta Muse, free":** Llama models served by
  a free inference provider (Groq / OpenRouter free tiers, §9.3), or
  small open-weight models run on-device (Llama 3.2 1B, Qwen, Gemma via
  LiteRT-LM, §9.2). Neither is Muse, and the doc should not call them
  that. A Muse leg can be added later as a **paid per-user-key** option
  (same pattern as the Steam key) if Anthony ever wants it — it is not
  part of the free chain.

Sources: dev.meta.ai blog "Build with Muse Spark" (Jul 2026); flatkey.ai
"Llama API (2026)" (Oct 22, 2026); technspire.com "Muse and the EU"
(Sep 2026); coursium.ai "Is Muse Spark Open Safe?" (Oct 2026).

## 9.2 On-device models — the runtime changed: LiteRT-LM, not MediaPipe

**Correction to §1.2:** Google's MediaPipe LLM Inference API (Android,
iOS, Web) is now in **maintenance-only mode** — the docs carry a notice
that new features go to **LiteRT-LM**, and new code should start there
(dev.to, Oct 17, 2026; Medium "LiteRT vs TensorFlow Lite", Sep 2026).
LiteRT-LM is Google's runtime for language models on top of LiteRT
(TensorFlow Lite renamed): artifact
`com.google.ai.edge.litertlm:litertlm-android`, **0.18.0** (Oct 2026,
still below 1.0), `.litertlm` bundles, small Kotlin API (`Engine` +
`Backend` CPU/GPU/NPU + `Conversation` with streaming). Native libs
ship for **arm64-v8a and x86_64 only** — add a `Build.SUPPORTED_ABIS`
check before loading. Models come from Hugging Face (`litert-community`
org), pinned to immutable commits with SHA-256 verification.

**Model table** (sizes from the DroidKaigi 2026 app README, the
flutter_gemma model list, and MLHive's supported-models matrix — all
Nov 2026; where sources disagree the range is shown):

| Model | Download | RAM at runtime | Gated? | Notes |
|---|---|---|---|---|
| **Qwen2.5-0.5B-Instruct** (int8 `.task`) | **~546 MB** | ~1 GB | **No** — anonymous download | Best default: smallest, fastest, no token needed |
| Qwen2.5-1.5B-Instruct (int8) | ~1.6 GB | ~2 GB | No | More coherent than 0.5B |
| Qwen3 0.6B | ~586 MB | ~1 GB | No | Function calling, tiny |
| Gemma 3 1B (int4) | ~0.5–1 GB | ~1–2 GB | **Yes** — HF account + license accept + read token | Balanced text generation |
| Gemma 3 270M | ~0.3 GB | <1 GB | Yes | Fine-tuning / classification grade |
| Gemma 4 E2B (mobile QAT build) | **~840 MB–2.4 GB** (sources disagree — see §9.7) | ~1–2 GB | Yes (but see §9.7) | Best small-model quality; multimodal |
| Gemma 4 E4B | ~2.2–4.3 GB | ~2–3 GB | Yes | Better reasoning; too big as a default |
| **Phi-4-mini-instruct** | ~3.9 GB | ~4 GB+ | No | Strongest reasoning of the small models; huge |
| Llama 3.2 1B | ~1.1 GB | ~2 GB | **Yes** — Llama license accept on HF | Efficient instruction following |
| TinyLlama 1.1B | ~680 MB | ~1 GB | No | Compact general chat |
| SmolLM 135M | ~150 MB | <1 GB | No | Classification / tiny tasks only |

**Gating is the real friction, not size.** Qwen and Phi download
anonymously — no account, no token, no click-through. Gemma and Llama
are gated: the user must accept the license on Hugging Face, and for
Gemma the app needs the user's HF read token pasted in (the DroidKaigi
app does exactly this — a "Hugging Face token" field appears in Settings
when a gated model is selected). A gated model can never be a silent
default download; it is an opt-in for users who do the license step.

**Speed / quality for Box's small tasks:** Gemma 3 1B at 45–65 tok/s;
Gemma 4 E4B at 25–40 tok/s on a Pixel 9 Pro; mid-range Snapdragon 778G
at 20–40 tok/s. For Box's actual jobs (classify a notification,
1–3 bullet summary, 1-line reply draft), a 0.5B model is good enough;
1–1.5B is better for free-form text; Phi-4-mini is overkill for these
tasks and 4 GB is not worth it as a default. **One model at a time by
default** (Settings can hold a second); the router picks the smallest
model that can do the job.

**Background:** LiteRT-LM runs in Box's own process, so unlike Nano it
*can* run in a foreground service or WorkManager worker — but loading a
~1 GB model costs time/RAM/battery, and Android can kill the process
under memory pressure. Realistic as a periodic batch job ("summarize
the last hour's inbox every 30 min while charging"), not continuous.

## 9.3 Free cloud legs — the honest table (terms changed Oct 2026)

Every free cloud endpoint needs an API key, even the free ones. There is
**no keyless free cloud endpoint** for an app to call. The only
zero-setup free AI is on-device (§9.2 + Nano + ML Kit classic).

| Provider (free leg) | Free limits (checked Oct–Nov 2026) | Key? | Card? | Verdict for Box |
|---|---|---|---|---|
| **Groq** (`api.groq.com/openai/v1`) — gpt-oss-120b / 20b, qwen3.8-27b | **30 RPM, 1,000 RPD, 8K TPM, 200K TPD** | Yes (`gsk_…`) | No | **Best free cloud leg** — biggest quota, no card, OpenAI-compatible. Llama models moved to Enterprise (not free). |
| **Gemini API** (AI Studio) — Flash / Flash-Lite | ~15 RPM, ~1,500 RPD (older checks; Google no longer publishes per-model limits) | Yes | No | Solid second leg. Free-tier prompts may train Google's products. |
| **OpenRouter** (`:free` models, incl. `openrouter/free` router) | 20 RPM, **50 RPD unfunded / 1,000 RPD after $10 lifetime purchase** | Yes | No (unless buying the $10) | Spare leg only — 50/day unfunded is thin; free lineup rotates (models come and go). |
| GitHub Models (models.github.ai, PAT auth) | 15 RPM / 150 RPD low tier; 10 RPM / 50 RPD high tier; 8K in / 4K out per request; production use banned | Yes (PAT) | No | **Do not build on it** — one source claims it was retired Jul 30, 2026; others describe it live through Oct 2026. Status unverified. |
| Together AI | **Free tier ENDED** — docs (Oct 2026) say no free trials, min **$15** credit purchase. A few models listed at $0.00 (e.g. Ternary Bonsai 27B). | Yes | Yes (to use) | Not a free leg. Skip. |
| Cerebras | **Changed** — mid-2026 sources say 1M tokens/day free, no card; Oct 2026 official-docs checks say **$5 one-time trial credit, payment method required**, 5 RPM / 30K TPM / 1M tokens/day, expires 30 days, **no permanent free tier**. | Yes | **Yes** | Card-gated trial, not free. Skip unless re-verified. |
| Hugging Face Inference Providers (`router.huggingface.co/v1`) | **Old free serverless scheme is gone** — free accounts get **no** Inference Providers credits (Oct 2026); PRO $9/mo includes $2. You *can* route with your own provider key (e.g. a Groq key) through the HF router without HF charging. | Yes (HF token) | No | Not a free leg by itself. Only useful as a router for keys the user already has. |
| Meta Model API (Muse Spark / Llama) | **No free tier** (see §9.1). Muse Spark PAYG $1.25 / $4.25 per M tokens. | Yes | Yes | Paid leg only. Not in the free chain. |

When a free quota runs out, the provider answers **429** (not a bill) —
except OpenRouter, which answers **402** if the account balance goes
negative (free models are blocked too). Nothing here can charge Anthony
or the user, because no card is on file on any of the three recommended
legs (Groq, Gemini, OpenRouter-unfunded).

Sources: Groq rate-limits page via byoklist.com / toolfreebie.com /
apidog.com (Oct 2026); OpenRouter limits docs via fast.io /
ampm-aiops.com (Aug–Sep 2026); Together AI billing docs via
grabaicredits.com (Oct 17, 2026); Cerebras rate-limits page via
saaspuppy.com / opengpuradar.com (Oct 2026); HF Inference pricing via
klymentiev.com / aifree.dev (Sep–Oct 2026); GitHub Models docs via
toolchew.com / fast.io (Jun–Oct 2026, conflicting on retirement).

## 9.4 The router design

**Precedent that this exact shape works:** the DroidKaigi 2026
conference app (GitHub `m15-ai/droidkaigi2026-app-gvp`, README read
Nov 2026) runs a voice pipeline with a fallback chain
**LiteRT (MediaPipe) → Gemini Nano via AICore → stub**, a
user-selectable model catalog in Settings (Qwen 0.5B/1.5B, Phi-4-mini,
Gemma), downloads persisted on-device, and an *Auto / force-LiteRT /
force-AICore* mode switch. Box can copy this shape almost directly —
the pattern is proven in a real shipping Android app.

**Runtime detection (how Box knows what's available):**
- **Nano:** ML Kit GenAI `checkFeatureStatus` (Prompt / Summarization
  APIs) — async, returns available / downloading / unavailable.
  Foreground only (`BACKGROUND_USE_BLOCKED` otherwise).
- **LiteRT-LM model:** model file exists in app storage + SHA-256
  check + RAM check (`ActivityManager.memoryInfo`) + ABI check
  (`Build.SUPPORTED_ABIS` contains arm64-v8a or x86_64) before loading.
- **Cloud leg:** network available + a stored key present for that
  provider + last-known quota state (the router remembers 429s and
  cools that leg down for ~15 min instead of hammering it).
- Availability is re-checked **lazily** (on engine failure, or at most
  once per app open), not on every call.

**Recommended fallback chain (in order):**
1. **Gemini Nano** — fastest, free, private, no download. But
   flagship-only and foreground-only.
2. **LiteRT-LM downloaded model** (Qwen 0.5B default; user can pick
   1.5B / Gemma in Settings) — offline, any phone, background-capable
   in WorkManager batches.
3. **Cloud free tier via per-user key — Groq first** (biggest free
   quota), Gemini second, OpenRouter as spare — works on every phone
   with internet, background-capable.
4. **ML Kit classic** (Smart Reply / Entity Extraction / keyword
   rules) — always available, no model at all.
5. **Plain list, no AI** — never a dead button, never an error state.

**Task-based routing, not just availability:** route by job size as
well as by what exists. Tiny jobs (classify a notification, 1-line
reply) go to the smallest/cheapest engine that can do them (ML Kit or
Qwen 0.5B) — never spend a 120B cloud call on what a regex answers.
Summaries go to Nano or cloud. Natural-language search ("open the food
app with the best deal") goes to Nano Prompt API or cloud, because it
needs real reasoning + structured output.

**Offline:** Nano + LiteRT work offline; cloud legs skip silently.
**Per-call fallback:** try the engine; on error / 429 / timeout, move
to the next leg and remember the failure briefly. **What the user
sees:** silent switching is fine when quality is equivalent, but show
a small label so the switch is visible during testing — recommend a
subtle chip in the Inbox header like "AI: Nano" / "AI: Qwen on-device"
 / "AI: Groq cloud", plus the full chain and per-leg status in
Settings → "AI model" (Auto default, or force one leg).

**Data model sketch (for the build, not code):** one `AiEngine`
interface (`suspend fun run(task: AiTask): AiResult`), one `AiRouter`
that owns the ordered leg list and the cooldown map, one `AiTask` type
(summarize / reply-draft / classify / search / rank-deals), and one
`AiResult` carrying the text **plus which engine answered**. Every
feature calls `AiRouter.complete(task)` and never talks to a model
directly — that is what makes adding a leg later a one-file change.

## 9.5 What "built in for free" honestly costs

- **Download:** Qwen 0.5B ~546 MB (Wi-Fi-only first-run download,
  with progress + resume); Gemma 4 E2B ~840 MB–2.4 GB (discrepancy —
  verify before promising a number); Phi-4-mini ~4 GB (don't ship as
  a default). **One model at a time by default.**
- **Storage:** ~0.5–1 GB per downloaded model, in app storage.
- **Battery:** on-device inference is NPU/GPU-accelerated and cheap
  per call; background batches only while charging (WorkManager
  constraint) so it never drains a phone in a pocket.
- **Rate limits:** Groq's 1,000 requests/day per user is plenty for
  Box (a digest is 1 call; a reply draft is 1 call). OpenRouter's
  50/day unfunded is thin — spare leg only.
- **Keys:** every cloud leg needs the user's own free key, pasted
  once, stored in EncryptedSharedPreferences — the same pattern as
  the Steam key, and it fits his public rule exactly (users bring
  their own credentials, nothing embedded in the public repo). **No
  key = the cloud legs simply don't appear in the chain.** The
  zero-setup free version of Box AI is on-device only (Nano + LiteRT
  + ML Kit classic).

## 9.6 Updated recommendation — Phase 1 build now

**Phase 1 — router + free on-device legs (no key, no server, no big
download by default):**
1. **Router skeleton** — `AiEngine` interface + `AiRouter` with the
   ordered leg list (Nano → LiteRT → cloud-stub → ML Kit classic →
   plain). One entry point, `AiRouter.complete(task)`, returning the
   result plus which engine answered. Cloud legs exist in the code as
   "not configured" until a key is added — that is the whole point of
   the router: adding a leg later is a one-file change.
2. **ML Kit Smart Reply drafts** + entity quick actions (every phone,
   no model).
3. **"Catch me up" digest** via Nano where available, else the
   downloaded LiteRT model if present, else the plain list. Computed
   on open (foreground — the Nano rule).
4. **Settings → "AI model"** — Auto (default) / force one leg; an
   opt-in **"Download on-device model (Qwen 2.5 0.5B, ~550 MB,
   Wi-Fi only)"** button with progress; a tiny label showing which
   engine answered last.
5. **Use LiteRT-LM, not MediaPipe** — `com.google.ai.edge.litertlm:
   litertlm-android` 0.18.0, `.litertlm` bundles, plus the ABI check.
   MediaPipe LLM Inference is maintenance-only; starting on it now
   means planning a migration on day one.

**Phase 2 — Nano Prompt API + model choice (still no key/server):**
6. Natural-language search + "best deal" ranking + thread summaries
   through the same router, on Nano devices.
7. Settings model picker: Qwen 1.5B / Gemma 3 1B as opt-in downloads,
   with the gating note shown in the UI (Gemma needs the user's HF
   token pasted; Qwen downloads anonymously).

**Phase 3 — cloud legs via per-user keys (unlocks it for everyone):**
8. **"AI keys" Settings screen** — same pattern as Steam: paste once,
   stored encrypted, never leaves the device except to call that
   provider. **Groq first** (biggest free quota, no card), Gemini
   second, OpenRouter as spare. Every Phase 1–2 feature then works on
   every phone.
9. Optional: MediaPipe/LiteRT Gemma as an opt-in offline mode for
   people who don't want a key and have the RAM/storage.

**Phase 4 — only if Box gets real users:**
10. Cloudflare Worker proxy (free tier) for zero-setup AI, with
    per-install rate limiting — only after deciding who pays when
    usage grows.
11. Background pre-digests via WorkManager + cloud — only once Phase 3
    exists, since Nano can't do background at all.

**Do NOT build:** a Muse API leg (paid, no free tier — add later as a
paid per-user-key leg only if he asks); Together / Cerebras / HF as
free legs (their terms changed — see §9.3); GitHub Models until its
status is re-verified; an embedded key in the public repo; a proxy
server now; Accessibility-based reading; bundling a multi-GB model
into the APK.

**What needs Anthony (his phone tests):**
- (a) Nano `checkFeatureStatus` in sideloaded Box on his Pixel 9 Pro
  (does ML Kit GenAI work sideloaded?).
- (b) Qwen 0.5B download (~550 MB, Wi-Fi) + one "Catch me up" digest
  on his phone — the proof the LiteRT leg works.
- (c) Later, one free Groq key (console.groq.com, ~2 min, no card)
  pasted into Box to test the cloud leg.

## 9.7 What I could not verify (new, on top of §8)

- **Gemma 4 E2B file size:** sources disagree (~840 MB mobile QAT
  build in the first round's research vs ~2.4 GB in the flutter_gemma
  table). Verify against the `litert-community` HF repo before
  promising a download size in the UI.
- **Gemma gating on `litert-community`:** one source (Sep 30, 2026)
  lists the Gemma 4 E2B litertlm repo as Apache-2.0 / not gated, while
  older Gemma repos are gated. Check the model card at build time —
  if it's genuinely ungated, Gemma 4 E2B becomes a much better
  default than Qwen 0.5B.
- **GitHub Models status:** one source claims it was fully retired
  Jul 30, 2026; others describe it live through Oct 2026. Do not
  build on it until checked against docs.github.com.
- **Cerebras free tier:** mid-2026 "1M tokens/day free, no card" vs
  Oct 2026 "card-required $5 trial" conflict. The official
  rate-limits page (inference-docs.cerebras.ai/support/rate-limits)
  is the authority — re-check it at build time.
- **Muse Spark contributor tier** ($0.10 / $0.20, one aggregator) vs
  standard $1.25 / $4.25 (Meta's blog) — unverified which tier a
  self-serve signup gets.
- **All free-tier numbers** (Groq, Gemini, OpenRouter) change often —
  re-check the provider console at build time; treat every number
  here as an Oct–Nov 2026 snapshot, not a contract.
- **LiteRT-LM minSdk** — Box is minSdk 26; MediaPipe needed API 26+,
  LiteRT-LM is likely the same, unconfirmed. Verify at build time.
- **ML Kit GenAI minSdk 26 vs 31** (carried from §8) — still open.

## Sources (added in this refinement)

- LiteRT-LM supersedes MediaPipe LLM Inference: dev.to
  "MediaPipe LLM Inference is maintenance-only: start on LiteRT-LM"
  (Oct 17, 2026); Medium "LiteRT vs TensorFlow Lite" (Sep 5, 2026);
  irurueta.com Android AI docs (Sep 30, 2026).
- Fallback-chain precedent (DroidKaigi 2026 app): GitHub
  `m15-ai/droidkaigi2026-app-gvp` README — LiteRT → Gemini Nano →
  stub, user-selectable models, Qwen 0.5B ~546 MB non-gated,
  Phi-4-mini ~4 GB, Gemma3-1B-IT ~1 GB gated (HF token).
- On-device model sizes: flutter_gemma model tables (Gemma 4 E2B
  2.4 GB, Phi-4-mini 3.9 GB, Qwen3 0.6B 586 MB, Gemma 3 1B 0.5 GB,
  Llama 3.2 1B 1.1 GB, TinyLlama 680 MB); MLHive LLM Inference
  supported-models matrix (Gemma 3 1B ~529 MB, 45–65 tok/s).
- Groq free tier: console.groq.com/docs/rate-limits via byoklist.com,
  toolfreebie.com, apidog.com (Sep–Oct 2026) — 30 RPM / 1,000 RPD /
  8K TPM / 200K TPD; Llama now Enterprise.
- OpenRouter free models: OpenRouter limits docs via fast.io,
  ampm-aiops.com, continuumcode.ai, pinggy.io (Aug–Oct 2026) — 20 RPM,
  50 RPD unfunded / 1,000 RPD after $10 lifetime purchase.
- Meta Muse / Llama API: dev.meta.ai blog "Build with Muse Spark"
  (Jul 2026) — $1.25/$4.25, api.meta.ai/v1, muse-spark-1.1;
  flatkey.ai "Llama API (2026)" (Oct 22, 2026) — no free self-serve
  Llama API; llama.com redirects to dev.meta.ai; technspire.com
  "Muse and the EU" (Sep 2026); coursium.ai "Is Muse Spark Open
  Safe?" (Oct 2026) — Spark Open ~1.5 TB checkpoint.
- Together AI: billing docs via grabaicredits.com (Oct 17, 2026) —
  no free trials, min $15 purchase; saaspuppy.com (Oct 2026).
- Cerebras: inference-docs.cerebras.ai/support/rate-limits via
  saaspuppy.com / opengpuradar.com (Oct 2026) — $5 card-required
  trial, 5 RPM / 30K TPM / 1M tokens/day; vs genai.club / free
  LLM API roundups (mid-2026) — 1M tokens/day free, no card.
- Hugging Face Inference: klymentiev.com (Sep 11, 2026) and
  aifree.dev (Oct 21, 2026) — no free Inference Providers credits;
  router.huggingface.co/v1; PRO $9/mo includes $2.
- GitHub Models: docs.github.com rate limits via toolchew.com /
  fast.io (Jun–Oct 2026) — 15 RPM / 150 RPD low tier; fast.io
  claims retirement Jul 30, 2026 (conflicts with later sources).
