# Project Task Roadmap: Open Source M-Pesa Tracker (Alpha)

This roadmap tracks all implementation phases, subphases, testing requirements, and commit checkpoints for the **OpenMpesaTracker** Android application in strict compliance with [AGENTS.md](file:///c:/Users/kimushzyyy/Desktop/kim%20projects/OpenMpesaTracker/AGENTS.md).

> **Enforcement Rule**: A subphase cannot be marked complete without passing unit tests. Git commits must only be executed after all unit tests for that subphase have succeeded.

---

## Phase 1: Project Scaffolding & Setup

### Subphase 1.1: Gradle Build Configuration & App Manifest
- [x] Configure `settings.gradle.kts` and `app/build.gradle.kts` (Compose, Room, Coroutines).
- [x] Configure `AndroidManifest.xml` with `READ_SMS`, `RECEIVE_SMS`, and receiver/provider declarations.
- [x] **Unit Tests**:
  - [x] Build script validation and dependency resolution test (Passed with 100% success).
- [x] **Commit Hook**:
  - `git commit -m "chore: setup gradle build configuration and manifest"`

### Subphase 1.2: App Theme & Core Foundation
- [ ] Create Material 3 theme (`Color.kt`, `Theme.kt`, `Type.kt`).
- [ ] Create simple `AppContainer` for lightweight manual dependency injection.
- [ ] **Unit Tests**:
  - [ ] App container and configuration sanity unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat: setup material 3 design foundation"`

---

## Phase 2: Domain Models & Regex Parsing Engine

### Subphase 2.1: Transaction Entity & Enums
- [ ] Define `TransactionType` (PayBill, Buy Goods, Send Money Outbound, Pochi, Inbound).
- [ ] Define `TransactionDirection` (Inbound, Outbound).
- [ ] Define `MpesaTransactionEntity` with `@PrimaryKey val code: String` (10-char body code).
- [ ] **Unit Tests**:
  - [ ] Entity instantiation, data integrity, and direction mapping tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(domain): define transaction models and enums"`

### Subphase 2.2: Deterministic MpesaEngineParser
- [ ] Implement `MpesaEngineParser` with pre-compiled `java.util.regex.Pattern` singletons.
- [ ] Implement fast O(1) guard check for `"Confirmed"` token and 10-character prefix.
- [ ] Support the 5 verified 2026 blueprints (Paybill, Buy Goods, P2P Outbound, Pochi, P2P Inbound).
- [ ] Extract auxiliary balance and transaction fees.
- [ ] **Unit Tests**:
  - [ ] Comprehensive unit test suite with 15+ real-world anonymized M-Pesa SMS fixtures.
  - [ ] Test edge cases: amounts with commas, missing optional fees, masked phones, omitted phones in Pochi.
- [ ] **Commit Hook**:
  - `git commit -m "feat(engine): implement battery-efficient regex parsing engine"`

---

## Phase 3: Local Persistence Layer (Room Database)

### Subphase 3.1: Room Database & Type Converters
- [ ] Define `AppDatabase` with Room database builder.
- [ ] Implement `Converters` for `TransactionType` and `TransactionDirection`.
- [ ] **Unit Tests**:
  - [ ] Type converter serialization and deserialization unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(data): configure room database and type converters"`

### Subphase 3.2: TransactionDao Implementation
- [ ] Implement `TransactionDao` with upsert/insert ignoring duplicates on `code`.
- [ ] Implement query flows for chronological list, date range filters, and monthly totals.
- [ ] Implement category spending aggregation query.
- [ ] **Unit Tests**:
  - [ ] In-memory Room database DAO tests for deduplication, insertions, and aggregations.
- [ ] **Commit Hook**:
  - `git commit -m "feat(data): implement transaction dao with deduplication"`

### Subphase 3.3: TransactionRepository Implementation
- [ ] Create `TransactionRepository` abstract interface and concrete implementation.
- [ ] Expose clean Kotlin `Flow`s to presentation layer.
- [ ] **Unit Tests**:
  - [ ] Repository unit tests using fake DAO.
- [ ] **Commit Hook**:
  - `git commit -m "feat(data): implement transaction repository"`

---

## Phase 4: SMS Ingestion Pipeline

### Subphase 4.1: Historical SMS ContentProvider Reader
- [ ] Implement `MpesaHistoryReader` targeting `"content://sms/inbox"` with `address = 'MPESA'`.
- [ ] Implement batched cursor reading and parsing on `Dispatchers.IO`.
- [ ] **Unit Tests**:
  - [ ] Mock cursor iteration and batch parsing unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(ingestion): implement historical sms reader"`

### Subphase 4.2: Real-Time SMS BroadcastReceiver
- [ ] Implement `MpesaReceiver` handling `android.provider.Telephony.SMS_RECEIVED`.
- [ ] Safely extract PDUs, handle multi-part concatenated messages, and verify `MPESA` sender.
- [ ] Use `goAsync()` to persist parsed entity to Room without blocking the main thread.
- [ ] **Unit Tests**:
  - [ ] Broadcast receiver intent and PDU parsing unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(ingestion): implement real-time broadcast receiver"`

---

## Phase 5: Reporting & Export Engine

### Subphase 5.1: CSV Report Exporter
- [ ] Implement `CsvReportExporter` saving to `context.cacheDir/mpesa_reports/`.
- [ ] Write escaped CSV headers and rows with transaction code, amount, party, category, fee, and balance.
- [ ] **Unit Tests**:
  - [ ] CSV string formatting and comma escaping unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(export): implement csv report exporter"`

### Subphase 5.2: Multi-Page PDF Report Exporter
- [ ] Implement `PdfReportExporter` utilizing `android.graphics.pdf.PdfDocument`.
- [ ] Render clean summary headers, table columns, and dynamic page boundaries (A4).
- [ ] **Unit Tests**:
  - [ ] PDF document generation and line pagination unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(export): implement pdf report exporter"`

### Subphase 5.3: FileProvider & Android Share Sheet
- [ ] Add `res/xml/file_paths.xml` configuring `cache-path` for `mpesa_reports/`.
- [ ] Implement `ShareHelper` building `Intent.ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`.
- [ ] **Unit Tests**:
  - [ ] FileProvider URI construction and intent flags unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(export): configure fileprovider and share helper"`

---

## Phase 6: Presentation Layer & UI (Jetpack Compose)

### Subphase 6.1: Onboarding & SMS Permission Flow
- [ ] Build privacy assurance card explaining 100% offline local processing.
- [ ] Implement runtime permission launcher for `READ_SMS` and `RECEIVE_SMS`.
- [ ] **Unit Tests**:
  - [ ] Permission state handling and rationale display unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(ui): implement onboarding and permission flow"`

### Subphase 6.2: Financial Dashboard
- [ ] Build monthly income, expense, and net balance summary cards.
- [ ] Build category breakdown and recent transactions preview.
- [ ] **Unit Tests**:
  - [ ] Dashboard formatting and balance calculation unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(ui): implement main financial dashboard"`

### Subphase 6.3: Transaction List, Search & Filter Screen
- [ ] Implement transaction list with search by name/business.
- [ ] Add filters for Inbound vs Outbound and date ranges.
- [ ] Provide transaction detail bottom sheet with category editor and custom notes.
- [ ] **Unit Tests**:
  - [ ] Transaction filtering and search algorithm unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(ui): implement transaction list and filtering"`

### Subphase 6.4: Export Modal Sheet
- [ ] Build export modal allowing date range selection and format choice (CSV vs PDF).
- [ ] Trigger export coroutine with loading indicator and launch system Share Sheet.
- [ ] **Unit Tests**:
  - [ ] Export UI state machine unit tests.
- [ ] **Commit Hook**:
  - `git commit -m "feat(ui): implement export dialog and triggers"`
