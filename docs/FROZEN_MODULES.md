# Frozen Modules

## Purpose
This document defines modules, files, and pipelines that may not be modified during MVP stabilization without explicit approval.

## Approval Rule
If a task touches any frozen module, file, config, or pipeline listed below, explicit approval is required before making the change.

## Frozen Application Files And Areas

### 1. Main Activity Protection
Protected file:

- `app/src/main/java/com/novarehab/ui/MainActivity.kt`

Protection reason:

- `MainActivity.kt` is the central runtime hub of the tablet MVP.
- It contains patient-facing home screen behavior, communication setup, update hooks, polling flows, and kiosk assumptions.
- It must not be broadly refactored or redesigned during stabilization work.

### 2. Updater Flow Protection
Protected area:

- tablet update flow
- backup-and-restore update behavior
- Android installer handoff behavior
- update state and timeout behavior

Protection reason:

- the updater is active but operationally sensitive
- update success still depends on package identity, signing continuity, installer behavior, and device testing
- unrelated tasks must not modify updater behavior

### 3. AAC Grid Protection
Protected area:

- AAC communication grid structure
- communication paging behavior
- language-independent main icon structure
- submenu boundary behavior

Protection reason:

- the AAC grid is currently stabilized around fixed paging expectations
- changes here can easily create patient-facing regressions
- the grid must not be redesigned during unrelated tasks

### 4. Companion Bootstrap Protection
Protected area:

- companion startup path
- saved contact restore flow
- fallback contact bootstrap behavior
- shared text import bootstrap flow

Protection reason:

- companion bootstrap has been narrowed to a defensive MVP path
- changing bootstrap logic without explicit approval can break startup, onboarding, or call readiness

## Frozen Infrastructure And Delivery Areas

### 5. GitHub Actions Protection
Protected area:

- `.github/**`

Protection reason:

- GitHub Actions are part of the controlled delivery path
- workflow edits can affect build, release, and update behavior across the repository

### 6. Signing Config Protection
Protected area:

- `signing/**`
- signing-related build configuration
- keystore and package identity continuity

Protection reason:

- signing continuity is required for reliable update installation
- accidental signing changes can block update testing and release installs

### 7. Release / Update Pipeline Protection
Protected area:

- release metadata generation
- update metadata publishing
- APK version sequencing
- package identity continuity
- release and update delivery pipeline behavior

Protection reason:

- the release/update pipeline is part of updater stability
- changes here can break update detection, install ordering, or rollback expectations

### 8. Dependency And Build-Version Freeze
Protected area:

- dependency versions
- Android Gradle Plugin version
- Kotlin version
- Compose BOM version
- `targetSdk` and `minSdk` values
- Gradle modernization work

Frozen rules:

- no automatic dependency upgrades
- no AGP upgrades
- no Kotlin version upgrades
- no Compose BOM upgrades
- no `targetSdk` changes
- no `minSdk` changes
- no Gradle modernization without explicit approval
- dependency upgrades require a dedicated approval task

Protection reason:

- stabilization work must not silently change the build platform under the app
- version upgrades can introduce regressions that are unrelated to the requested task
- dependency and build-version changes must be reviewed as their own scoped decision

## Forbidden Stabilization Behaviors

### 9. Broad Refactors Forbidden
During stabilization:

- broad refactors are forbidden without explicit approval
- cleanup work must not expand into module reshaping
- unrelated architecture changes must not be bundled into bug-fix tasks

### 10. Architecture Rewrites Forbidden During Stabilization
During stabilization:

- architecture rewrites are forbidden without explicit approval
- redesigning module responsibilities is forbidden
- replacing established MVP flows with wider systems is forbidden

## Working Rule
If a requested task affects any frozen module, file, configuration, or pipeline in this document:

- stop before editing
- explain why the frozen area is involved
- list the affected file or area
- wait for explicit approval
