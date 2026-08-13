-- Optimized view for admin order dashboard with real-time relevant data
-- Provides flat data structure for easier consumption in the mobile app

DROP VIEW IF EXISTS public.v_admin_order_summary;

CREATE OR REPLACE VIEW public.v_admin_order_summary AS
SELECT
    o.id,
    o.created_at,
    o.status,
    o.payment_status,
    o.payment_method,
    o.total_amount,
    o.final_amount,
    o.shipping_address,
    o.shipping_method,
    o.is_fitting_service,
    o.digital_receipt_enabled,
    p.full_name as customer_name,
    p.email as customer_email,
    p.phone_number as customer_phone,
    (SELECT COUNT(*) FROM public.order_items WHERE order_id = o.id) as item_count,
    COALESCE(
        (SELECT string_agg(DISTINCT prof.business_name, ', ')
         FROM public.order_items oi
         JOIN public.profiles prof ON oi.vendor_id = prof.id
         WHERE oi.order_id = o.id),
        'System'
    ) as vendor_names
FROM public.orders o
JOIN public.profiles p ON o.user_id = p.id;

-- Ensure RLS and Permissions
ALTER VIEW public.v_admin_order_summary OWNER TO postgres;
GRANT SELECT ON public.v_admin_order_summary TO authenticated;
GRANT SELECT ON public.v_admin_order_summary TO service_role;

-- Log System update
INSERT INTO public.system_logs (action, details, severity)
VALUES ('DATABASE_UPDATE', '{"module": "admin_orders", "features": ["v_admin_order_summary"]}', 'info');
