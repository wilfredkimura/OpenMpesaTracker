# Agent Guidelines & Engineering Standards

This document establishes mandatory development rules, quality gates, and code standards for all AI agents and developers working on the **OpenMpesaTracker** codebase.

---

## 1. Mandatory Unit Testing per Subphase
- **Zero Incomplete Subphases Without Tests**: Never mark any subphase as complete until corresponding unit tests have been written, run, and verified to pass.
- **Comprehensive Test Coverage**: All critical paths—including regex parsing engines, Room database entities and DAOs, background sync workers, ContentProvider readers, and report exporters—must have dedicated unit tests validating standard flows, boundary conditions, and edge cases.

---

## 2. Implementation Tracking & Commit Protocols (`task.md`)
- **Single Source of Implementation Truth**: All features and work items must be systematically mapped out in [task.md](file:///c:/Users/kimushzyyy/Desktop/kim%20projects/OpenMpesaTracker/task.md) with defined phases and discrete subphases.
- **Subphase Commit Checkpoints**:
  - Every single subphase must define an explicit commit checkpoint.
  - **Git commits must ONLY happen after unit tests have successfully passed**. Under no circumstances should unverified or failing code be committed to version control.
  - Check off each subphase in [task.md](file:///c:/Users/kimushzyyy/Desktop/kim%20projects/OpenMpesaTracker/task.md) only after tests pass and the commit checkpoint is completed.

---

## 3. Clear, Plain-Language Code Comments
- **Detailed, Plain-Language Explanations**: All code files must feature clear, thorough comments explaining the purpose of classes, functions, parameters, and algorithms.
- **No Complex Jargon**: Comments must avoid unnecessarily complex technical jargon or cryptic abbreviations. Explain domain logic simply (for example, explaining Safaricom SMS formatting rules, PDU extraction, regex lookaheads, and Android lifecycle constraints) so that any open-source contributor can understand, maintain, and expand the codebase with ease.
