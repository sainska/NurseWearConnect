-- Vendor Business Intelligence & Advanced Operations
-- 1. Create order item status history for granular tracking
-- 2. Add functions for vendor performance benchmarking
-- 3. Update vendor_order_feed with SLA tracking (e.g., hours since order)

-- Track granular history of each order item (important for multi-vendor accountability)
CREATE TABLE IF NOT EXISTS public.order_item_status_history (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    order_item_id uuid REFERENCES public.order_items(id) ON DELETE CASCADE,
    status text NOT NULL,
    notes text,
    changed_by uuid REFERENCES public.profiles(id),
    created_at timestamp with time zone DEFAULT now()
);

-- Trigger to auto-log status changes for items
CREATE OR REPLACE FUNCTION public.fn_log_order_item_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.status IS DISTINCT FROM NEW.status) THEN
        INSERT INTO public.order_item_status_history (order_item_id, status, changed_by)
        VALUES (NEW.id, NEW.status, auth.uid());
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_log_order_item_status_change ON public.order_items;
CREATE TRIGGER tr_log_order_item_status_change
AFTER UPDATE OF status ON public.order_items
FOR EACH ROW EXECUTE FUNCTION public.fn_log_order_item_status_change();

-- Update vendor order feed with fulfillment SLA calculation (hours since creation)
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
    o.payment_status,
    -- Business Intelligence: How many hours has this order been waiting?
    EXTRACT(EPOCH FROM (now() - oi.created_at)) / 3600 as hours_since_order,
    -- Check if it violates 24h shipping SLA
    CASE WHEN (oi.status = 'pending' AND (now() - oi.created_at) > interval '24 hours') THEN true ELSE false END as is_late_fulfillment
FROM public.order_items oi
JOIN public.orders o ON oi.order_id = o.id
JOIN public.profiles p ON o.user_id = p.id
JOIN public.products prod ON oi.product_id = prod.id
WHERE o.payment_status = 'paid';

-- Function to get vendor revenue by period (Day, Week, Month)
CREATE OR REPLACE FUNCTION public.get_vendor_revenue_breakdown(p_vendor_id UUID)
RETURNS TABLE (
    period text,
    gross_revenue numeric,
    commission numeric,
    net_earnings numeric,
    order_count bigint
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        'Last 24 Hours'::text as period,
        COALESCE(SUM(unit_price * quantity), 0) as gross_revenue,
        COALESCE(SUM(unit_price * quantity * 0.1), 0) as commission, -- 10% standard
        COALESCE(SUM(unit_price * quantity * 0.9), 0) as net_earnings,
        COUNT(DISTINCT order_id) as order_count
    FROM public.order_items
    WHERE vendor_id = p_vendor_id AND created_at >= (now() - interval '24 hours')

    UNION ALL

    SELECT
        'Last 7 Days'::text as period,
        COALESCE(SUM(unit_price * quantity), 0) as gross_revenue,
        COALESCE(SUM(unit_price * quantity * 0.1), 0) as commission,
        COALESCE(SUM(unit_price * quantity * 0.9), 0) as net_earnings,
        COUNT(DISTINCT order_id) as order_count
    FROM public.order_items
    WHERE vendor_id = p_vendor_id AND created_at >= (now() - interval '7 days')

    UNION ALL

    SELECT
        'Last 30 Days'::text as period,
        COALESCE(SUM(unit_price * quantity), 0) as gross_revenue,
        COALESCE(SUM(unit_price * quantity * 0.1), 0) as commission,
        COALESCE(SUM(unit_price * quantity * 0.9), 0) as net_earnings,
        COUNT(DISTINCT order_id) as order_count
    FROM public.order_items
    WHERE vendor_id = p_vendor_id AND created_at >= (now() - interval '30 days');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Log System update
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SYSTEM_UPDATE', '{"module": "vendor_intelligence", "features": ["order_item_history", "sla_tracking", "revenue_breakdown"]}', 'info');
