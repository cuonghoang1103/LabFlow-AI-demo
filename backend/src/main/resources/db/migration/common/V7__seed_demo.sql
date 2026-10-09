-- V7: FAKE demo data (no real people). Every demo account has password: Labflow123
INSERT INTO campuses (code, name) VALUES ('FU-HL', 'FPT University Hoa Lac');

INSERT INTO users (email, password_hash, full_name, status) VALUES
('admin@fpt.edu.vn',    '$2a$10$omqnYfcFEYaPgwD2cf6l4OE.I/0mn2tyby.szAWKhJ3DQPT5q9ppe', 'Demo Admin',      'ACTIVE'),
('manager@fpt.edu.vn',  '$2a$10$omqnYfcFEYaPgwD2cf6l4OE.I/0mn2tyby.szAWKhJ3DQPT5q9ppe', 'Demo Lab Manager', 'ACTIVE'),
('staff@fpt.edu.vn',    '$2a$10$omqnYfcFEYaPgwD2cf6l4OE.I/0mn2tyby.szAWKhJ3DQPT5q9ppe', 'Demo Lab Staff',  'ACTIVE'),
('lecturer@fe.edu.vn',  '$2a$10$omqnYfcFEYaPgwD2cf6l4OE.I/0mn2tyby.szAWKhJ3DQPT5q9ppe', 'Demo Lecturer',   'ACTIVE'),
('student1@fpt.edu.vn', '$2a$10$omqnYfcFEYaPgwD2cf6l4OE.I/0mn2tyby.szAWKhJ3DQPT5q9ppe', 'Demo Student One', 'ACTIVE'),
('student2@fpt.edu.vn', '$2a$10$omqnYfcFEYaPgwD2cf6l4OE.I/0mn2tyby.szAWKhJ3DQPT5q9ppe', 'Demo Student Two', 'ACTIVE'),
('pending@fpt.edu.vn',  '$2a$10$omqnYfcFEYaPgwD2cf6l4OE.I/0mn2tyby.szAWKhJ3DQPT5q9ppe', 'Demo Not Verified', 'PENDING');

INSERT INTO user_roles (user_id, role_id) SELECT id, 5 FROM users WHERE email = 'admin@fpt.edu.vn';
INSERT INTO user_roles (user_id, role_id) SELECT id, 4 FROM users WHERE email = 'manager@fpt.edu.vn';
INSERT INTO user_roles (user_id, role_id) SELECT id, 3 FROM users WHERE email = 'staff@fpt.edu.vn';
INSERT INTO user_roles (user_id, role_id) SELECT id, 2 FROM users WHERE email = 'lecturer@fe.edu.vn';
INSERT INTO user_roles (user_id, role_id) SELECT id, 1 FROM users WHERE email IN ('student1@fpt.edu.vn', 'student2@fpt.edu.vn', 'pending@fpt.edu.vn');

INSERT INTO buildings (campus_id, code, name, status)
SELECT id, 'AL', 'Alpha', 'ACTIVE' FROM campuses WHERE code = 'FU-HL';
INSERT INTO buildings (campus_id, code, name, status)
SELECT id, 'BE', 'Beta', 'ACTIVE' FROM campuses WHERE code = 'FU-HL';

-- AL-301 has exactly 30 seats: NOT above the BR-06 threshold, so no approval needed (boundary example).
INSERT INTO labs (building_id, code, name, floor, capacity, status, requires_approval)
SELECT id, 'AL-301', 'IoT Lab', 3, 30, 'ACTIVE', FALSE FROM buildings WHERE code = 'AL';
INSERT INTO labs (building_id, code, name, floor, capacity, status, requires_approval)
SELECT id, 'AL-302', 'Network Lab', 3, 25, 'ACTIVE', FALSE FROM buildings WHERE code = 'AL';
INSERT INTO labs (building_id, code, name, floor, capacity, status, requires_approval)
SELECT id, 'BE-201', 'Electronics Lab', 2, 60, 'ACTIVE', TRUE FROM buildings WHERE code = 'BE';
INSERT INTO labs (building_id, code, name, floor, capacity, status, requires_approval)
SELECT id, 'BE-105', 'Old Robotics Lab', 1, 20, 'CLOSED', FALSE FROM buildings WHERE code = 'BE';

INSERT INTO equipment_types (code, name) VALUES
('OSC', 'Oscilloscope'), ('MCU', 'Microcontroller kit'), ('PSU', 'Bench power supply'), ('SOLDER', 'Soldering station');

INSERT INTO equipment (lab_id, type_id, serial, name, status, requires_training, requires_approval, status_reason)
SELECT l.id, t.id, 'OSC-001', 'Rigol DS1054Z #1', 'AVAILABLE', TRUE, FALSE, NULL FROM labs l, equipment_types t WHERE l.code = 'BE-201' AND t.code = 'OSC';
INSERT INTO equipment (lab_id, type_id, serial, name, status, requires_training, requires_approval, status_reason)
SELECT l.id, t.id, 'OSC-002', 'Rigol DS1054Z #2', 'AVAILABLE', TRUE, FALSE, NULL FROM labs l, equipment_types t WHERE l.code = 'BE-201' AND t.code = 'OSC';
INSERT INTO equipment (lab_id, type_id, serial, name, status, requires_training, requires_approval, status_reason)
SELECT l.id, t.id, 'OSC-003', 'Rigol DS1054Z #3', 'MAINTENANCE', TRUE, FALSE, 'Channel 2 noisy' FROM labs l, equipment_types t WHERE l.code = 'BE-201' AND t.code = 'OSC';
INSERT INTO equipment (lab_id, type_id, serial, name, status, requires_training, requires_approval, status_reason)
SELECT l.id, t.id, 'MCU-ESP32-01', 'ESP32 DevKit #1', 'AVAILABLE', FALSE, FALSE, NULL FROM labs l, equipment_types t WHERE l.code = 'AL-301' AND t.code = 'MCU';
INSERT INTO equipment (lab_id, type_id, serial, name, status, requires_training, requires_approval, status_reason)
SELECT l.id, t.id, 'MCU-ESP32-02', 'ESP32 DevKit #2', 'AVAILABLE', FALSE, FALSE, NULL FROM labs l, equipment_types t WHERE l.code = 'AL-301' AND t.code = 'MCU';
INSERT INTO equipment (lab_id, type_id, serial, name, status, requires_training, requires_approval, status_reason)
SELECT l.id, t.id, 'PSU-001', 'Bench PSU 30V 5A', 'AVAILABLE', FALSE, FALSE, NULL FROM labs l, equipment_types t WHERE l.code = 'AL-301' AND t.code = 'PSU';
INSERT INTO equipment (lab_id, type_id, serial, name, status, requires_training, requires_approval, status_reason)
SELECT l.id, t.id, 'SOLDER-001', 'Hakko FX-888D', 'AVAILABLE', TRUE, TRUE, NULL FROM labs l, equipment_types t WHERE l.code = 'BE-201' AND t.code = 'SOLDER';
INSERT INTO equipment (lab_id, type_id, serial, name, status, requires_training, requires_approval, status_reason)
SELECT l.id, t.id, 'PSU-OLD-01', 'Old PSU (broken)', 'RETIRED', FALSE, FALSE, 'Transformer burnt' FROM labs l, equipment_types t WHERE l.code = 'AL-302' AND t.code = 'PSU';

-- Default opening hours (lab_id NULL): Monday-Saturday 07:00-21:00, Sunday closed.
INSERT INTO operating_hours (lab_id, day_of_week, open_time, close_time, closed) VALUES
(NULL, 1, '07:00', '21:00', FALSE), (NULL, 2, '07:00', '21:00', FALSE), (NULL, 3, '07:00', '21:00', FALSE),
(NULL, 4, '07:00', '21:00', FALSE), (NULL, 5, '07:00', '21:00', FALSE), (NULL, 6, '07:00', '21:00', FALSE),
(NULL, 7, NULL, NULL, TRUE);
-- Override: the Electronics Lab closes early on Saturday.
INSERT INTO operating_hours (lab_id, day_of_week, open_time, close_time, closed)
SELECT id, 6, '07:00', '12:00', FALSE FROM labs WHERE code = 'BE-201';

INSERT INTO holidays (holiday_date, name) VALUES
('2027-01-01', 'New Year'),
('2027-02-05', 'Tet holiday'), ('2027-02-06', 'Tet holiday'), ('2027-02-07', 'Tet holiday'),
('2027-02-08', 'Tet holiday'), ('2027-02-09', 'Tet holiday');
