-- Enhance vendor analytics to include comparison with previous period
-- This allows calculating % growth for revenue and orders

CREATE OR REPLACE FUNCTION public.get_vendor_dashboard_stats(p_vendor_id UUID, p_days INTEGER DEFAULT 30)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_total_revenue NUMERIC;
    v_order_count INTEGER;
    v_avg_order_value NUMERIC;
    v_best_sellers JSONB;
    v_sales_trends JSONB;
    v_stock_health JSONB;
    v_start_date TIMESTAMPTZ;
    v_prev_start_date TIMESTAMPTZ;
    v_prev_revenue NUMERIC;
    v_prev_order_count INTEGER;
BEGIN
    v_start_date := NOW() - (p_days || ' days')::INTERVAL;
    v_prev_start_date := NOW() - (p_days * 2 || ' days')::INTERVAL;

    -- Current Period Stats
    SELECT
        COALESCE(SUM(quantity * unit_price), 0),
        COUNT(DISTINCT order_id)
    INTO v_total_revenue, v_order_count
    FROM public.order_items
    WHERE vendor_id = p_vendor_id
    AND status = 'delivered'
    AND created_at >= v_start_date;

    -- Previous Period Stats (for comparison)
    SELECT
        COALESCE(SUM(quantity * unit_price), 0),
        COUNT(DISTINCT order_id)
    INTO v_prev_revenue, v_prev_order_count
    FROM public.order_items
    WHERE vendor_id = p_vendor_id
    AND status = 'delivered'
    AND created_at >= v_prev_start_date
    AND created_at < v_start_date;

    -- Average order value
    IF v_order_count > 0 THEN
        v_avg_order_value := v_total_revenue / v_order_count;
    ELSE
        v_avg_order_value := 0;
    END IF;

    -- Best sellers
    SELECT jsonb_agg(t) INTO v_best_sellers FROM (
        SELECT p.name, SUM(oi.quantity) as units_sold, SUM(oi.quantity * oi.unit_price) as revenue
        FROM public.order_items oi
        JOIN public.products p ON oi.product_id = p.id
        WHERE oi.vendor_id = p_vendor_id
        AND oi.status = 'delivered'
        AND oi.created_at >= v_start_date
        GROUP BY p.name
        ORDER BY units_sold DESC
        LIMIT 5
    ) t;

    -- Sales trends
    SELECT jsonb_agg(t) INTO v_sales_trends FROM (
        WITH date_series AS (
            SELECT generate_series(v_start_date::date, NOW()::date, '1 day'::interval)::date as d
        )
        SELECT
            ds.d::text as date,
            COALESCE(SUM(oi.quantity * oi.unit_price), 0) as revenue,
            COUNT(DISTINCT oi.order_id) as order_count
        FROM date_series ds
        LEFT JOIN public.order_items oi ON oi.created_at::date = ds.d AND oi.vendor_id = p_vendor_id AND oi.status = 'delivered'
        GROUP BY ds.d
        ORDER BY ds.d ASC
    ) t;

    -- Stock health
    SELECT jsonb_build_object(
        'total_products', COUNT(*),
        'low_stock', COUNT(*) FILTER (WHERE stock_count > 0 AND stock_count <= 5),
        'out_of_stock', COUNT(*) FILTER (WHERE stock_count = 0),
        'total_stock_value', COALESCE(SUM(price_kes * stock_count), 0)
    ) INTO v_stock_health
    FROM public.products
    WHERE vendor_id = p_vendor_id;

    RETURN jsonb_build_object(
        'revenue', v_total_revenue,
        'order_count', v_order_count,
        'avg_order_value', v_avg_order_value,
        'best_sellers', COALESCE(v_best_sellers, '[]'::jsonb),
        'sales_trends', COALESCE(v_sales_trends, '[]'::jsonb),
        'stock_health', v_stock_health,
        'prev_revenue', v_prev_revenue,
        'prev_order_count', v_prev_order_count
    );
END;
$$;
