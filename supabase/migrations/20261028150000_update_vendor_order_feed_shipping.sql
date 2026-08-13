-- Update vendor_order_feed view to include shipping address and customer contact info
-- This ensures vendors have all necessary details to fulfill orders

DROP VIEW IF EXISTS public.vendor_order_feed;

CREATE OR REPLACE VIEW public.vendor_order_feed
WITH (security_invoker = true)
AS
SELECT
    oi.id as order_item_id,
    oi.order_id,
    oi.product_id,
    oi.vendor_id,
    oi.quantity,
    oi.unit_price,
    oi.size,
    oi.color,
    oi.status as item_status,
    oi.embroidery_name,
    oi.created_at,
    o.user_id as customer_id,
    p.full_name as customer_name,
    p.email as customer_email,
    p.phone_number as customer_phone,
    prod.name as product_name,
    prod.images as product_images,
    o.total_amount as order_total,
    o.status as order_status,
    o.shipping_address,
    o.shipping_method,
    o.payment_status
FROM public.order_items oi
JOIN public.orders o ON oi.order_id = o.id
JOIN public.profiles p ON o.user_id = p.id
JOIN public.products prod ON oi.product_id = prod.id;

-- Log the schema update
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SCHEMA_UPDATE', '{"view": "vendor_order_feed", "changes": "added shipping_address, customer_contact, embroidery"}', 'info');
