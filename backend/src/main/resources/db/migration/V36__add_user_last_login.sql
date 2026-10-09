CREATE TABLE user_logins
(
    user_id       VARCHAR(255) PRIMARY KEY,
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL
);
