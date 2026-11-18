-- Additive edit revisions for existing records; applied V1/V2 remain unchanged.
ALTER TABLE crew ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE aircraft ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE flight ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE crew_qualification ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE crew_unavailability ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE duty_assignment ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
