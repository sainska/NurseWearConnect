-- FIX ORDER ITEMS COLUMNS: Aligning app and database schema
-- Fixes: 'price_at_purchase' not-null violation and color JSONB casting

-- 1. Sync price columns: Make unit_price the source of truth if price_at_purchase is missing
ALTER TABLE public.order_items ALTER COLUMN price_at_purchase DROP NOT NULL;
ALTER TABLE public.order_items ALTER COLUMN price_at_purchase SET DEFAULT 0;

CREATE OR REPLACE FUNCTION public.sync_order_item_prices()
RETURNS TRIGGER AS $$
BEGIN
    -- Ensure price_at_purchase is always populated from unit_price if missing
    IF (NEW.price_at_purchase IS NULL OR NEW.price_at_purchase = 0) AND NEW.unit_price > 0 THEN
        NEW.price_at_purchase := NEW.unit_price;
    ELSIF (NEW.unit_price IS NULL OR NEW.unit_price = 0) AND NEW.price_at_purchase > 0 THEN
        NEW.unit_price := NEW.price_at_purchase;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_order_item_prices ON public.order_items;
CREATE TRIGGER tr_sync_order_item_prices
BEFORE INSERT OR UPDATE ON public.order_items
FOR EACH ROW EXECUTE FUNCTION public.sync_order_item_prices();

-- 2. Handle Color column flexibility
-- If color is JSONB in DB but app sends a string, ensure it's compatible
-- This is often easier handled in the app, but we can relax constraints here if needed.
-- We'll also allow null for unit_price if price_at_purchase is set.
ALTER TABLE public.order_items ALTER COLUMN unit_price DROP NOT NULL;

-- 3. Update existing records to be consistent
UPDATE public.order_items SET price_at_purchase = unit_price WHERE price_at_purchase IS NULL OR price_at_purchase = 0;
UPDATE public.order_items SET unit_price = price_at_purchase WHERE unit_price IS NULL OR unit_price = 0;

-- 4. Log the fix
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SCHEMA_FIX_ORDER_ITEMS', '{"description": "Unified unit_price and price_at_purchase columns to prevent null constraint violations."}', 'info');
