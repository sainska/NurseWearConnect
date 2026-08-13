-- Fix infinite recursion in RLS policies for orders and order_items
-- Run: supabase db push

-- 1. Helper to check if a vendor has items in an order without triggering RLS recursion
CREATE OR REPLACE FUNCTION public.vendor_has_order_item(p_order_id UUID, p_vendor_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM public.order_items
    WHERE order_id = p_order_id
    AND vendor_id = p_vendor_id
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Helper to check if a user owns an order without triggering RLS recursion
CREATE OR REPLACE FUNCTION public.user_owns_order(p_order_id UUID, p_user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM public.orders
    WHERE id = p_order_id
    AND user_id = p_user_id
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Update Orders Policy
DROP POLICY IF EXISTS "orders_access_policy" ON public.orders;
CREATE POLICY "orders_access_policy" ON public.orders
FOR SELECT USING (
    auth.uid() = user_id -- Customer
    OR auth.uid() = vendor_id -- Assigned vendor
    OR public.is_admin() -- Admin
    OR (
        CASE
            WHEN auth.uid() IS NOT NULL THEN public.vendor_has_order_item(id, auth.uid())
            ELSE FALSE
        END
    )
);

-- 4. Update Order Items Policy
DROP POLICY IF EXISTS "order_items_access_policy" ON public.order_items;
CREATE POLICY "order_items_access_policy" ON public.order_items
FOR SELECT USING (
    auth.uid() = vendor_id -- Vendor
    OR public.is_admin() -- Admin
    OR (
        CASE
            WHEN auth.uid() IS NOT NULL THEN public.user_owns_order(order_id, auth.uid())
            ELSE FALSE
        END
    )
);

-- 5. Finalize permissions for service_role to ensure Edge Functions skip RLS
GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO service_role;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO service_role;
