-- AutoBank Full Database Setup (PostgreSQL)
-- Step 1: Create the database
-- CREATE DATABASE autobank;
-- \c autobank;

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'OPERATOR',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS login_sessions (
    id SERIAL PRIMARY KEY,
    user_id INTEGER,
    login_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    logout_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS accounts (
    id SERIAL PRIMARY KEY,
    account_number VARCHAR(20) UNIQUE NOT NULL,
    holder_name VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    address TEXT,
    balance NUMERIC(15,2) DEFAULT 0.00 NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_acct_name ON accounts (holder_name);

CREATE TABLE IF NOT EXISTS transactions (
    id SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    balance_before NUMERIC(15,2) NOT NULL,
    balance_after NUMERIC(15,2) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    operator_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reference_id INTEGER,
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (operator_id) REFERENCES users(id),
    FOREIGN KEY (reference_id) REFERENCES transactions(id)
);

CREATE INDEX IF NOT EXISTS idx_tx_account ON transactions (account_id);
CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions (created_at);

CREATE TABLE IF NOT EXISTS daily_sessions (
    id SERIAL PRIMARY KEY,
    date DATE UNIQUE NOT NULL,
    opening_balance NUMERIC(15,2),
    closing_balance NUMERIC(15,2),
    expected_cash NUMERIC(15,2),
    actual_cash NUMERIC(15,2),
    status VARCHAR(20) DEFAULT 'OPEN' NOT NULL,
    opened_by INTEGER,
    closed_by INTEGER,
    opened_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    FOREIGN KEY (opened_by) REFERENCES users(id),
    FOREIGN KEY (closed_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS loans (
    id SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    interest_rate NUMERIC(5,2) NOT NULL,
    installment_amount NUMERIC(15,2),
    total_paid NUMERIC(15,2) DEFAULT 0.00,
    outstanding NUMERIC(15,2),
    disbursed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    due_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    operator_id INTEGER,
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (operator_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_loans_acct ON loans (account_id);

CREATE TABLE IF NOT EXISTS loan_payments (
    id SERIAL PRIMARY KEY,
    loan_id INTEGER NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    principal_component NUMERIC(15,2),
    interest_component NUMERIC(15,2),
    paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    operator_id INTEGER,
    transaction_id INTEGER,
    FOREIGN KEY (loan_id) REFERENCES loans(id),
    FOREIGN KEY (operator_id) REFERENCES users(id),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE TABLE IF NOT EXISTS payment_distributions (
    id SERIAL PRIMARY KEY,
    import_file VARCHAR(255),
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount NUMERIC(15,2),
    total_records INTEGER,
    matched_records INTEGER,
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    operator_id INTEGER,
    FOREIGN KEY (operator_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS distribution_records (
    id SERIAL PRIMARY KEY,
    distribution_id INTEGER NOT NULL,
    account_id INTEGER,
    holder_name VARCHAR(100),
    amount NUMERIC(15,2),
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    error_message TEXT,
    transaction_id INTEGER,
    FOREIGN KEY (distribution_id) REFERENCES payment_distributions(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id SERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id INTEGER,
    description TEXT,
    operator_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (operator_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_audit_date ON audit_log (created_at);
