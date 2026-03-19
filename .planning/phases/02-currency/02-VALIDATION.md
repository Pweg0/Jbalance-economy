---
phase: 02
slug: currency
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-19
---

# Phase 02 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (jupiter) |
| **Config file** | build.gradle (testImplementation needed) |
| **Quick run command** | `./gradlew test` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew build`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 20 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | CURR-01, CURR-02 | build | `./gradlew build` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 1 | CURR-03 | build | `./gradlew build` | ❌ W0 | ⬜ pending |
| 02-02-02 | 02 | 1 | CURR-04 | build | `./gradlew build` | ❌ W0 | ⬜ pending |
| 02-03-01 | 03 | 2 | CURR-05, CURR-06, CURR-07 | build | `./gradlew build` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] JUnit 5 `testImplementation` dependency in build.gradle — if tests are added
- [ ] Existing `./gradlew build` covers compilation verification for all tasks

*Existing infrastructure covers compilation verification. Full behavioral testing requires a running NeoForge server (manual).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| /eco balance shows PT-BR formatted balance | CURR-01 | Requires running MC server with mod loaded | Join server, run /eco balance, verify §6[JBalance] prefix and J$ format |
| /eco pay transfers atomically | CURR-03 | Requires two players on server | Two players online, sender runs /eco pay, verify both balances changed |
| /eco top shows ranked list | CURR-04 | Requires server with multiple player records | Multiple players with balances, run /eco top, verify ordering |
| /ecoadmin works on offline players | CURR-05-07 | Requires server with DB records | Admin runs /ecoadmin give on offline player, verify DB updated |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 20s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
