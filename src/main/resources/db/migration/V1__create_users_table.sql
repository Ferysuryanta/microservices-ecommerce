CREATE TABLE users (

    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL default 'USER',
    enabled BOOLEAN NOT NULL default true,
    created_at TIMESTAMP NOT NULL default CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL default CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_enabled ON users (enabled);