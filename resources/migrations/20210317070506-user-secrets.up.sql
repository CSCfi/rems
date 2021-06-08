CREATE TABLE user_secrets (
  userId varchar(255) NOT NULL PRIMARY KEY,
  secrets jsonb,
  FOREIGN KEY (userId) REFERENCES users
);
