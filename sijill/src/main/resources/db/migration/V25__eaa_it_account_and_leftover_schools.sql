-- Abdalwahab becomes EAA IT; the test account that was "EAA" becomes the school's IT, named "School IT".
UPDATE people SET role = 'EAA_ADMIN' WHERE display_name = 'Abdalwahab' AND role = 'SCHOOL_IT';
UPDATE people SET role = 'SCHOOL_IT', display_name = 'School IT' WHERE display_name = 'EAA' AND role = 'EAA_ADMIN';

-- Two leftover demonstration schools are removed from use, the same way EAA IT removes a school: they disappear from
-- every screen and comparison, and their history stays in the database.
UPDATE sites SET active = false WHERE name IN ('School E', 'School F') AND NOT head_office;
