# CODEX RULES

## 1. Scope discipline
Codex may only perform the explicitly requested task.

## 2. No unrelated modifications
Codex must not modify unrelated modules, files, architecture or workflows.

## 3. Mandatory stop rule
If requested work requires additional changes:
- stop
- explain why
- list files/modules affected
- wait for approval

## 4. Every task must contain
- goal
- allowed files
- forbidden files
- acceptance criteria

## 5. Mandatory completion report
After every task Codex must report:
- changed files
- reason for each change
- build status
- generated APK artifacts

## 6. Failure handling
If a change fails:
- do not improvise
- do not continue broad refactors
- revert or stop

## 7. Frozen modules
Modules listed in FROZEN_MODULES.md may not be modified without explicit approval.

## 8. Build discipline
Every task must end with:
- clean build
- GitHub Actions green
- APK artifacts generated

## 9. No feature explosion
Do not redesign the app while fixing bugs.

## 10. Main principle
Stability is more important than adding features.
