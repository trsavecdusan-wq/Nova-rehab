# NovaRehab MVP Architecture

## Repository Overview
NovaRehab is a two-module Android repository with a documentation layer around it:

- `app` is the patient-facing tablet application.
- `companion` is the contact-side companion application.
- `docs` stores operating rules, architectural guardrails, and task guidance.

The current repository direction is MVP stabilization, not platform redesign. Recent work has concentrated on main screen stability, settings safety, companion startup safety, communication paging consistency, and updater reliability.

## App Module Role
The `app` module is the main NovaRehab tablet experience. It currently acts as the orchestration layer for:

- the patient-facing home screen
- AAC / communication grid presentation
- speech and translation entry points
- radio, gallery, mirror, and navigation entry points
- settings/admin access
- update prompts and rollback hooks
- companion incoming-call polling and media inbox polling

`MainActivity` is the central runtime hub of the MVP. It wires together UI, preferences, communication paging, background workers, update entry points, media polling, incoming call checks, and kiosk behavior. This means the app module is functional, but the main screen architecture is sensitive and should be treated as a stabilization zone rather than a place for broad feature expansion.

## Companion Module Role
The `companion` module is the contact-side companion app used for communication with the tablet side. Its current role includes:

- selecting or restoring the active contact configuration
- importing shared config text sent from the tablet side
- bootstrapping a safe default contact when no valid saved config exists
- waiting for incoming calls
- placing outgoing test calls to the tablet
- handling active call state and basic image sending back to the tablet

The companion is therefore not just a video screen. It is a small bootstrap-and-call client with defensive startup behavior.

## Docs Role
The `docs` folder is part of runtime safety for future work. It exists to reduce guessing and keep tasks aligned with current architecture. It should be used to document:

- repo rules
- frozen or protected areas
- current MVP structure
- stabilization boundaries
- explicit non-goals

## Updater Status
The updater exists and is active in the tablet app. Current status:

- update metadata is read from the GitHub release `app-version.json`
- the app can check for updates, download the new APK, validate it, and open the Android installer
- install is not silent; Android confirmation is still required
- a backup APK is stored so rollback can be offered on failed post-update launch
- the current flow includes preparation states, timeout protection, and installer handoff messaging

Updater should be considered usable but still operationally sensitive. Real-world update success still depends on Android installer behavior, package identity continuity, signing continuity, and device testing.

## Companion Bootstrap Status
Companion bootstrap is partially stabilized and must stay narrow in scope.

Current state:

- shared text import from the tablet exists
- saved contact config is restored when valid
- invalid or missing config falls back to the default contact instead of blocking startup
- the companion can resume waiting for calls after bootstrap

Not yet established as architecture:

- QR onboarding is not the current bootstrap path
- file-based onboarding is not the current bootstrap path
- broader companion provisioning flows should not be assumed

## Main Screen Stability Warning
The main tablet screen is a protected stability area.

Important facts:

- the first screen has been repeatedly stabilized in recent work
- visible layout expectations are now tightly constrained
- top-area structure and bottom action readability have been treated as regression-sensitive
- unsafe on-screen controls have intentionally been hidden instead of forced into unstable layouts

Future tasks must assume that the main screen is fragile. Changes there should be minimal, explicit, and directly tied to a requested bug or stabilization task.

## AAC Communication Grid Status
The AAC communication grid is present and functioning within current MVP rules.

Current architecture assumptions:

- page sizes are stabilized around `4`, `9`, `16`, and `25`
- the language switch must not change the main icon structure
- submenu behavior exists, but it is intentionally bounded
- `MainActivity` currently limits submenu rendering to a small capped set instead of open-ended deep navigation
- recent work has focused on keeping paging behavior consistent rather than redesigning communication architecture

This means the grid is a stabilized MVP communication surface, not an invitation to add deeper menu systems or dynamic layout experiments.

## Settings Stabilization Note
Settings are under a stabilization-first architecture.

Current direction:

- broken or unsafe spinner/dropdown flows were replaced with tablet-safe fullscreen picker patterns
- settings work is being normalized toward one shared picker-style interaction model
- settings should be improved through consistency and readability, not by multiplying new control types

Any future settings task should preserve the picker-based direction unless a specific approved task says otherwise.

## MainActivity Protection Rule
`app/src/main/java/com/novarehab/ui/MainActivity.kt` is a protected file.

Rule:

- do not redesign `MainActivity` during unrelated fixes
- do not split or refactor it unless a task explicitly approves that scope
- preserve AppCompat lifecycle behavior, kiosk behavior, update hooks, communication setup, polling loops, and current home-screen assumptions
- when a task touches `MainActivity`, prefer the smallest safe patch that preserves current behavior outside the requested fix

## No Feature Explosion Rule
NovaRehab is currently in MVP stabilization mode.

Therefore:

- do not turn bug-fix tasks into architecture rewrites
- do not add adjacent features just because a module is already open
- do not widen scope from stabilization into redesign
- do not assume unfinished subsystems should be generalized now

## Stability Over Features Principle
The repository should be treated with one primary principle:

**stability is more important than adding features**

For this MVP, a smaller stable flow is better than a broader unstable one. When there is a tradeoff between feature breadth and patient-facing reliability, reliability wins.
