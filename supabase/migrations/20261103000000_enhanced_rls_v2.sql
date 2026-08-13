-- Enhanced RLS Policies for NurseWear Connect
-- This migration adds missing RLS policies for all secondary tables.

-- ==========================================
-- 1. SYSTEM LOGS
-- ==========================================
ALTER TABLE public.system_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "system_logs_admin_all" ON public.system_logs;
CREATE POLICY "system_logs_admin_all" ON public.system_logs FOR ALL USING (public.is_admin());
CREATE POLICY "system_logs_self_insert" ON public.system_logs FOR INSERT WITH CHECK (true); -- Allow all to log actions

-- ==========================================
-- 2. MESSAGES
-- ==========================================
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "messages_access" ON public.messages;
CREATE POLICY "messages_access" ON public.messages
FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id OR public.is_admin());

CREATE POLICY "messages_insert" ON public.messages
FOR INSERT WITH CHECK (auth.uid() = sender_id);

-- ==========================================
-- 3. NOTIFICATIONS
-- ==========================================
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "notifications_self_access" ON public.notifications;
CREATE POLICY "notifications_self_access" ON public.notifications
FOR SELECT USING (auth.uid() = user_id OR public.is_admin());

CREATE POLICY "notifications_update" ON public.notifications
FOR UPDATE USING (auth.uid() = user_id);

-- ==========================================
-- 4. WALLET TRANSACTIONS
-- ==========================================
ALTER TABLE public.wallet_transactions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "wallet_transactions_self_access" ON public.wallet_transactions;
CREATE POLICY "wallet_transactions_self_access" ON public.wallet_transactions
FOR SELECT USING (EXISTS (SELECT 1 FROM public.wallets w WHERE w.id = wallet_id AND w.user_id = auth.uid()) OR public.is_admin());

-- ==========================================
-- 5. GENERATED REPORTS
-- ==========================================
ALTER TABLE public.generated_reports ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "reports_access" ON public.generated_reports;
CREATE POLICY "reports_access" ON public.generated_reports
FOR SELECT USING (generated_by = auth.uid() OR public.is_admin());

-- ==========================================
-- 6. ADDRESSES
-- ==========================================
ALTER TABLE public.addresses ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "addresses_self_all" ON public.addresses;
CREATE POLICY "addresses_self_all" ON public.addresses
FOR ALL USING (auth.uid() = user_id OR public.is_admin());

-- ==========================================
-- 7. CART ITEMS
-- ==========================================
ALTER TABLE public.cart_items ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "cart_items_self_all" ON public.cart_items;
CREATE POLICY "cart_items_self_all" ON public.cart_items
FOR ALL USING (auth.uid() = user_id);

-- ==========================================
-- 8. REVIEWS & FAVORITES
-- ==========================================
ALTER TABLE public.reviews ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "reviews_select" ON public.reviews;
CREATE POLICY "reviews_select" ON public.reviews FOR SELECT USING (true);
DROP POLICY IF EXISTS "reviews_self_all" ON public.reviews;
CREATE POLICY "reviews_self_all" ON public.reviews FOR ALL USING (auth.uid() = user_id OR public.is_admin());

ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "favorites_self_all" ON public.favorites;
CREATE POLICY "favorites_self_all" ON public.favorites FOR ALL USING (auth.uid() = user_id);

-- ==========================================
-- 9. MISC: RETURNS, SUBSCRIPTIONS, WAITLIST
-- ==========================================
ALTER TABLE public.return_requests ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "returns_self_select" ON public.return_requests;
CREATE POLICY "returns_self_select" ON public.return_requests FOR SELECT USING (auth.uid() = user_id OR public.is_admin());
DROP POLICY IF EXISTS "returns_self_insert" ON public.return_requests;
CREATE POLICY "returns_self_insert" ON public.return_requests FOR INSERT WITH CHECK (auth.uid() = user_id);

ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "subscriptions_self_all" ON public.subscriptions;
CREATE POLICY "subscriptions_self_all" ON public.subscriptions FOR ALL USING (auth.uid() = user_id OR public.is_admin());

ALTER TABLE public.product_waitlist ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "waitlist_self_all" ON public.product_waitlist;
CREATE POLICY "waitlist_self_all" ON public.product_waitlist FOR ALL USING (auth.uid() = user_id);

-- ==========================================
-- 10. REALTIME CONFIGURATION
-- ==========================================
-- Ensure all relevant tables are in the realtime publication safely
DO $$
BEGIN
    -- profiles
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'profiles') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.profiles;
    END IF;
    -- wallets
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'wallets') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.wallets;
    END IF;
    -- wallet_transactions
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'wallet_transactions') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.wallet_transactions;
    END IF;
    -- notifications
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'notifications') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.notifications;
    END IF;
    -- messages
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'messages') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    END IF;
    -- order_items
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'order_items') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.order_items;
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Error adding tables to publication: %', SQLERRM;
END $$;
