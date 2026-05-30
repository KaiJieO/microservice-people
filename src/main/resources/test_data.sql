-- Test Data for People Microservice
-- Usage: mysql -u root -p test123 microservice_people_db < src/main/resources/test_data.sql
-- Run manually when needed. Clears existing data and inserts fresh sample data.

-- Clear existing data (reverse FK order)
DELETE FROM audit_logs;
DELETE FROM user_sessions;
DELETE FROM password_reset_tokens;
DELETE FROM user_personal_details;
DELETE FROM user_signup;

-- Sample Users
INSERT INTO user_signup (id, email, phone, password_hash, roles, status, email_verified, phone_verified, last_login_at, login_attempt_count)
VALUES
  ('550e8400-e29b-41d4-a716-446655440001', 'alice@example.com', '+60123456789', '$2a$12$dXJ3SW6G7P50eS3q5iKUl.gB8.gd6Ydq9eHSjLPfvvxKqJeT2Xvei', 'USER', 'ACTIVE', TRUE, TRUE, NOW(), 0),
  ('550e8400-e29b-41d4-a716-446655440002', 'bob@example.com', '+60187654321', '$2a$12$dXJ3SW6G7P50eS3q5iKUl.gB8.gd6Ydq9eHSjLPfvvxKqJeT2Xvei', 'USER,ADMIN', 'ACTIVE', TRUE, TRUE, NOW(), 0),
  ('550e8400-e29b-41d4-a716-446655440003', 'charlie@example.com', '+60191234567', '$2a$12$dXJ3SW6G7P50eS3q5iKUl.gB8.gd6Ydq9eHSjLPfvvxKqJeT2Xvei', 'USER', 'INACTIVE', FALSE, FALSE, NULL, 0),
  ('550e8400-e29b-41d4-a716-446655440004', 'diana@example.com', '+60198765432', '$2a$12$dXJ3SW6G7P50eS3q5iKUl.gB8.gd6Ydq9eHSjLPfvvxKqJeT2Xvei', 'USER', 'SUSPENDED', TRUE, FALSE, NULL, 3);

-- Sample Personal Details
INSERT INTO user_personal_details (id, user_id, salutation, first_name, last_name, date_of_birth, gender, identity_type, identity_number, citizenship, nationality, address1, address2, city, state, postcode, identity_verified)
VALUES
  ('650e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'Ms', 'Alice', 'Johnson', '1990-05-15', 'F', 'PASSPORT', 'A12345678', 'FOREIGNER', 'American', '123 Main St', 'Apt 101', 'Kuala Lumpur', 'SELANGOR', '50000', TRUE),
  ('650e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440002', 'Mr', 'Bob', 'Ahmad', '1988-03-22', 'M', 'NRIC', '880322015678', 'MALAYSIAN', 'Malaysian', '456 Jalan Raja', NULL, 'George Town', 'PENANG', '10000', TRUE),
  ('650e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440003', 'Mr', 'Charlie', 'Chen', '1992-07-10', 'M', 'NRIC', '920710015432', 'MALAYSIAN', 'Malaysian', '789 Bukit Bintang', 'Suite 200', 'Kuala Lumpur', 'SELANGOR', '50200', FALSE),
  ('650e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440004', 'Ms', 'Diana', 'Tan', '1995-11-28', 'F', 'PASSPORT', 'B87654321', 'FOREIGNER', 'Singaporean', '321 Merdeka Square', NULL, 'Johor Bahru', 'JOHOR', '80000', FALSE);

-- Sample Password Reset Tokens
INSERT INTO password_reset_tokens (id, user_id, email, token_hash, expires_at, used_at)
VALUES
  ('750e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440003', 'charlie@example.com', '$2a$12$reset123token456789abcdefghijklmnopqrstuvwxyz', DATE_ADD(NOW(), INTERVAL 5 MINUTE), NULL),
  ('750e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440004', 'diana@example.com', '$2a$12$expiredtoken789abcdefghijklmnopqrstuvwxyz123456', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW());

-- Sample User Sessions
INSERT INTO user_sessions (id, user_id, token_hash, ip_address, user_agent, expires_at)
VALUES
  ('850e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', '$2a$12$sessiontoken1abcdefghijklmnopqrstuvwxyz123456789', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', DATE_ADD(NOW(), INTERVAL 24 HOUR)),
  ('850e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440001', '$2a$12$sessiontoken2abcdefghijklmnopqrstuvwxyz123456789', '192.168.1.101', 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_0)', DATE_ADD(NOW(), INTERVAL 24 HOUR)),
  ('850e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440002', '$2a$12$sessiontoken3abcdefghijklmnopqrstuvwxyz123456789', '203.0.113.45', 'PostmanRuntime/7.26.8', DATE_ADD(NOW(), INTERVAL 24 HOUR));

-- Sample Audit Logs
INSERT INTO audit_logs (id, user_id, action, table_name, record_id, old_values, new_values, ip_address, user_agent)
VALUES
  ('950e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'CREATE', 'user_signup', '550e8400-e29b-41d4-a716-446655440001', NULL, '{"email":"alice@example.com","phone":"+60123456789","status":"ACTIVE"}', '192.168.1.100', 'Mozilla/5.0'),
  ('950e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440002', 'CREATE', 'user_signup', '550e8400-e29b-41d4-a716-446655440002', NULL, '{"email":"bob@example.com","phone":"+60187654321","roles":"USER,ADMIN","status":"ACTIVE"}', '203.0.113.45', 'PostmanRuntime/7.26.8'),
  ('950e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440001', 'LOGIN', 'user_sessions', '850e8400-e29b-41d4-a716-446655440001', NULL, '{"session_id":"850e8400-e29b-41d4-a716-446655440001","user_id":"550e8400-e29b-41d4-a716-446655440001"}', '192.168.1.100', 'Mozilla/5.0'),
  ('950e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440002', 'LOGIN', 'user_sessions', '850e8400-e29b-41d4-a716-446655440003', NULL, '{"session_id":"850e8400-e29b-41d4-a716-446655440003","user_id":"550e8400-e29b-41d4-a716-446655440002"}', '203.0.113.45', 'PostmanRuntime/7.26.8');
