# Feature roadmap

| Area | Current implementation | Remaining work |
|---|---|---|
| Accounts | Registration, login, token refresh, export, deletion | Verification, reset password, secure device session storage, consent onboarding |
| Periods | Create/list/remove periods; API also edits dates | Mobile end-date editing, richer cycle timeline |
| Daily tracking | Flow, ten symptom options, mood and notes | Broader configurable tracking catalog, custom tags, attachments if needed |
| Calendar | Logged periods and daily entries | Predictions as distinct visual layers, accessibility and localization review |
| Insights | Cycle/period estimates, interval count and variation | Symptom trends, charts, history controls and explainable uncertainty |
| Predictions | Independent median calendar estimate | Validated model, evaluation, versioning, explicit mode eligibility |
| Birth control | Not implemented | Schedules, methods and reminders |
| Pregnancy and perimenopause | Not implemented | Dedicated modes, tracking catalogs, reviewed education |
| Health integrations | Not implemented | Health Connect and HealthKit adapters, consent and background sync |
| Reminders | Not implemented | Local notification scheduling, timezone changes and permission flows |
| Offline | Not implemented | Local encrypted store, outbox, conflict handling and sync tests |
| Education | Not implemented | Original/reviewed content and search |
| Reports | Not implemented | PDF exports, report scope, native save/share |
| Sharing/community | Not implemented | Explicit access grants, revocation and moderation |
| Subscription | Not implemented | StoreKit/Play Billing, server purchase validation and entitlements |
| Optional AI | Not implemented | Separate consent, data boundaries, evaluated responses |
| Release | Development projects only | Device QA, signing, accessibility, privacy policies and store packaging |

Start by validating the included tracking flow on Android and iOS. Add offline storage and secure sessions before treating this as a daily-use product. Later features should follow agreed product requirements; packaged Clue route names alone are not a complete specification.
