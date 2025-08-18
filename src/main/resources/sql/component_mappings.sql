SELECT
    component_id,
    component_name,
    transaction_cycle,
    mapping_type,
    instance_url,
    tool_type,
    tool_element_id,
    name,
    identifier,
    web_url
FROM public.component_mapping
WHERE component_id = ?
