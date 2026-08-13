-- FINAL RLS FIX: Eliminating Recursion in Orders and Order Items
-- This migration drops ALL previous policies on these tables to ensure a clean state.

-- ==========================================
-- 1. DROP ALL EXISTING POLICIES
-- ==========================================

-- Drop policies for 'orders'
DROP POLICY IF EXISTS "Orders access policy" ON public.orders;
DROP POLICY IF EXISTS "orders_admin_all" ON public.orders;
DROP POLICY IF EXISTS "orders_customer_select" ON public.orders;
DROP POLICY IF EXISTS "orders_insert_policy" ON public.orders;
DROP POLICY IF EXISTS "orders_update_policy" ON public.orders;
DROP POLICY IF EXISTS "orders_vendor_select" ON public.orders;
DROP POLICY IF EXISTS "orders_access_policy" ON public.orders;
DROP POLICY IF EXISTS "orders_self_insert" ON public.orders;

-- Drop policies for 'order_items'
DROP POLICY IF EXISTS "Order items access policy" ON public.order_items;
DROP POLICY IF EXISTS "Users can insert their own order items" ON public.order_items;
DROP POLICY IF EXISTS "order_items_admin_all" ON public.order_items;
DROP POLICY IF EXISTS "order_items_customer_select" ON public.order_items;
DROP POLICY IF EXISTS "order_items_vendor_select" ON public.order_items;
DROP POLICY IF EXISTS "order_items_insert_policy" ON public.order_items;
DROP POLICY IF EXISTS "order_items_update_policy" ON public.order_items;
DROP POLICY IF EXISTS "order_items_access_policy" ON public.order_items;
DROP POLICY IF EXISTS "order_items_select_v10" ON public.order_items;
DROP POLICY IF EXISTS "order_items_select_policy" ON public.order_items;

-- ==========================================
-- 2. CREATE SECURITY DEFINER HELPERS
-- ==========================================
-- These functions run with the privileges of the creator (owner),
-- bypassing RLS checks on the tables they query.
-- This is the standard way to break RLS recursion in Supabase/Postgres.

-- Check if user is the customer who placed the order
CREATE OR REPLACE FUNCTION public.check_is_order_customer(p_order_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.orders
        WHERE id = p_order_id AND user_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Check if user is the vendor for any item in the order
CREATE OR REPLACE FUNCTION public.check_is_order_vendor(p_order_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.order_items
        WHERE order_id = p_order_id AND vendor_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ==========================================
-- 3. APPLY CLEAN POLICIES FOR ORDERS
-- ==========================================

-- Customers can see their own orders
CREATE POLICY "orders_customer_select" ON public.orders
FOR SELECT USING (auth.uid() = user_id);

-- Vendors can see orders if they are the main vendor OR have an item in it
CREATE POLICY "orders_vendor_select" ON public.orders
FOR SELECT USING (
    auth.uid() = vendor_id OR
    public.check_is_order_vendor(id)
);

-- Admins can do anything
CREATE POLICY "orders_admin_all" ON public.orders
FOR ALL USING (public.get_user_role(auth.uid()) = 'admin');

-- INSERT: Anyone logged in can create an order for themselves
CREATE POLICY "orders_insert" ON public.orders
FOR INSERT WITH CHECK (auth.uid() = user_id);

-- UPDATE: Only admins (for status) or maybe system
CREATE POLICY "orders_update" ON public.orders
FOR UPDATE USING (public.get_user_role(auth.uid()) = 'admin');

-- ==========================================
-- 4. APPLY CLEAN POLICIES FOR ORDER_ITEMS
-- ==========================================

-- Vendors can see their own items
CREATE POLICY "order_items_vendor_select" ON public.order_items
FOR SELECT USING (auth.uid() = vendor_id);

-- Customers can see items in their orders
CREATE POLICY "order_items_customer_select" ON public.order_items
FOR SELECT USING (public.check_is_order_customer(order_id));

-- Admins can do anything
CREATE POLICY "order_items_admin_all" ON public.order_items
FOR ALL USING (public.get_user_role(auth.uid()) = 'admin');

-- INSERT: Customer can insert items for their own orders
CREATE POLICY "order_items_insert" ON public.order_items
FOR INSERT WITH CHECK (public.check_is_order_customer(order_id));

-- UPDATE: Vendor can update status of their items
CREATE POLICY "order_items_vendor_update" ON public.order_items
FOR UPDATE USING (auth.uid() = vendor_id);

-- ==========================================
-- 5. FINAL LOGGING
-- ==========================================

INSERT INTO public.system_logs (action, details, severity)
VALUES ('RLS_RECURSION_FIX_V3', '{"description": "Eliminated all recursion chains in orders/order_items using Security Definers."}', 'info');
