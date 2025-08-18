SELECT
    business_application_name AS app_name,
    correlation_id AS app_id,
    active,
    owning_transaction_cycle,
    owning_transaction_cycle_id,
    resilience_category,
    operational_status,
    application_type,
    architecture_type,
    install_type,
    application_parent,
    application_parent_correlation_id,
    house_position,
    business_application_sys_id,
    application_tier,
    application_product_owner,
    system_architect
FROM public.vwsfbusinessapplication
WHERE correlation_id = ?
