-- AutoBank Schema (auto-applied on first startup)
-- Adapted for MySQL

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'OPERATOR',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS login_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    login_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    logout_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS accounts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(20) UNIQUE NOT NULL,
    holder_name VARCHAR(100) NOT NULL,
    phone VARCHAR(15),
    address TEXT,
    balance NUMERIC(15,2) DEFAULT 0.00 NOT NULL,
    interest_rate NUMERIC(5,2) DEFAULT 4.00 NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_acct_name (holder_name)
);

CREATE TABLE IF NOT EXISTS transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INT NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    balance_before NUMERIC(15,2) NOT NULL,
    balance_after NUMERIC(15,2) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    operator_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reference_id INT,
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (operator_id) REFERENCES users(id),
    FOREIGN KEY (reference_id) REFERENCES transactions(id),
    INDEX idx_tx_account (account_id),
    INDEX idx_tx_date (created_at)
);

CREATE TABLE IF NOT EXISTS daily_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    date DATE UNIQUE NOT NULL,
    opening_balance NUMERIC(15,2),
    closing_balance NUMERIC(15,2),
    expected_cash NUMERIC(15,2),
    actual_cash NUMERIC(15,2),
    status VARCHAR(20) DEFAULT 'OPEN' NOT NULL,
    opened_by INT,
    closed_by INT,
    opened_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    FOREIGN KEY (opened_by) REFERENCES users(id),
    FOREIGN KEY (closed_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS loans (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INT NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    interest_rate NUMERIC(5,2) NOT NULL,
    installment_amount NUMERIC(15,2),
    total_paid NUMERIC(15,2) DEFAULT 0.00,
    outstanding NUMERIC(15,2),
    disbursed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    due_date DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    operator_id INT,
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (operator_id) REFERENCES users(id),
    INDEX idx_loans_acct (account_id)
);

CREATE TABLE IF NOT EXISTS loan_payments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    loan_id INT NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    principal_component NUMERIC(15,2),
    interest_component NUMERIC(15,2),
    paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    operator_id INT,
    transaction_id INT,
    FOREIGN KEY (loan_id) REFERENCES loans(id),
    FOREIGN KEY (operator_id) REFERENCES users(id),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE TABLE IF NOT EXISTS payment_distributions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    import_file VARCHAR(255),
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount NUMERIC(15,2),
    total_records INT,
    matched_records INT,
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    operator_id INT,
    FOREIGN KEY (operator_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS distribution_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    distribution_id INT NOT NULL,
    account_id INT,
    holder_name VARCHAR(100),
    amount NUMERIC(15,2),
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    error_message TEXT,
    transaction_id INT,
    FOREIGN KEY (distribution_id) REFERENCES payment_distributions(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id INT,
    description TEXT,
    operator_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (operator_id) REFERENCES users(id),
    INDEX idx_audit_date (created_at)
);
INT AUTO_INCREMENT PRIMARY KEY,
    month INT NOT NULL,
    year INT NOT NULL,
    batch_type VARCHAR(20) NOT NULL, -- 'SAVINGS' or 'LOAN'
    total_amount NUMERIC(15,2) NOT NULL,
    record_count INT NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    operator_id INT,
    FOREIGN KEY (operator_id) REFERENCES users(id),
    UNIQUE INDEX idx_batch_period (month, year, batch_type)
);
