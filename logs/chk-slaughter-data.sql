SELECT 'pig_individual' t, COUNT(*) cnt FROM db_breeding.pig_individual
UNION ALL SELECT 'entry_inspection', COUNT(*) FROM db_slaughter.entry_inspection
UNION ALL SELECT 'slaughter_inspection', COUNT(*) FROM db_slaughter.slaughter_inspection
UNION ALL SELECT 'carcass_batch', COUNT(*) FROM db_slaughter.carcass_batch
UNION ALL SELECT 'carcass_stamp', COUNT(*) FROM db_slaughter.carcass_stamp
UNION ALL SELECT 'ractopamine_test', COUNT(*) FROM db_slaughter.ractopamine_test
UNION ALL SELECT 'slaughter_apply', COUNT(*) FROM db_slaughter.slaughter_apply;
