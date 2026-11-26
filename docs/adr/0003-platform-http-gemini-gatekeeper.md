# ADR-0003: Replace Google Generative AI SDK with Platform `HttpURLConnection`

- **Status:** Accepted
- **Date:** 2026-11-26

## Context
`baseline-v1` depended on `com.google.ai.client.generativeai:generativeai:0.9.0`, which transitively pulled Ktor (`9,033` DEX methods), OkHttp (`2,761` methods + `41 KB` `publicsuffixes.gz`), and `kotlinx.serialization` (`2,738` methods) — over 16,500 DEX methods and ~1.46 MB of APK weight — solely to invoke a single REST endpoint (`POST /v1beta/{model}:generateContent`). Model listing (`GET /v1beta/models`) already used `java.net.HttpURLConnection`.

## Decision
Introduce a transport-agnostic `GatekeeperClient` interface in `feature/gatekeeper/domain` and implement `HttpGeminiGatekeeperClient` directly over platform `java.net.HttpURLConnection` and `org.json.JSONObject`, downscaling screenshot attachments to ≤1024px JPEG (`BitmapImageEncoder`).

## Consequences
- **Positive:** Removed 65 transitive artifacts and ~20,793 pre-R8 DEX references, reduced the APK from 2 DEX files to 1, eliminated Ktor/OkHttp/Gson, and enabled hermetic loopback HTTP unit tests without mocking SDK internals.
- **Negative:** Direct REST payload construction must track Gemini `v1beta` schema changes manually (isolated inside `HttpGeminiGatekeeperClient.kt`).
