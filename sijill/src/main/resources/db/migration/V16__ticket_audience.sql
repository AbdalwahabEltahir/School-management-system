-- A ticket is addressed either to the school's own School IT, or (principals only) to Leadership at head office.
-- Tickets that already exist were all written to head office, so they become Leadership tickets.
ALTER TABLE support_tickets ADD COLUMN audience VARCHAR(10) NOT NULL DEFAULT 'LEADERSHIP';
CREATE INDEX idx_tickets_creator ON support_tickets (created_by, updated_at DESC);
