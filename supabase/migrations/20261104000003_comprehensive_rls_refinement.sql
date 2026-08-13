-- Comprehensive RLS Refinement for Security and Privacy
-- Run: supabase db push

-- 1. Profiles RLS: More restrictive but functional
DROP POLICY IF EXISTS "profiles_universal_select" ON public.profiles;
CREATE POLICY "profiles_select_policy" ON public.profiles
FOR SELECT USING (
    auth.uid() = id -- Self
    OR public.is_admin() -- Admin
    OR role = 'vendor' -- Everyone can see vendor basic info
);

-- 2. Products RLS: Ensure vendors can always see and manage their products
DROP POLICY IF EXISTS "products_select_policy" ON public.products;
CREATE POLICY "products_select_policy" ON public.products
FOR SELECT USING (
    is_active = true -- Active products are public
    OR auth.uid() = vendor_id -- Vendor sees their own (even if inactive)
    OR public.is_admin() -- Admin sees everything
);

-- 3. Orders RLS: Vendors see orders where they are the assigned vendor or have items
DROP POLICY IF EXISTS "orders_access_policy" ON public.orders;
CREATE POLICY "orders_access_policy" ON public.orders
FOR SELECT USING (
    auth.uid() = user_id -- Customer sees their own orders
    OR auth.uid() = vendor_id -- Assigned vendor sees the order
    OR public.is_admin() -- Admin sees all
    OR EXISTS (
        -- Vendor sees the order if it contains one of their items
        SELECT 1 FROM public.order_items oi
        WHERE oi.order_id = public.orders.id
        AND oi.vendor_id = auth.uid()
    )
);

-- 4. Order Items RLS: Refined for multi-vendor support
DROP POLICY IF EXISTS "order_items_access_policy" ON public.order_items;
CREATE POLICY "order_items_access_policy" ON public.order_items
FOR SELECT USING (
    auth.uid() = vendor_id -- Vendor sees their own items
    OR public.is_admin() -- Admin sees all
    OR EXISTS (
        -- Customer sees the items in their own order
        SELECT 1 FROM public.orders o
        WHERE o.id = order_id
        AND o.user_id = auth.uid()
    )
);

-- 5. Wallet Transactions RLS: Ensure privacy
ALTER TABLE public.wallet_transactions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "wallet_transactions_self_select" ON public.wallet_transactions;
CREATE POLICY "wallet_transactions_self_select" ON public.wallet_transactions
FOR SELECT USING (
    EXISTS (
        SELECT 1 FROM public.wallets w
        WHERE w.id = wallet_id
        AND (w.user_id = auth.uid() OR public.is_admin())
    )
);

-- 6. Notifications RLS: Ensure privacy
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "notifications_self_select" ON public.notifications;
CREATE POLICY "notifications_self_select" ON public.notifications
FOR SELECT USING (user_id = auth.uid() OR public.is_admin());

-- 7. Messages RLS: Ensure privacy
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "messages_access_policy" ON public.messages;
CREATE POLICY "messages_access_policy" ON public.messages
FOR SELECT USING (sender_id = auth.uid() OR receiver_id = auth.uid() OR public.is_admin());

-- 8. Payouts RLS (Fixing from previous migration if needed)
DROP POLICY IF EXISTS "Users can view own payouts" ON public.payouts;
CREATE POLICY "payouts_self_select" ON public.payouts
FOR SELECT USING (vendor_id = auth.uid() OR public.is_admin());

-- 9. System Logs RLS: Only Admin should see all
DROP POLICY IF EXISTS "system_logs_self_select" ON public.system_logs;
CREATE POLICY "system_logs_admin_only" ON public.system_logs
FOR SELECT USING (public.is_admin() OR user_id = auth.uid());

-- Ensure the is_admin helper is efficient
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN AS $$
BEGIN
  RETURN (
    SELECT (raw_user_meta_data->>'role') = 'admin'
    FROM auth.users
    WHERE id = auth.uid()
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
