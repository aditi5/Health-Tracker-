# Health Tracker · Luma

A Django API and **one shared Kotlin Multiplatform app** for Android and iOS. The mobile UI, API handler, token refresh, models, and state live in `mobile/composeApp/src/commonMain`. Android and iOS only have thin platform launchers. A desktop target previews the same UI during development.

This is an initial implementation, not full Clue feature parity. It uses original UI and independently written code.

## Included

- Email/password registration and sign-in, rotating JWT refresh tokens, sign-out.
- Period history and daily flow, symptom, mood and note tracking.
- Monthly calendar, cycle-day overview and basic insights.
- Editable cycle assumptions and timezone settings.
- Per-user JSON data export and account deletion.
- Server-owned calendar estimates based on the median interval between logged period starts.

Estimates are informational. They do not establish ovulation, provide diagnosis, or support contraception. The starting settings of 28 cycle days and 5 period days are editable product defaults, not measured values or Clue's algorithm.

## Run Django

Requires Python 3.12+.

```sh
cd backend
python3.12 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python manage.py migrate
.venv/bin/python manage.py runserver 0.0.0.0:8000
```

SQLite is used locally. For PostgreSQL set `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and optionally `POSTGRES_HOST`/`POSTGRES_PORT` in the process environment. Do not commit credentials.

```sh
cd backend
.venv/bin/python manage.py test
.venv/bin/python manage.py check
```

For stable local sessions, supply `DJANGO_SECRET_KEY` through your process environment. Without it, development generates an ephemeral key per process, so a server restart expires sessions. Outside development, `DJANGO_DEBUG=0` requires an explicit key and enforces HTTPS. Set `DJANGO_ALLOWED_HOSTS` for the deployed hostname. This repository does not deploy a public backend.

Throttle defaults are development starting points: 30 anonymous, 300 authenticated and 10 authentication requests per minute. Override through `API_ANON_RATE`, `API_USER_RATE` and `API_AUTH_RATE`; they are not measured capacity limits.

## Run the shared mobile app

Requires JDK 17, Android Studio with Android SDK 36, and the Android emulator. Open `mobile/` in Android Studio, sync Gradle, and run `composeApp`. The emulator's development URL is `http://10.0.2.2:8000/api/v1`.

```sh
cd mobile
sh ./gradlew :composeApp:assembleDebug
sh ./gradlew :composeApp:desktopTest
sh ./gradlew :composeApp:run
```

To set an Android API endpoint use `-PapiBaseUrl=https://your-backend.example/api/v1`; replace the example hostname with your deployment. Cleartext HTTP is enabled only in the Android debug source set. Release builds require a reachable HTTPS API.

### iOS

Install full Xcode and XcodeGen using their official distributions. Select your Xcode developer directory, then:

```sh
cd mobile/iosApp
xcodegen generate
open Luma.xcodeproj
```

Choose a simulator and run. The Xcode prebuild phase compiles and embeds `HealthTracker.framework` from the same Kotlin code. Debug uses `http://localhost:8000/api/v1`. For a physical device, set `API_BASE_URL` to a reachable backend. For Release, set it to HTTPS; the build phase rejects missing or insecure release URLs. Set your own Apple signing team and bundle identifier before device/distribution builds.

The Swift file contains only the app launcher and Compose host. There is no separate iOS business-logic or screen implementation.

## API

All paths start with `/api/v1/`. Authenticated requests use `Authorization: Bearer <access>`.

| Endpoint | Methods | Purpose |
|---|---|---|
| `auth/register/`, `auth/login/` | POST | Credentials in JSON: `email`, `password` |
| `auth/refresh/`, `auth/logout/` | POST | JSON `refresh` token; rotation or revocation |
| `profile/` | GET, PATCH | Display name, timezone, cycle assumptions |
| `periods/`, `periods/{id}/` | GET, POST; GET, PUT, PATCH, DELETE | User-owned period history |
| `logs/YYYY-MM-DD/` | GET, PUT, DELETE | Daily entry; PUT replaces the complete day |
| `calendar/?month=YYYY-MM` | GET | Entries for a month plus user period history |
| `summary/` | GET | Date-aware estimate and history statistics |
| `catalog/` | GET | Supported flow, mood and symptom options |
| `account/` | GET, DELETE | Export owned records or delete account |

## Current limits and next work

- Sessions are in memory on mobile. Restarting requires sign-in. Keychain/Android Keystore persistence and offline sync are not implemented.
- Data export currently displays JSON in the app; native save/share integration is pending.
- Email verification, password reset, notifications, HealthKit/Health Connect, subscriptions, educational content, pregnancy/perimenopause modes, reports, community, sharing and optional AI features are not implemented.
- No fertility or symptom-prediction model has been ported from Clue. Its server algorithm is not present in the APK evidence inspected.
- Logout revokes the refresh token. An already issued access token can remain valid for its 15-minute lifetime. Account deletion invalidates access because the user no longer exists.
- Production deployment needs a persistent database, a stable signing secret, HTTPS, backups and restore testing, appropriate privacy/consent flows and verified device builds.

See [architecture](docs/ARCHITECTURE.md) and [feature roadmap](docs/ROADMAP.md). APKs, decompiled source, third-party app assets, local databases and credentials are excluded from this repository.
