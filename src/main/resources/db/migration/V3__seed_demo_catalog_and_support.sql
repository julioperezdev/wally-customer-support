insert into wcs.catalog_products (
    id, name, description, image_object_key, active, demo, created_at, updated_at
) values
    ('10000000-0000-0000-0000-000000000001', 'Remera NullPointer',
     'Remera de algodón con diseño para quienes sobreviven a producción.',
     'demo/ropa-programador/remera-nullpointer.jpg', true, true,
     '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('10000000-0000-0000-0000-000000000002', 'Buzo Spring Boot',
     'Buzo unisex de abrigo para deploys durante todo el año.',
     'demo/ropa-programador/buzo-spring-boot.jpg', true, true,
     '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('10000000-0000-0000-0000-000000000003', 'Campera Deploy Friday',
     'Campera liviana para salir de la oficina antes del viernes a la tarde.',
     'demo/ropa-programador/campera-deploy-friday.jpg', true, true,
     '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z');

insert into wcs.catalog_variants (
    id, product_id, sku, size_label, color, price, currency, stock, active, created_at, updated_at
) values
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'RP-REM-NP-NEG-M', 'M', 'Negro', 18900.00, 'ARS', 12, true, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
     'RP-REM-NP-NEG-L', 'L', 'Negro', 18900.00, 'ARS', 7, true, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
     'RP-REM-NP-BLA-M', 'M', 'Blanco', 18900.00, 'ARS', 0, true, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002',
     'RP-BUZ-SB-GRI-L', 'L', 'Gris', 42900.00, 'ARS', 5, true, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002',
     'RP-BUZ-SB-NEG-XL', 'XL', 'Negro', 42900.00, 'ARS', 3, true, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('20000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000003',
     'RP-CAM-DF-AZU-M', 'M', 'Azul', 67900.00, 'ARS', 4, true, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z');

insert into wcs.business_hours (
    id, day_of_week, opens_at, closes_at, closed, timezone, active, demo, record_version, created_at, updated_at
) values
    ('30000000-0000-0000-0000-000000000001', 1, '09:00:00', '18:00:00', false, 'America/Argentina/Buenos_Aires', true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('30000000-0000-0000-0000-000000000002', 2, '09:00:00', '18:00:00', false, 'America/Argentina/Buenos_Aires', true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('30000000-0000-0000-0000-000000000003', 3, '09:00:00', '18:00:00', false, 'America/Argentina/Buenos_Aires', true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('30000000-0000-0000-0000-000000000004', 4, '09:00:00', '18:00:00', false, 'America/Argentina/Buenos_Aires', true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('30000000-0000-0000-0000-000000000005', 5, '09:00:00', '18:00:00', false, 'America/Argentina/Buenos_Aires', true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('30000000-0000-0000-0000-000000000006', 6, '10:00:00', '14:00:00', false, 'America/Argentina/Buenos_Aires', true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('30000000-0000-0000-0000-000000000007', 7, null, null, true, 'America/Argentina/Buenos_Aires', true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z');

insert into wcs.support_policies (
    id, policy_key, title, content, active, demo, record_version, published_at, created_at, updated_at
) values
    ('40000000-0000-0000-0000-000000000001', 'shipping', 'Envíos',
     'Los envíos de prueba se realizan dentro de Argentina. El costo y el plazo se confirman antes de finalizar la compra.',
     true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('40000000-0000-0000-0000-000000000002', 'payments', 'Medios de pago',
     'Aceptamos tarjetas y transferencia bancaria. En este MVP no se ejecutan pagos ni se solicitan datos financieros por WhatsApp.',
     true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('40000000-0000-0000-0000-000000000003', 'changes', 'Cambios',
     'Los cambios de productos de demostración se solicitan dentro de los 30 días, sujetos a disponibilidad y revisión del equipo.',
     true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'),
    ('40000000-0000-0000-0000-000000000004', 'returns', 'Devoluciones',
     'Las devoluciones se evalúan caso por caso por una persona del equipo. El bot no confirma reembolsos ni inicia operaciones.',
     true, true, 1, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z');
