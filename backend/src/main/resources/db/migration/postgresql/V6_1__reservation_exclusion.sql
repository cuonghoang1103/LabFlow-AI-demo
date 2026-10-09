-- PostgreSQL only: the database itself refuses two ACTIVE items on the same lab/equipment whose
-- half-open periods overlap (error 23P01). H2 has no exclusion constraints, so the H2 profile
-- relies on the service-level check only. Concurrency is tested on real PostgreSQL (ConcurrentBookingIT).
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservation_items
    ADD CONSTRAINT ex_lab_no_overlap
    EXCLUDE USING gist (lab_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
    WHERE (active AND lab_id IS NOT NULL);

ALTER TABLE reservation_items
    ADD CONSTRAINT ex_equipment_no_overlap
    EXCLUDE USING gist (equipment_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
    WHERE (active AND equipment_id IS NOT NULL);
