---
phase: 1
slug: foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-19
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle test task (JUnit 5 via NeoForge test framework) |
| **Config file** | build.gradle (created in Plan 01-01) |
| **Quick run command** | `./gradlew test` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 1 | INFR-01 | build | `./gradlew build` | ❌ W0 | ⬜ pending |
| 1-02-01 | 02 | 1 | CURR-08, INFR-02, INFR-03 | integration | `./gradlew test` | ❌ W0 | ⬜ pending |
| 1-02-02 | 02 | 1 | CURR-09 | integration | `./gradlew test` | ❌ W0 | ⬜ pending |
| 1-03-01 | 03 | 1 | INFR-04, INFR-05, CURR-10 | unit | `./gradlew test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `build.gradle` — NeoForge project with JUnit 5 test dependency
- [ ] `src/test/java/com/pweg0/jbalance/` — test source directory

*Test infrastructure is created as part of Plan 01-01 (project scaffold).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mod loads on dedicated server | INFR-01 | Requires running NeoForge server | Run `./gradlew runServer`, check no errors in log |
| Config hot-reload | INFR-04 | Requires runtime TOML edit | Edit config while server runs, verify values change |
| Currency display in chat | INFR-05 | Requires player chat observation | Join server, trigger balance message, verify format |
| Balance survives restart | CURR-08 | Requires server stop/start cycle | Set balance, restart server, check balance persists |
| New player initial balance | CURR-09 | Requires new player join | Join as new player, verify starting balance |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
