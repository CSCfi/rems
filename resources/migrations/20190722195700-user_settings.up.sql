CREATE TABLE user_settings (
  userId varchar(255) NOT NULL PRIMARY KEY,
  settings jsonb,
  FOREIGN KEY (userId) REFERENCES users
);
