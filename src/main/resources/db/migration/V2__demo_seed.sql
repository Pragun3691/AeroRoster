-- Fictional demonstration data, December 2026. Do not treat as live schedules.
INSERT INTO airport(iata_code, name, city, country) VALUES
('DEL','Indira Gandhi International','New Delhi','India'),
('BOM','Chhatrapati Shivaji Maharaj International','Mumbai','India'),
('BLR','Kempegowda International','Bengaluru','India'),
('HYD','Rajiv Gandhi International','Hyderabad','India'),
('CCU','Netaji Subhas Chandra Bose International','Kolkata','India');

INSERT INTO aircraft_type(model,manufacturer,capacity,min_cabin_crew) VALUES
('Airbus A320','Airbus',180,2),
('Boeing 737-800','Boeing',189,2);

INSERT INTO aircraft(registration,aircraft_type_id) VALUES
('VT-AR101',(SELECT id FROM aircraft_type WHERE model='Airbus A320')),
('VT-AR102',(SELECT id FROM aircraft_type WHERE model='Airbus A320')),
('VT-BR201',(SELECT id FROM aircraft_type WHERE model='Boeing 737-800'));

INSERT INTO crew(name,role,license_no,experience_years,contact_no) VALUES
('Ravi Sharma','PILOT','DEMO-P-001',9,'9000000001'),
('Asha Verma','PILOT','DEMO-P-002',7,'9000000002'),
('Kiran Mehta','PILOT','DEMO-P-003',8,'9000000003'),
('Rahul Sethi','COPILOT','DEMO-C-001',4,'9000000004'),
('Nitin Bose','COPILOT','DEMO-C-002',3,'9000000005'),
('Sana Khan','COPILOT','DEMO-C-003',5,'9000000006'),
('Neha Saxena','CABIN_CREW','DEMO-F-001',3,'9000000007'),
('Meera Patel','CABIN_CREW','DEMO-F-002',4,'9000000008'),
('Dev Arora','CABIN_CREW','DEMO-F-003',2,'9000000009'),
('Tara Singh','CABIN_CREW','DEMO-F-004',5,'9000000010'),
('Zoya Iyer','CABIN_CREW','DEMO-F-005',2,'9000000011'),
('Anil Joshi','CABIN_CREW','DEMO-F-006',6,'9000000012');

INSERT INTO qualification(aircraft_type_id,required_role)
SELECT at.id, roles.role FROM aircraft_type at
CROSS JOIN (VALUES ('PILOT'),('COPILOT'),('CABIN_CREW')) AS roles(role);

-- Demo: A320 qualifications for first two pilots, first two copilots, and four cabin crew.
INSERT INTO crew_qualification(crew_id, qualification_id, valid_until)
SELECT c.id, q.id, DATE '2027-12-31'
FROM crew c JOIN qualification q ON q.required_role = c.role
JOIN aircraft_type at ON q.aircraft_type_id=at.id
WHERE at.model='Airbus A320' AND c.license_no IN
('DEMO-P-001','DEMO-P-002','DEMO-C-001','DEMO-C-002',
 'DEMO-F-001','DEMO-F-002','DEMO-F-003','DEMO-F-004');

-- B737 qualifications for another dedicated group.
INSERT INTO crew_qualification(crew_id, qualification_id, valid_until)
SELECT c.id, q.id, DATE '2027-12-31'
FROM crew c JOIN qualification q ON q.required_role = c.role
JOIN aircraft_type at ON q.aircraft_type_id=at.id
WHERE at.model='Boeing 737-800' AND c.license_no IN
('DEMO-P-003','DEMO-C-003','DEMO-F-005','DEMO-F-006');

INSERT INTO flight(flight_number,source_airport_id,destination_airport_id,aircraft_id,departure_time,arrival_time) VALUES
('AI-101',(SELECT id FROM airport WHERE iata_code='DEL'),(SELECT id FROM airport WHERE iata_code='BOM'),(SELECT id FROM aircraft WHERE registration='VT-AR101'),'2026-12-01 05:00:00+00','2026-12-01 07:10:00+00'),
('AI-102',(SELECT id FROM airport WHERE iata_code='BOM'),(SELECT id FROM airport WHERE iata_code='BLR'),(SELECT id FROM aircraft WHERE registration='VT-AR101'),'2026-12-01 11:00:00+00','2026-12-01 13:00:00+00'),
('AI-205',(SELECT id FROM airport WHERE iata_code='BLR'),(SELECT id FROM airport WHERE iata_code='DEL'),(SELECT id FROM aircraft WHERE registration='VT-BR201'),'2026-12-01 06:00:00+00','2026-12-01 08:20:00+00'),
('AI-301',(SELECT id FROM airport WHERE iata_code='DEL'),(SELECT id FROM airport WHERE iata_code='HYD'),(SELECT id FROM aircraft WHERE registration='VT-AR102'),'2026-12-01 06:00:00+00','2026-12-01 07:30:00+00'),
('AI-101',(SELECT id FROM airport WHERE iata_code='DEL'),(SELECT id FROM airport WHERE iata_code='BOM'),(SELECT id FROM aircraft WHERE registration='VT-AR101'),'2026-12-02 05:00:00+00','2026-12-02 07:10:00+00');

-- Existing crew assigned to AI-101. Duty window: 1h before departure, 30m after arrival.
INSERT INTO duty_assignment(crew_id,flight_id,assigned_role,duty_start_time,duty_end_time)
SELECT c.id, f.id, c.role, f.departure_time - INTERVAL '1 hour', f.arrival_time + INTERVAL '30 minutes'
FROM crew c CROSS JOIN flight f
WHERE f.flight_number='AI-101' AND f.departure_time='2026-12-01 05:00:00+00'::TIMESTAMPTZ
AND c.license_no IN ('DEMO-P-001','DEMO-C-001','DEMO-F-001','DEMO-F-002');

-- Demo absence: tests should reject Tara on overlapping duties, not mark all-day unavailable.
INSERT INTO crew_unavailability(crew_id,start_time,end_time,reason)
SELECT c.id, '2026-12-01 04:00:00+00', '2026-12-01 10:00:00+00', 'Training'
FROM crew c WHERE c.license_no='DEMO-F-004';
