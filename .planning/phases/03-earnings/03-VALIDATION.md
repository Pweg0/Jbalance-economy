---
phase: 3
slug: earnings
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-19
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + NeoForge test harness |
| **Config file** | `build.gradle` (existing) |
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
| 03-01-01 | 01 | 1 | EARN-01 | unit | `./gradlew test` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | EARN-02 | unit | `./gradlew test` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 1 | EARN-03 | unit | `./gradlew test` | ❌ W0 | ⬜ pending |
| 03-02-01 | 02 | 1 | EARN-04 | unit | `./gradlew test` | ❌ W0 | ⬜ pending |
| 03-02-02 | 02 | 1 | EARN-05 | unit | `./gradlew test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Test stubs for EARN-01 through EARN-05
- [ ] Test fixtures for mock EconomyService and config

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mob kill notification appears in chat | EARN-01 | Requires in-game client | Kill a zombie, verify chat message format |
| Milestone notification with colors | EARN-04 | Requires in-game client | Play until 1h milestone, verify §a/§6 colors |
| AFK detection stops playtime counting | EARN-04 | Requires idle player test | Go AFK for 6+ minutes, verify playtime stops |
| Spawner mob gives no reward | EARN-03 | Requires spawner placement | Kill mob from spawner, verify no coins |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
