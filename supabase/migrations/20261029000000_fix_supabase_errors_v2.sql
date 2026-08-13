-- COMPREHENSIVE SUPABASE ERROR FIX SCRIPT
-- Version: 2.0
-- Fixes: RLS Recursion, RPC Overloading (300 errors), Type Mismatches (42804 errors)

-- ==========================================
-- 1. DROP CONFLICTING RPCs (Cleanup Overloads)
-- ==========================================

-- Drop all versions of get_sales_trends to avoid 300 Multiple Choices
DROP FUNCTION IF EXISTS public.get_sales_trends(text, text, text, uuid);
DROP FUNCTION IF EXISTS public.get_sales_trends(timestamptz, timestamptz, text, uuid);
DROP FUNCTION IF EXISTS public.get_sales_trends_v2(text, uuid);

-- Drop inventory health variations
DROP FUNCTION IF EXISTS public.get_inventory_health(uuid);

-- Drop demand forecasting
DROP FUNCTION IF EXISTS public.get_demand_forecasting(uuid);

-- Drop dashboard stats
DROP FUNCTION IF EXISTS public.get_vendor_dashboard_stats(uuid, integer);

-- ==========================================
-- 2. RE-IMPLEMENT RPCS WITH CONSISTENT TYPES
-- ==========================================

-- A. get_sales_trends
CREATE OR REPLACE FUNCTION public.get_sales_trends(
    p_start_date TEXT DEFAULT NULL,
    p_end_date TEXT DEFAULT NULL,
    p_interval TEXT DEFAULT 'day',
    p_vendor_id UUID DEFAULT NULL
)
RETURNS TABLE (
    trend_date TEXT,
    revenue NUMERIC,
    order_count BIGINT
) LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_start TIMESTAMPTZ;
    v_end TIMESTAMPTZ;
BEGIN
    v_start := COALESCE(p_start_date::TIMESTAMPTZ, NOW() - INTERVAL '30 days');
    v_end := COALESCE(p_end_date::TIMESTAMPTZ, NOW());

    RETURN QUERY
    WITH date_series AS (
        SELECT generate_series(
            date_trunc(p_interval, v_start),
            date_trunc(p_interval, v_end),
            (1 || ' ' || p_interval)::INTERVAL
        ) as d
    )
    SELECT
        ds.d::TEXT as trend_date,
        COALESCE(SUM(oi.quantity * oi.unit_price), 0)::NUMERIC as revenue,
        COUNT(DISTINCT oi.order_id)::BIGINT as order_count
    FROM date_series ds
    LEFT JOIN public.order_items oi ON
        date_trunc(p_interval, oi.created_at) = ds.d AND
        (p_vendor_id IS NULL OR oi.vendor_id = p_vendor_id) AND
        oi.status = 'delivered'
    GROUP BY ds.d
    ORDER BY ds.d ASC;
END;
$$;

-- B. get_inventory_health
CREATE OR REPLACE FUNCTION public.get_inventory_health(p_vendor_id UUID DEFAULT NULL)
RETURNS TABLE (
    total_products BIGINT,
    low_stock BIGINT,
    out_of_stock BIGINT,
    total_stock_value NUMERIC
) LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*)::BIGINT as total_products,
        COUNT(*) FILTER (WHERE stock_count > 0 AND stock_count <= 5)::BIGINT as low_stock,
        COUNT(*) FILTER (WHERE stock_count = 0)::BIGINT as out_of_stock,
        COALESCE(SUM(price_kes * stock_count), 0)::NUMERIC as total_stock_value
    FROM public.products
    WHERE (p_vendor_id IS NULL OR vendor_id = p_vendor_id)
      AND is_active = true;
END;
$$;

-- C. get_demand_forecasting
CREATE OR REPLACE FUNCTION public.get_demand_forecasting(p_vendor_id UUID DEFAULT NULL)
RETURNS TABLE (
    product_id UUID,
    product_name TEXT,
    current_stock INTEGER,
    avg_daily_sales NUMERIC,
    forecasted_demand_30d NUMERIC,
    recommended_restock INTEGER,
    risk_level TEXT
) LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN QUERY
    WITH product_sales AS (
        SELECT
            p.id as pid,
            p.name as pname,
            p.stock_count as pstock,
            COALESCE(SUM(oi.quantity)::NUMERIC / 30.0, 0) as daily_avg
        FROM public.products p
        LEFT JOIN public.order_items oi ON oi.product_id = p.id
            AND oi.created_at >= (NOW() - INTERVAL '30 days')
            AND oi.status = 'delivered'
        WHERE (p_vendor_id IS NULL OR p.vendor_id = p_vendor_id)
          AND p.is_active = true
        GROUP BY p.id, p.name, p.stock_count
    )
    SELECT
        ps.pid as product_id,
        ps.pname as product_name,
        ps.pstock as current_stock,
        ps.daily_avg as avg_daily_sales,
        (ps.daily_avg * 30.0 * 1.2)::NUMERIC as forecasted_demand_30d, -- 20% buffer
        GREATEST(0, CEIL((ps.daily_avg * 30.0 * 1.2) - ps.pstock))::INTEGER as recommended_restock,
        CASE
            WHEN ps.pstock < (ps.daily_avg * 7) THEN 'HIGH'
            WHEN ps.pstock < (ps.daily_avg * 14) THEN 'MEDIUM'
            ELSE 'LOW'
        END as risk_level
    FROM product_sales ps
    ORDER BY ps.pstock ASC;
END;
$$;

-- D. get_vendor_dashboard_stats
CREATE OR REPLACE FUNCTION public.get_vendor_dashboard_stats(p_vendor_id UUID, p_days INTEGER DEFAULT 30)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_total_revenue NUMERIC;
    v_order_count INTEGER;
    v_best_sellers JSONB;
    v_sales_trends JSONB;
    v_stock_health JSONB;
    v_prev_revenue NUMERIC;
    v_prev_order_count INTEGER;
    v_start_date TIMESTAMPTZ;
    v_prev_start_date TIMESTAMPTZ;
BEGIN
    v_start_date := NOW() - (p_days || ' days')::INTERVAL;
    v_prev_start_date := NOW() - (p_days * 2 || ' days')::INTERVAL;

    -- Current Metrics
    SELECT
        COALESCE(SUM(quantity * unit_price), 0),
        COUNT(DISTINCT order_id)
    INTO v_total_revenue, v_order_count
    FROM public.order_items
    WHERE vendor_id = p_vendor_id AND status = 'delivered' AND created_at >= v_start_date;

    -- Previous Period Metrics
    SELECT
        COALESCE(SUM(quantity * unit_price), 0),
        COUNT(DISTINCT order_id)
    INTO v_prev_revenue, v_prev_order_count
    FROM public.order_items
    WHERE vendor_id = p_vendor_id AND status = 'delivered' AND created_at >= v_prev_start_date AND created_at < v_start_date;

    -- Trends
    SELECT jsonb_agg(t) INTO v_sales_trends FROM (
        SELECT * FROM public.get_sales_trends(v_start_date::text, NOW()::text, 'day', p_vendor_id)
    ) t;

    -- Best Sellers
    SELECT jsonb_agg(t) INTO v_best_sellers FROM (
        SELECT
            p.name,
            SUM(oi.quantity)::BIGINT as units_sold,
            SUM(oi.quantity * oi.unit_price)::NUMERIC as revenue
        FROM public.order_items oi
        JOIN public.products p ON oi.product_id = p.id
        WHERE oi.vendor_id = p_vendor_id AND oi.status = 'delivered' AND oi.created_at >= v_start_date
        GROUP BY p.name
        ORDER BY units_sold DESC
        LIMIT 5
    ) t;

    -- Stock Health
    SELECT row_to_json(h)::jsonb INTO v_stock_health FROM (
        SELECT * FROM public.get_inventory_health(p_vendor_id)
    ) h;

    RETURN jsonb_build_object(
        'revenue', v_total_revenue,
        'order_count', v_order_count,
        'best_sellers', COALESCE(v_best_sellers, '[]'::jsonb),
        'sales_trends', COALESCE(v_sales_trends, '[]'::jsonb),
        'stock_health', v_stock_health,
        'prev_revenue', v_prev_revenue,
        'prev_order_count', v_prev_order_count
    );
END;
$$;

-- ==========================================
-- 3. RESOLVE RLS RECURSION
-- ==========================================

-- Drop all problematic policies
DROP POLICY IF EXISTS "orders_access_policy" ON public.orders;
DROP POLICY IF EXISTS "orders_customer_access" ON public.orders;
DROP POLICY IF EXISTS "orders_vendor_access" ON public.orders;
DROP POLICY IF EXISTS "orders_admin_access" ON public.orders;
DROP POLICY IF EXISTS "orders_select_policy" ON public.orders;
DROP POLICY IF EXISTS "orders_customer_select" ON public.orders;
DROP POLICY IF EXISTS "orders_vendor_select" ON public.orders;
DROP POLICY IF EXISTS "orders_admin_all" ON public.orders;

DROP POLICY IF EXISTS "order_items_access_policy" ON public.order_items;
DROP POLICY IF EXISTS "order_items_customer_access" ON public.order_items;
DROP POLICY IF EXISTS "order_items_vendor_access" ON public.order_items;
DROP POLICY IF EXISTS "order_items_admin_access" ON public.order_items;
DROP POLICY IF EXISTS "order_items_select_policy" ON public.order_items;
DROP POLICY IF EXISTS "order_items_select_v10" ON public.order_items;
DROP POLICY IF EXISTS "order_items_vendor_select" ON public.order_items;
DROP POLICY IF EXISTS "order_items_customer_select" ON public.order_items;
DROP POLICY IF EXISTS "order_items_admin_all" ON public.order_items;

-- Security Definer Helper to break recursion
CREATE OR REPLACE FUNCTION public.check_vendor_access_to_order(p_order_id UUID, p_user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.order_items
        WHERE order_id = p_order_id AND vendor_id = p_user_id
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Fix Orders Policies
CREATE POLICY "orders_customer_select" ON public.orders
FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "orders_vendor_select" ON public.orders
FOR SELECT USING (
    auth.uid() = vendor_id OR
    public.check_vendor_access_to_order(id, auth.uid())
);

CREATE POLICY "orders_admin_all" ON public.orders
FOR ALL USING (public.get_user_role(auth.uid()) = 'admin');

-- Fix Order Items Policies
CREATE POLICY "order_items_vendor_select" ON public.order_items
FOR SELECT USING (auth.uid() = vendor_id);

CREATE POLICY "order_items_customer_select" ON public.order_items
FOR SELECT USING (
    EXISTS (
        SELECT 1 FROM public.orders
        WHERE id = order_items.order_id AND user_id = auth.uid()
    )
);

CREATE POLICY "order_items_admin_all" ON public.order_items
FOR ALL USING (public.get_user_role(auth.uid()) = 'admin');

-- ==========================================
-- 4. FINAL PERMISSIONS & LOGGING
-- ==========================================

GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO authenticated, service_role;

INSERT INTO public.system_logs (action, details, severity)
VALUES ('DATABASE_FIX_APPLIED', '{"version": "2.0", "description": "Unified RPCs and broke RLS recursion chains."}', 'info');
