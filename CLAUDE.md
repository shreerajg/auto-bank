# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

AutoBank is currently in the **requirements definition phase** — no implementation code exists yet. The next steps are:
1. Initialize the Java/JavaFX project structure (Maven)
2. Set up the PostgreSQL database schema
3. Implement core authentication and account management modules

## Build & Run Commands

*(To be filled in once the Maven project is initialized)*

```bash
mvn clean install       # Build
mvn javafx:run          # Run desktop app
mvn test                # Run all tests
mvn test -Dtest=<Name>  # Run a single test class
```

Python automation scripts will live in a separate directory and be invoked by the Java layer via process calls or a defined IPC mechanism.

## Architecture

**Four-layer system:**

| Layer | Technology | Role |
|---|---|---|
| UI | JavaFX | Desktop forms, sidebar navigation, bilingual (English/Marathi) |
| Backend | Java | Transaction engine, business logic, validation, audit logging |
| Database | PostgreSQL | Persistent financial records, rollback, atomic transactions |
| Automation | Python | Excel/PDF import-export, graph generation, report rendering, backups |

Java is the **primary transaction engine**. Python is a **side-channel automation layer** — it never writes directly to core financial tables. All critical financial state lives in PostgreSQL.

### 12 Functional Modules

1. **Authentication & Security** — login, session logging, role-based access (Admin / Operator)
2. **Account Management** — customer/farmer records, balance tracking, transaction history
3. **Transaction Engine** — deposits, withdrawals, adjustments, corrections, cashbook
4. **Payment Distribution** — import dairy payment files (Excel/PDF), match accounts, bulk credit
5. **Loan Management** — dairy loans, interest calculation, installments, overdue detection
6. **Daily Operations** — day open/close, expected-vs-actual cash verification, daily summaries
7. **Report Engine** — daily/monthly/yearly reports exported as PDF and Excel
8. **Analytics Dashboard** — Python-generated graphs for trends (collections, loans, profit)
9. **Import/Export Engine** — bulk Excel/PDF/CSV with duplicate detection and error reporting
10. **Backup & Recovery** — auto backup every 5–10 min, transaction snapshots, shutdown backup, crash recovery
11. **Settings** — language toggle, backup intervals, file paths, UI preferences
12. **Audit & Logging** — every transaction, edit, login, import, and backup logged with timestamp + operator

## Non-Negotiable Design Rules

**Transaction Safety:** No permanent deletion. Every edit or deletion must produce an audit record. Use status values `REVERSED`, `CORRECTED`, `MODIFIED` — never hard-delete financial records.

**Offline-First:** All core financial operations must work with zero internet. Internet is reserved for future optional features (cloud sync, AI).

**Backup-First:** Backup is a core financial safety feature, not an afterthought. Automatic timed backups, transaction snapshots, and a shutdown backup are required.

**Atomic Execution:** Every financial operation must use PostgreSQL transactions. Partial writes are never acceptable.

**DO NOT:**
- Use SQLite — PostgreSQL is required for rollback and audit reliability
- Add microservices or cloud-dependent features to core operations
- Hardcode UI text — use `en.json` / `mr.json` language files
- Prioritize UI aesthetics over financial consistency and stability

**Hardware target:** Windows 10/11, dual-core, low RAM. Keep the application lightweight; avoid heavy animations or GPU-dependent rendering.
