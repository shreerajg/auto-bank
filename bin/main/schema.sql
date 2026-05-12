-- AutoBank Schema (auto-applied on first startup)

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
    user_id INT REFERENCES users(id),
    login_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    logout_at TIMESTAMP,
    status VARCHAR(20) NOT NULL
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

CREATE TABLE IF NOT EXISTS transactions (
    id SERIAL PRIMARY KEY,
    account_id INT NOT NULL REFERENCES accounts(id),
    type VARCHAR(30) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    balance_before NUMERIC(15,2) NOT NULL,
    balance_after NUMERIC(15,2) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    operator_id INT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reference_id INT REFERENCES transactions(id)
);

CREATE TABLE IF NOT EXISTS daily_sessions (
    id SERIAL PRIMARY KEY,
    date DATE UNIQUE NOT NULL,
    opening_balance NUMERIC(15,2),
    closing_balance NUMERIC(15,2),
    expected_cash NUMERIC(15,2),
    actual_cash NUMERIC(15,2),
    status VARCHAR(20) DEFAULT 'OPEN' NOT NULL,
    opened_by INT REFERENCES users(id),
    closed_by INT REFERENCES users(id),
    opened_at TIMESTAMP,
    closed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS loans (
    id SERIAL PRIMARY KEY,
    account_id INT NOT NULL REFERENCES accounts(id),
    amount NUMERIC(15,2) NOT NULL,
    interest_rate NUMERIC(5,2) NOT NULL,
    installment_amount NUMERIC(15,2),
    total_paid NUMERIC(15,2) DEFAULT 0.00,
    outstanding NUMERIC(15,2),
    disbursed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    due_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    operator_id INT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS loan_payments (
    id SERIAL PRIMARY KEY,
    loan_id INT NOT NULL REFERENCES loans(id),
    amount NUMERIC(15,2) NOT NULL,
    principal_component NUMERIC(15,2),
    interest_component NUMERIC(15,2),
    paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    operator_id INT REFERENCES users(id),
    transaction_id INT REFERENCES transactions(id)
);

CREATE TABLE IF NOT EXISTS payment_distributions (
    id SERIAL PRIMARY KEY,
    import_file VARCHAR(255),
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount NUMERIC(15,2),
    total_records INT,
    matched_records INT,
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    operator_id INT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS distribution_records (
    id SERIAL PRIMARY KEY,
    distribution_id INT NOT NULL REFERENCES payment_distributions(id),
    account_id INT REFERENCES accounts(id),
    holder_name VARCHAR(100),
    amount NUMERIC(15,2),
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    error_message TEXT,
    transaction_id INT REFERENCES transactions(id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id SERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id INT,
    description TEXT,
    operator_id INT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tx_account   ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_tx_date      ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_acct_name    ON accounts(holder_name);
CREATE INDEX IF NOT EXISTS idx_audit_date   ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_loans_acct   ON loans(account_id)
