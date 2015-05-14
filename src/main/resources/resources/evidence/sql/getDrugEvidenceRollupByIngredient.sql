SELECT REPORT_ORDER, REPORT_NAME, INGREDIENT_ID, INGREDIENT, CLINICAL_DRUG_ID, CLINICAL_DRUG, HOI_ID, HOI, CT_COUNT, CASE_COUNT, OTHER_COUNT, SPLICER_COUNT, EU_SPC_COUNT, SEMMEDDB_CT_COUNT, SEMMEDDB_CASE_COUNT, SEMMEDDB_NEG_CT_COUNT, SEMMEDDB_NEG_CASE_COUNT, AERS_REPORT_COUNT, PRR
FROM @OHDSI_schema.LAERTES_SUMMARY
WHERE INGREDIENT_ID = @id
