-- The sweeper records an expiry marker through the pipeline; it needs the
-- flow's composed event type to name it (<composed_event_type>.expired).
-- NULL for rows created before this migration: they expire without a marker.
ALTER TABLE composition_state ADD COLUMN composed_event_type VARCHAR(128) NULL;
