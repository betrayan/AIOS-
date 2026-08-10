# Buddy AI OS — README

<p align="center">
  <img src="docs/buddy_logo_placeholder.png" width="120" alt="Buddy AI OS Logo" />
</p>

<h1 align="center">Buddy AI OS</h1>
<p align="center"><strong>Production-grade Android AI Companion</strong></p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-API%2028%2B-green?logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-blue?logo=kotlin" />
  <img src="https://img.shields.io/badge/Compose-2024.10-purple?logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/Architecture-Clean%20MVVM-orange" />
</p>

---

## Architecture Overview

Buddy AI OS is built with **Clean Architecture + MVVM** in a **Gradle multi-module** structure:

```
:app                          ← Navigation host, Application class
├── :feature:chat             ← Chat screen, ChatViewModel, ChatUiState
├── :feature:home             ← Dashboard, conversation list
├── :feature:memory           ← Long-term memory browser
├── :feature:settings         ← Privacy controls, persona selection
├── :feature:onboarding       ← First-run experience
├── :core:domain              ← Pure Kotlin: entities, use cases, interfaces
├── :core:data                ← Repository implementations, mappers
├── :core:ai                  ← AIEngine interface, HybridAIEngine, AIPolicy
├── :core:database            ← Room DB, DAOs, entities
├── :core:network             ← Retrofit, OkHttp, interceptors
├── :core:security            ← Android Keystore, encryption, biometric
├── :core:ui                  ← Design system, shared composables
├── :core:analytics           ← Firebase Analytics, privacy gate
├── :core:common              ← Dispatchers, Logger, extensions
└── :workers                  ← WorkManager workers
```

## Key Principles

- **Offline-First**: Every feature works without network.
- **Privacy by Default**: LOCAL_ONLY users never have data sent to cloud AI.
- **Encrypted at Rest**: Message content and memories AES-GCM encrypted via Android Keystore.
- **Battery-Conscious**: No persistent foreground services. All background work via WorkManager.
- **Testable**: Every use case, ViewModel, and repository is independently unit-testable.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose (Material3) |
| DI | Hilt |
| Navigation | Navigation Compose |
| Database | Room |
| AI (on-device) | MediaPipe LLM Inference (Gemma) |
| AI (cloud) | Gemini API |
| Background | WorkManager |
| Security | Android Keystore + EncryptedSharedPreferences |
| Analytics | Firebase Analytics (PII-free) |

## Building

```bash
./gradlew assembleDebug
```

## Running Tests

```bash
./gradlew :core:domain:test
./gradlew :core:ai:test
./gradlew :feature:chat:test
```

## Module Dependency Graph

```
:app
 └── :feature:*
      └── :core:domain, :core:ui, :core:common (via AndroidFeaturePlugin)
           └── :core:data
                ├── :core:database → :core:security
                ├── :core:network  → :core:security
                └── :core:ai       → :core:domain
```

**Strict rule**: Feature modules CANNOT depend on each other.

---

*Architecture by Buddy AI OS Chief Software Architect*
