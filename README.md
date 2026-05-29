# CutStock (کات‌استاک)

Android app for offline rebar cutting-stock optimization with Persian RTL UI.

## Features

- Multi-stock lengths, kerf, diameter, and configurable steel price
- Native C++ solver (FFD + local improvement)
- Project management with Room persistence
- Freemium: free tier limits; Pro unlocks PDF export, backup, advanced reports
- Billing abstraction (Debug toggle / Stub); Cafe Bazaar integration placeholder

## Build

Requirements: JDK 17, Android SDK 34, NDK 27, CMake 3.22.

```bash
# Windows
gradlew.bat assembleDebug

# Linux / CI
./scripts/bootstrap.sh
./scripts/gradle.sh assembleDebug
```

Unit tests:

```bash
gradlew.bat testDebugUnitTest
```

## Architecture

- `presentation/` — Activities, ViewModels, adapters
- `domain/` — Sales, backup, PDF, billing, parsers
- `data/` — Room, repository, preferences
- `nativecore/` — JNI solver bridge
- `app/src/main/cpp/` — Cutting solver

## Application ID

Release package: `ir.cutstock.app`

## Cafe Bazaar

Replace `StubBillingManager` with `CafeBazaarBillingManager` (Poolakey) in a release PR.
