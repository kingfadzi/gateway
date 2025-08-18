SELECT
    bs.service_correlation_id,
    bs.service,
    bac.correlation_id,
    bac.business_application_name,
    si.correlation_id AS instance_correlation_id,
    si.it_service_instance,
    si.environment,
    si.install_type
FROM public.vwsfitserviceinstance AS si
         JOIN public.vwsfbusinessapplication AS bac
              ON si.business_application_sysid = bac.business_application_sys_id
         JOIN public.vwsfitbusinessservice AS bs
              ON si.it_business_service_sysid = bs.it_business_service_sysid
WHERE bac.correlation_id = ?;
