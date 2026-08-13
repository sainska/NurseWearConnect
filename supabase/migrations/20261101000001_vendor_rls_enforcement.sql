-- Robust RLS enforcement for Vendor Order Management
-- Ensures vendors ONLY see orders that have been successfully paid.

-- 1. Orders Table: Vendors see paid orders assigned to them
DROP POLICY IF EXISTS "orders_vendor_select" ON public.orders;
DROP POLICY IF EXISTS "orders_access_policy" ON public.orders;

CREATE POLICY "orders_access_policy_v2" ON public.orders
FOR SELECT USING (
    (auth.uid() = user_id) OR
    (auth.uid() = vendor_id AND payment_status = 'paid') OR
    (public.get_user_role(auth.uid()) = 'admin')
);

-- 2. Order Items Table: Vendors see items only for paid orders
DROP POLICY IF EXISTS "order_items_vendor_select" ON public.order_items;
DROP POLICY IF EXISTS "order_items_access_policy" ON public.order_items;

CREATE POLICY "order_items_access_policy_v2" ON public.order_items
FOR SELECT USING (
    (public.check_is_order_customer(order_id)) OR
    (auth.uid() = vendor_id AND EXISTS (
        SELECT 1 FROM public.orders o
        WHERE o.id = order_id AND o.payment_status = 'paid'
    )) OR
    (public.get_user_role(auth.uid()) = 'admin')
);

-- 3. Order Items Update: Vendors can only update status of their own items
DROP POLICY IF EXISTS "order_items_vendor_update" ON public.order_items;

CREATE POLICY "order_items_vendor_update_v2" ON public.order_items
FOR UPDATE USING (auth.uid() = vendor_id)
WITH CHECK (auth.uid() = vendor_id);

-- 4. Audit Log
INSERT INTO public.system_logs (user_id, action, details, severity)
VALUES (auth.uid(), 'SECURITY_ENFORCEMENT', '{"module": "orders", "description": "Enforced strict Vendor-Payment RLS."}', 'info');
