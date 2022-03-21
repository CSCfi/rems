CREATE TABLE user_mappings (
  userId varchar(255) NOT NULL,
  extIdAttribute varchar(255) NOT NULL,
  extIdValue varchar(255) NOT NULL,
  PRIMARY KEY (userId, extIdAttribute, extIdValue),
  FOREIGN KEY (userId) REFERENCES users
);
