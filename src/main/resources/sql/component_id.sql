SELECT component_id
FROM component_mapping
WHERE mapping_type = 'it_business_application'
  AND identifier = ?
    LIMIT 1
