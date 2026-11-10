-- There is no separate "head office" in the product: every workspace is a school and counts in the KPIs.
UPDATE sites SET head_office = FALSE, scored = TRUE WHERE head_office = TRUE;
