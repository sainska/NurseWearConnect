-- FIX PRICE TYPE MISMATCH: Changing integer columns to numeric to support decimals from app
-- This resolves the error: invalid input syntax for type integer: "1800.0"

-- 0. Drop dependent views
DROP VIEW IF EXISTS public.catalog_products;
DROP VIEW IF EXISTS public.student_catalog_view;

-- 1. Alter public.products table
ALTER TABLE public.products ALTER COLUMN price_kes TYPE numeric USING price_kes::numeric;
ALTER TABLE public.products ALTER COLUMN flash_sale_price TYPE numeric USING flash_sale_price::numeric;

-- 2. Check if other columns have similar issues
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'products' AND column_name = 'cost_price_kes') THEN
        ALTER TABLE public.products ALTER COLUMN cost_price_kes TYPE numeric USING cost_price_kes::numeric;
    END IF;
END $$;

-- 3. Recreate catalog_products view
CREATE OR REPLACE VIEW public.catalog_products
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
WHERE prof.status = 'active';

-- 4. Recreate student_catalog_view
CREATE OR REPLACE VIEW public.student_catalog_view AS
 SELECT p.id,
    p.name,
    p.description,
    p.price_kes,
    p.stock_count,
    p.in_stock,
    p.category,
    p.images,
    p.rating,
    p.reviews_count,
    p.vendor_id,
    v.business_name AS vendor_name,
    v.is_verified_vendor AS vendor_verified,
    v.status AS vendor_status
   FROM (public.products p
     JOIN public.profiles v ON ((p.vendor_id = v.id)))
  WHERE ((p.is_active = true) AND (v.status = 'active'::text) AND (p.stock_count > 0));

-- 5. Log the fix
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SCHEMA_FIX_PRICE_TYPE', '{"description": "Converted price_kes and flash_sale_price to numeric and recreated dependent views to resolve integer syntax errors."}', 'info');
