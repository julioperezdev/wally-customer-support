alter table wcs.catalog_variants
    add column image_object_key varchar(512);

update wcs.catalog_variants
set image_object_key = case sku
    when 'RP-REM-NP-NEG-M' then 'wcs/catalog/10000000-0000-0000-0000-000000000001/RP-REM-NP-NEG-M.png'
    when 'RP-REM-NP-NEG-L' then 'wcs/catalog/10000000-0000-0000-0000-000000000001/RP-REM-NP-NEG-M.png'
    when 'RP-REM-NP-BLA-M' then 'wcs/catalog/10000000-0000-0000-0000-000000000001/RP-REM-NP-BLA-M.png'
    when 'RP-BUZ-SB-GRI-L' then 'wcs/catalog/10000000-0000-0000-0000-000000000002/RP-BUZ-SB-GRI-L.png'
    when 'RP-BUZ-SB-NEG-XL' then 'wcs/catalog/10000000-0000-0000-0000-000000000002/RP-BUZ-SB-NEG-XL.png'
    when 'RP-CAM-DF-AZU-M' then 'wcs/catalog/10000000-0000-0000-0000-000000000003/RP-CAM-DF-AZU-M.png'
    else image_object_key
end
where sku in (
    'RP-REM-NP-NEG-M', 'RP-REM-NP-NEG-L', 'RP-REM-NP-BLA-M',
    'RP-BUZ-SB-GRI-L', 'RP-BUZ-SB-NEG-XL', 'RP-CAM-DF-AZU-M');

update wcs.catalog_products
set image_object_key = case id
    when '10000000-0000-0000-0000-000000000001' then 'wcs/catalog/10000000-0000-0000-0000-000000000001/RP-REM-NP-NEG-M.png'
    when '10000000-0000-0000-0000-000000000002' then 'wcs/catalog/10000000-0000-0000-0000-000000000002/RP-BUZ-SB-NEG-XL.png'
    when '10000000-0000-0000-0000-000000000003' then 'wcs/catalog/10000000-0000-0000-0000-000000000003/RP-CAM-DF-AZU-M.png'
    else image_object_key
end
where id in (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003');
