# AutoBank: Smart Cooperative Banking & Financial Automation System

## Project Overview
AutoBank is a professional, reliable, offline-first desktop financial management system designed specifically for rural cooperative societies (Patsanstha / Dairy-based financial cooperatives). It modernizes traditional banking workflows while preserving the operational structure used by these societies.

**Key Goals:**
- Reduce manual paperwork and calculation errors.
- Automate Excel/PDF workflows for dairy-linked payments.
- Provide reliable transaction logging and comprehensive reporting.
- Ensure high data integrity and transaction safety.
- Operate efficiently on low-end hardware in offline environments.

## Technology Stack
- **Frontend:** JavaFX (Modern, lightweight, keyboard-friendly UI with Marathi/English support).
- **Backend:** Java (Modular architecture for business logic and transaction engine).
- **Database:** PostgreSQL (Ensuring transaction safety, rollback, and data integrity).
- **Automation Engine:** Python (Handling Excel/PDF parsing, report generation, and analytics).

## Project Structure (Planned)
The system is organized into the following functional modules:
1.  **Authentication & Security:** User login and session management.
2.  **Account Management:** Customer/farmer records and balance tracking.
3.  **Transaction Engine:** Deposits, withdrawals, and cashbook handling.
4.  **Payment Distribution:** Importing and processing dairy-generated payments.
5.  **Loan Management:** Small-scale rural cooperative loan tracking.
6.  **Daily Operations:** Day opening/closing and cash verification.
7.  **Report Engine:** Automated PDF/Excel generation (Daily, Monthly, Yearly).
8.  **Analytics Dashboard:** Visualizing trends using Python-generated graphs.
9.  **Import/Export Engine:** Bulk data processing from Excel/PDF/CSV.
10. **Backup & Recovery:** Critical automated and manual backup systems.
11. **Settings:** Language toggles and system configurations.
12. **Audit & Logging:** Comprehensive event logs for all system actions.

## Development Status
- **Phase:** Conceptual / Requirements Definition.
- **Current Files:**
    - `Auto bank system information .md`: Detailed project specification and requirements.
- **Next Steps:**
    - Initialize JavaFX/Java project structure.
    - Set up PostgreSQL database schema.
    - Implement core authentication and account management modules.

## Key Development Principles
- **Offline-First:** All core operations must work without internet.
- **Transaction Safety:** No permanent deletion; all edits/deletes are logged with status updates.
- **Reliability:** System must be resilient to power cuts and improper shutdowns.
- **Simplicity:** Optimized for single-operator usage with minimal navigation complexity.
- **Consistency:** Prioritize financial accuracy and stability over flashy design.

## Placeholder Commands (TODO)
Once the project structure is initialized, document the following:
- **Build:** `[TODO: Build command, e.g., mvn clean install]`
- **Run:** `[TODO: Run command, e.g., mvn javafx:run]`
- **Test:** `[TODO: Test command, e.g., mvn test]`
