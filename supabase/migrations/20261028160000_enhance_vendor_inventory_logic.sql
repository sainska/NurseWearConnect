-- Enhancement for Vendor Inventory Management
-- 1. Consolidate and secure the inventory update RPC
-- 2. Add trigger for automatic stock status (in_stock)
-- 3. Add performance indexes for product variations

-- Securely update product stock and visibility
CREATE OR REPLACE FUNCTION public.update_product_inventory_v2(
    p_product_id UUID,
    p_stock_count INTEGER DEFAULT NULL,
    p_is_active BOOLEAN DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE public.products
    SET
        stock_count = COALESCE(p_stock_count, stock_count),
        is_active = COALESCE(p_is_active, is_active),
        updated_at = NOW()
    WHERE id = p_product_id
      AND (vendor_id = auth.uid() OR public.get_user_role(auth.uid()) = 'admin');

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Product not found or unauthorized';
    END IF;
END;
$$;

-- Trigger to automatically update 'in_stock' based on 'stock_count'
CREATE OR REPLACE FUNCTION public.fn_sync_product_stock_status()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.stock_count <= 0 THEN
        NEW.in_stock := false;
    ELSE
        NEW.in_stock := true;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_product_stock_status ON public.products;
CREATE TRIGGER tr_sync_product_stock_status
BEFORE INSERT OR UPDATE OF stock_count ON public.products
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_product_stock_status();

-- Add GIN indexes for JSONB columns to support efficient filtering of variations/guides
CREATE INDEX IF NOT EXISTS idx_products_available_colors ON public.products USING GIN (available_colors);
CREATE INDEX IF NOT EXISTS idx_products_measurement_guide ON public.products USING GIN (measurement_guide);

-- Log the feature enhancement
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SYSTEM_UPDATE', '{"feature": "vendor_inventory", "changes": ["auto_stock_status", "v2_inventory_rpc", "variation_indexes"]}', 'info');
