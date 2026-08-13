-- Finalize Vendor Inventory Schema for Enhanced UI
-- 1. Add constraints to products table
-- 2. Update catalog view to ensure consistency
-- 3. Add helper for vendor product statistics

-- Ensure stock count is never negative
ALTER TABLE public.products
ADD CONSTRAINT products_stock_count_check CHECK (stock_count >= 0);

-- Ensure available_colors and measurement_guide are valid JSON objects if not null
ALTER TABLE public.products
ADD CONSTRAINT products_available_colors_check CHECK (jsonb_typeof(available_colors) = 'array'),
ADD CONSTRAINT products_measurement_guide_check CHECK (jsonb_typeof(measurement_guide) = 'object');

-- Enhance catalog view for performance and clarity
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

-- Helper function to get product-level performance for vendors
CREATE OR REPLACE FUNCTION public.get_product_performance(p_product_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_total_sold INTEGER;
    v_revenue NUMERIC;
    v_last_ordered TIMESTAMPTZ;
BEGIN
    SELECT
        COALESCE(SUM(quantity), 0),
        COALESCE(SUM(quantity * unit_price), 0),
        MAX(created_at)
    INTO v_total_sold, v_revenue, v_last_ordered
    FROM public.order_items
    WHERE product_id = p_product_id
      AND status = 'delivered';

    RETURN jsonb_build_object(
        'total_sold', v_total_sold,
        'revenue', v_revenue,
        'last_ordered', v_last_ordered
    );
END;
$$;

-- Log the schema finalization
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SCHEMA_FINALIZATION', '{"module": "inventory", "status": "completed", "details": "Added constraints and performance helpers"}', 'info');
