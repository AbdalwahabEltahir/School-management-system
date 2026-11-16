-- Pick-up is now confirmed by the parent: pressing "Pick up" completes it at once, without staff approval. Before
-- pressing, the parent accepts that from that moment the school is no longer responsible for the child. When and what
-- they accepted is kept with the pick-up.
ALTER TABLE pickup_requests ADD COLUMN parent_acknowledged_at TIMESTAMPTZ;
ALTER TABLE pickup_requests ADD COLUMN acknowledgement VARCHAR(800);
