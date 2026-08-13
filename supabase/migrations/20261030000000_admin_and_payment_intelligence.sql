-- Admin Dashboard & Payment Intelligence Updates
-- Consolidated: 2026-10-30

-- 1. Function for Sales Trends (Revenue over the last X days)
CREATE OR REPLACE FUNCTION public.get_admin_sales_trends(p_days INTEGER DEFAULT 30)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_sales_trends JSONB;
    v_start_date TIMESTAMPTZ;
BEGIN
    v_start_date := NOW() - (p_days || ' days')::INTERVAL;

    SELECT jsonb_agg(t) INTO v_sales_trends FROM (
        WITH date_series AS (
            SELECT generate_series(v_start_date::date, NOW()::date, '1 day'::interval)::date as d
        )
        SELECT
            ds.d::text as period,
            COALESCE(SUM(o.total_amount), 0) as revenue,
            COUNT(o.id) as order_count
        FROM date_series ds
        LEFT JOIN public.orders o ON o.created_at::date = ds.d AND o.status = 'delivered'
        GROUP BY ds.d
        ORDER BY ds.d ASC
    ) t;

    RETURN COALESCE(v_sales_trends, '[]'::jsonb);
END;
$$;

-- 2. Function for Inventory Health Overview
CREATE OR REPLACE FUNCTION public.get_admin_inventory_health()
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_inventory_health JSONB;
BEGIN
    SELECT jsonb_build_object(
        'total_items_count', COUNT(*),
        'low_stock_count', COUNT(*) FILTER (WHERE stock_count > 0 AND stock_count <= 5),
        'out_of_stock_count', COUNT(*) FILTER (WHERE stock_count = 0),
        'total_stock_value', COALESCE(SUM(price_kes * stock_count), 0),
        'total_vendors', COUNT(DISTINCT vendor_id)
    ) INTO v_inventory_health
    FROM public.products;

    RETURN v_inventory_health;
END;
$$;

-- 3. Detailed Admin Sales Report View
DROP VIEW IF EXISTS public.admin_detailed_sales_report;
CREATE OR REPLACE VIEW public.admin_detailed_sales_report
WITH (security_invoker = true)
AS
SELECT
    o.id AS order_id,
    o.created_at AS order_date,
    o.total_amount,
    o.status AS order_status,
    o.payment_status,
    o.payment_method,
    p.full_name AS customer_name,
    oi.product_id,
    prod.name AS product_name,
    oi.quantity,
    oi.unit_price,
    oi.vendor_id,
    v.business_name AS vendor_name,
    (oi.quantity * oi.unit_price) * (v.commission_rate / 100.0) AS commission_earned
FROM public.orders o
JOIN public.profiles p ON o.user_id = p.id
JOIN public.order_items oi ON o.id = oi.order_id
JOIN public.products prod ON oi.product_id = prod.id
JOIN public.profiles v ON oi.vendor_id = v.id;

-- 4. Enhanced Wallet Payment Processor
CREATE OR REPLACE FUNCTION public.process_wallet_payment(p_order_id UUID, p_user_id UUID, p_amount NUMERIC)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_current_balance NUMERIC;
    v_wallet_id UUID;
BEGIN
    -- Get wallet and lock for update
    SELECT id, balance INTO v_wallet_id, v_current_balance
    FROM public.wallets
    WHERE user_id = p_user_id
    FOR UPDATE;

    IF v_current_balance IS NULL OR v_current_balance < p_amount THEN
        RETURN jsonb_build_object('success', false, 'message', 'Insufficient wallet balance');
    END IF;

    -- Update balance
    UPDATE public.wallets
    SET balance = balance - p_amount, updated_at = now()
    WHERE id = v_wallet_id;

    -- Update order
    UPDATE public.orders
    SET status = 'processing', payment_status = 'paid', payment_method = 'wallet', updated_at = now()
    WHERE id = p_order_id;

    -- Log transaction
    INSERT INTO public.wallet_transactions (wallet_id, amount, type, reference_type, reference_id, description)
    VALUES (v_wallet_id, p_amount, 'debit', 'order', p_order_id, 'Payment for order #' || substring(p_order_id::text, 1, 8));

    -- Audit log
    INSERT INTO public.system_logs (user_id, action, details, severity)
    VALUES (p_user_id, 'WALLET_PAYMENT_SUCCESS', jsonb_build_object('order_id', p_order_id, 'amount', p_amount), 'info');

    RETURN jsonb_build_object('success', true, 'message', 'Payment successful', 'transaction_id', p_order_id::text);
END;
$$;

-- 5. Ensure Catalog Products View is up to date
DROP VIEW IF EXISTS public.catalog_products;
CREATE OR REPLACE VIEW public.catalog_products
WITH (security_invoker = true)
AS
SELECT
    p.id,
    p.vendor_id,
    p.category_id,
    p.category,
    p.name,
    p.price_kes,
    p.stock_count,
    p.description,
    p.images,
    p.in_stock,
    p.is_active,
    p.gender,
    p.tag,
    p.material,
    p.features,
    p.available_sizes,
    p.available_colors,
    p.sub_category,
    p.measurement_guide,
    p.featured,
    p.flash_sale_end,
    p.flash_sale_price,
    p.rating,
    p.reviews_count,
    p.created_at,
    p.updated_at,
    prof.full_name AS vendor_name,
    prof.business_name AS vendor_business_name,
    prof.rating AS vendor_rating,
    prof.avatar_url AS vendor_avatar
FROM public.products p
JOIN public.profiles prof ON p.vendor_id = prof.id
WHERE prof.status = 'active'
  AND p.is_active = true;

-- Log System update for Admin Dashboard & Payments
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SYSTEM_UPDATE', '{"module": "admin_intelligence_and_payments", "features": ["sales_trends", "inventory_health", "detailed_reports", "wallet_optimization"]}', 'info');
