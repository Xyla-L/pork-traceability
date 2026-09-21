SELECT 'entry_inspection' t, COUNT(*) cnt FROM db_slaughter.entry_inspection
UNION ALL SELECT 'slaughter_inspection', COUNT(*) FROM db_slaughter.slaughter_inspection
UNION ALL SELECT 'carcass_stamp', COUNT(*) FROM db_slaughter.carcass_stamp
UNION ALL SELECT 'ractopamine_test', COUNT(*) FROM db_slaughter.ractopamine_test
UNION ALL SELECT 'slaughterhouse', COUNT(*) FROM db_slaughter.slaughterhouse
UNION ALL SELECT 'pig_individual', COUNT(*) FROM db_breeding.pig_individual
UNION ALL SELECT 'slaughter_apply', COUNT(*) FROM db_breeding.slaughter_apply
UNION ALL SELECT 'quarantine_certificate', COUNT(*) FROM db_breeding.quarantine_certificate;
