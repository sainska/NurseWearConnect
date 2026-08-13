-- Create a flattened view for vendor orders to simplify app consumption
-- This view allows vendors to see specific items ordered from them along with customer info

CREATE OR REPLACE VIEW public.vendor_order_feed AS
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
    oi.created_at,
    o.user_id as customer_id,
    p.full_name as customer_name,
    prod.name as product_name,
    prod.images as product_images,
    o.total_amount as order_total,
    o.status as order_status
FROM public.order_items oi
JOIN public.orders o ON oi.order_id = o.id
JOIN public.profiles p ON o.user_id = p.id
JOIN public.products prod ON oi.product_id = prod.id;

-- Ensure RLS applies to the view (it inherits from base tables, but good to be explicit)
ALTER VIEW public.vendor_order_feed SET (security_invoker = true);

-- Add to master schema
-- Note: Already handled by the master schema update step below
