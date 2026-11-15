-- A bell entry keeps its title (and, where the sender has one, its text) in both languages, so the screen can show
-- the language the person is looking at. The existing title column stays as it was (the person's chosen language);
-- older rows have only that one.
ALTER TABLE notifications ADD COLUMN title_en VARCHAR(300);
ALTER TABLE notifications ADD COLUMN title_ar VARCHAR(300);
ALTER TABLE notifications ADD COLUMN body_ar VARCHAR(1000);
