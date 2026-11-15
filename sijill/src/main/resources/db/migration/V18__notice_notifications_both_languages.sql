-- Bell entries for notices written before V17 hold one language only. The notice itself has both, so fill in the
-- new columns from it. Only the new columns change; title and body stay exactly as they were.
UPDATE notifications nt
SET title_en = LEFT('New notice: ' || n.title_en, 300),
    title_ar = LEFT('إشعار جديد: ' || n.title_ar, 300),
    body_ar  = CASE WHEN LENGTH(n.body_ar) > 900 THEN SUBSTRING(n.body_ar FROM 1 FOR 900) || '…' ELSE n.body_ar END
FROM notices n
WHERE nt.kind = 'NOTICE'
  AND nt.title_en IS NULL
  AND nt.site_id IS NOT DISTINCT FROM n.site_id
  AND nt.title IN ('إشعار جديد: ' || n.title_ar, 'New notice: ' || n.title_en, 'إشعار جديد: ' || n.title_ar || ' | New notice: ' || n.title_en);
