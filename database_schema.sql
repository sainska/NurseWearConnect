-- NurseWearConnect Unified Database Schema
-- Single Source of Truth for the Backend
-- Consolidated & Unified: 2026-10-27

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

-- ==========================================
-- 1. EXTENSIONS
-- ==========================================

CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";

-- ==========================================
-- 2. HELPER FUNCTIONS & TRIGGERS
-- ==========================================

-- Get user role from auth.users metadata to avoid RLS recursion
CREATE OR REPLACE FUNCTION public.get_user_role(user_id uuid) RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  user_role text;
BEGIN
  SELECT (raw_user_meta_data->>'role') INTO user_role FROM auth.users WHERE id = user_id;
  RETURN user_role;
END;
$$;

-- Check if current user is an admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean AS $$
BEGIN
  RETURN (SELECT raw_user_meta_data->>'role' FROM auth.users WHERE id = auth.uid()) = 'admin';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Check order access to break RLS chains
CREATE OR REPLACE FUNCTION public.check_order_access(p_order_id uuid)
RETURNS boolean AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.orders
        WHERE id = p_order_id
        AND (user_id = auth.uid() OR vendor_id = auth.uid() OR public.is_admin())
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- JSONB diff helper for logging changes
CREATE OR REPLACE FUNCTION public.jsonb_diff(l JSONB, r JSONB)
RETURNS JSONB AS $$
DECLARE
    result JSONB := '{}'::jsonb;
    key TEXT;
    value JSONB;
BEGIN
    FOR key, value IN SELECT * FROM jsonb_each(l) LOOP
        IF NOT (r ? key) OR (r -> key) != value THEN
            result := result || jsonb_build_object(key, value);
        END IF;
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Global updated_at handler
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ==========================================
-- 3. CORE TABLES
-- ==========================================

-- User Profiles
CREATE TABLE IF NOT EXISTS public.profiles (
    id uuid PRIMARY KEY REFERENCES auth.users(id),
    full_name text,
    email text UNIQUE,
    phone_number text,
    role text DEFAULT 'student'::text,
    status text DEFAULT 'active'::text,
    institution text,
    business_name text,
    business_description text,
    bio text,
    location text,
    address text,
    avatar_url text,
    commission_rate numeric DEFAULT 10.0,
    rating numeric DEFAULT 5.0,
    reviews_count integer DEFAULT 0,
    is_verified_vendor boolean DEFAULT false,
    total_sales_count integer DEFAULT 0,
    loyalty_points integer DEFAULT 0,
    loyalty_tier text DEFAULT 'bronze',
    measurements jsonb DEFAULT '{"bust": "0\"", "hips": "0\"", "waist": "0\""}'::jsonb,
    business_license_url text,
    document_status text DEFAULT 'pending'::text,
    paystack_recipient_code text,
    bank_code text,
    bank_account_number text,
    biometric_enabled boolean DEFAULT false,
    notifications_enabled boolean DEFAULT true,
    monthly_sales_target numeric DEFAULT 100000.0,
    last_login timestamp with time zone,
    status_notes text,
    rejection_reason text,
    fcm_token text,
    referral_code text UNIQUE DEFAULT substring(md5(random()::text) from 1 for 8),
    referred_by uuid REFERENCES public.profiles(id),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT profiles_role_check CHECK (role = ANY (ARRAY['student', 'professional', 'nurse', 'vendor', 'admin'])),
    CONSTRAINT profiles_status_check CHECK (status = ANY (ARRAY['active', 'pending', 'suspended', 'rejected', 'deleted']))
);

-- Wallets & Finance
CREATE TABLE IF NOT EXISTS public.wallets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE UNIQUE,
    balance numeric(12, 2) DEFAULT 0.0 CHECK (balance >= 0),
    currency text DEFAULT 'KES',
    is_locked boolean DEFAULT false,
    updated_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.wallet_transactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id uuid REFERENCES public.wallets(id) ON DELETE CASCADE,
    amount numeric(12, 2) NOT NULL,
    type text CHECK (type IN ('credit', 'debit')),
    reference_type text,
    reference_id uuid,
    description text,
    created_at timestamp with time zone DEFAULT now()
);

-- Addresses
CREATE TABLE IF NOT EXISTS public.addresses (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    name text NOT NULL,
    address_line text NOT NULL,
    city text,
    is_default boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now()
);

-- Catalog & Categories
CREATE TABLE IF NOT EXISTS public.categories (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    name text NOT NULL UNIQUE,
    description text,
    icon_name text,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.products (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    vendor_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    category_id uuid REFERENCES public.categories(id),
    category text, -- Denormalized for simpler app logic
    name text NOT NULL,
    price_kes numeric NOT NULL CHECK (price_kes >= 0),
    stock_count integer DEFAULT 0 CHECK (stock_count >= 0),
    description text,
    images text[] DEFAULT '{}'::text[],
    in_stock boolean DEFAULT true,
    is_active boolean DEFAULT true,
    gender text DEFAULT 'Unisex',
    tag text,
    material text,
    features text[] DEFAULT '{}'::text[],
    available_sizes text[] DEFAULT '{XS,S,M,L,XL,XXL}'::text[],
    available_colors jsonb DEFAULT '[]'::jsonb CHECK (jsonb_typeof(available_colors) = 'array'),
    sub_category text,
    measurement_guide jsonb DEFAULT '{}'::jsonb CHECK (jsonb_typeof(measurement_guide) = 'object'),
    featured boolean DEFAULT false,
    flash_sale_end timestamp with time zone,
    flash_sale_price numeric,
    rating numeric DEFAULT 5.0,
    reviews_count integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);

-- Orders System
CREATE TABLE IF NOT EXISTS public.orders (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    vendor_id uuid REFERENCES public.profiles(id), -- Nullable for multi-vendor main order
    total_amount numeric NOT NULL,
    discount_amount numeric DEFAULT 0,
    final_amount numeric NOT NULL,
    status text DEFAULT 'pending'::text,
    payment_status text DEFAULT 'pending',
    payment_method text,
    shipping_address text,
    shipping_method text DEFAULT 'Standard',
    shipping_cost numeric DEFAULT 0,
    currency text DEFAULT 'KES',
    payment_id text,
    is_fitting_service boolean DEFAULT false,
    digital_receipt_enabled boolean DEFAULT true,
    coupon_code text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT orders_status_check CHECK (status = ANY (ARRAY['pending', 'processing', 'shipped', 'delivered', 'cancelled', 'failed', 'returned']))
);

CREATE TABLE IF NOT EXISTS public.order_items (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id uuid REFERENCES public.orders(id) ON DELETE CASCADE,
    product_id uuid REFERENCES public.products(id),
    vendor_id uuid REFERENCES public.profiles(id),
    quantity integer NOT NULL,
    unit_price numeric NOT NULL,
    size text,
    color text,
    status text DEFAULT 'pending',
    delivery_fee numeric DEFAULT 0,
    fulfillment_data jsonb DEFAULT '{}'::jsonb,
    has_embroidery boolean DEFAULT false,
    embroidery_name text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT order_items_status_check CHECK (status = ANY (ARRAY['pending', 'processing', 'shipped', 'delivered', 'cancelled', 'returned']))
);

CREATE TABLE IF NOT EXISTS public.order_status_history (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id uuid REFERENCES public.orders(id) ON DELETE CASCADE,
    status text NOT NULL,
    notes text,
    changed_by uuid REFERENCES public.profiles(id),
    created_at timestamp with time zone DEFAULT now()
);

-- Cart Persistence
CREATE TABLE IF NOT EXISTS public.cart_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    product_id uuid REFERENCES public.products(id) ON DELETE CASCADE NOT NULL,
    quantity integer NOT NULL DEFAULT 1 CHECK (quantity > 0),
    size text NOT NULL,
    color_name text,
    color_hex bigint,
    embroidery_name text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    UNIQUE(user_id, product_id, size, color_name, embroidery_name)
);

-- Payments
CREATE TABLE IF NOT EXISTS public.payments (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id uuid REFERENCES public.orders(id) ON DELETE CASCADE,
    transaction_id text UNIQUE,
    amount numeric NOT NULL,
    payment_method text,
    currency text DEFAULT 'KES',
    status text DEFAULT 'pending'::text,
    provider_response jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);

-- Notifications & Messaging
CREATE TABLE IF NOT EXISTS public.notifications (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    title text NOT NULL,
    body text NOT NULL,
    type text DEFAULT 'info'::text,
    category text DEFAULT 'general'::text,
    priority_level text DEFAULT 'normal'::text,
    is_read boolean DEFAULT false,
    is_archived boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.messages (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    sender_id uuid REFERENCES auth.users(id),
    receiver_id uuid REFERENCES auth.users(id),
    message text NOT NULL,
    image_url text,
    priority text DEFAULT 'normal',
    category text DEFAULT 'direct',
    is_read boolean DEFAULT false,
    is_delivered boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now()
);

-- Marketing, Reviews & Favorites
CREATE TABLE IF NOT EXISTS public.banners (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    vendor_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    title text NOT NULL,
    subtitle text,
    image_url text NOT NULL,
    action_link text, -- e.g. 'category:Scrubs' or 'product:uuid'
    status text DEFAULT 'pending'::text, -- 'pending', 'approved', 'rejected'
    is_active boolean DEFAULT true,
    sort_order integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT banners_status_check CHECK (status = ANY (ARRAY['pending', 'approved', 'rejected']))
);

CREATE TABLE IF NOT EXISTS public.coupons (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    code text NOT NULL UNIQUE,
    discount_percent integer,
    discount_value numeric,
    expiry_date timestamp with time zone,
    is_active boolean DEFAULT true,
    usage_limit integer DEFAULT 100,
    created_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.reviews (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id uuid REFERENCES public.products(id) ON DELETE CASCADE,
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    rating integer CHECK (rating >= 1 AND rating <= 5),
    comment text,
    images text[] DEFAULT '{}'::text[],
    created_at timestamp with time zone DEFAULT now(),
    UNIQUE(product_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.favorites (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
    product_id uuid REFERENCES public.products(id) ON DELETE CASCADE,
    created_at timestamp with time zone DEFAULT now(),
    UNIQUE(user_id, product_id)
);

-- Returns, Subscriptions, Waitlist & Bundles
CREATE TABLE IF NOT EXISTS public.return_requests (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id uuid REFERENCES public.orders(id) ON DELETE CASCADE NOT NULL,
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    reason text NOT NULL,
    status text DEFAULT 'pending' NOT NULL,
    admin_notes text,
    images text[] DEFAULT '{}'::text[],
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT return_requests_status_check CHECK (status IN ('pending', 'approved', 'rejected', 'item_received', 'refunded'))
);

CREATE TABLE IF NOT EXISTS public.subscriptions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    product_id uuid REFERENCES public.products(id) ON DELETE CASCADE NOT NULL,
    quantity integer DEFAULT 1 CHECK (quantity > 0),
    size text,
    color text,
    frequency_days integer DEFAULT 30,
    status text DEFAULT 'active' CHECK (status IN ('active', 'paused', 'cancelled')),
    next_delivery_date timestamp with time zone DEFAULT (now() + interval '30 days'),
    shipping_address_id uuid REFERENCES public.addresses(id),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.product_waitlist (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
    product_id uuid REFERENCES public.products(id) ON DELETE CASCADE NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    notified boolean DEFAULT false,
    UNIQUE(user_id, product_id, notified)
);

CREATE TABLE IF NOT EXISTS public.bundles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    description text,
    discount_percent numeric NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.bundle_items (
    bundle_id uuid REFERENCES public.bundles(id) ON DELETE CASCADE,
    product_id uuid REFERENCES public.products(id) ON DELETE CASCADE,
    PRIMARY KEY (bundle_id, product_id)
);

-- System Logs & Reports
CREATE TABLE IF NOT EXISTS public.system_logs (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid REFERENCES public.profiles(id),
    action text NOT NULL,
    details jsonb DEFAULT '{}'::jsonb,
    severity text DEFAULT 'info'::text,
    created_at timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.generated_reports (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    report_type text NOT NULL, -- 'SALES', 'INVENTORY', 'VENDOR_PERFORMANCE', 'COUPON'
    format text NOT NULL, -- 'PDF', 'EXCEL'
    file_url text,
    generated_by uuid REFERENCES public.profiles(id),
    metadata jsonb DEFAULT '{}'::jsonb,
    verification_code text UNIQUE DEFAULT encode(gen_random_bytes(12), 'hex'),
    created_at timestamp with time zone DEFAULT now()
);

-- ==========================================
-- 4. CORE TRIGGERS
-- ==========================================

-- Auto-update updated_at for all tables
DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS tr_update_updated_at ON public.%I', t);
        EXECUTE format('CREATE TRIGGER tr_update_updated_at BEFORE UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()', t);
    END LOOP;
END $$;

-- Handle New User Profile Creation
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, full_name, email, role, status, avatar_url)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
    NEW.email,
    COALESCE(NEW.raw_user_meta_data->>'role', 'student'),
    CASE WHEN (NEW.raw_user_meta_data->>'role') = 'vendor' THEN 'pending' ELSE 'active' END,
    COALESCE(NEW.raw_user_meta_data->>'avatar_url', '')
  );

  -- Create wallet for new user
  INSERT INTO public.wallets (user_id) VALUES (NEW.id);

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Handle New User Profile Creation
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Product Category Sync
CREATE OR REPLACE FUNCTION public.sync_product_category_id() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.category IS NOT NULL AND NEW.category_id IS NULL THEN
        SELECT id INTO NEW.category_id FROM public.categories WHERE name = NEW.category LIMIT 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_product_category_id ON public.products;
CREATE TRIGGER tr_sync_product_category_id BEFORE INSERT OR UPDATE OF category ON public.products FOR EACH ROW EXECUTE FUNCTION public.sync_product_category_id();

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

-- Multi-vendor Order Logic
CREATE OR REPLACE FUNCTION public.fn_sync_order_item_vendor() RETURNS TRIGGER AS $$
BEGIN
    SELECT vendor_id INTO NEW.vendor_id FROM public.products WHERE id = NEW.product_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER tr_sync_order_item_vendor BEFORE INSERT ON public.order_items FOR EACH ROW EXECUTE FUNCTION public.fn_sync_order_item_vendor();

-- Order Status Aggregation
CREATE OR REPLACE FUNCTION public.fn_sync_order_status_from_items() RETURNS TRIGGER AS $$
DECLARE
    total_items integer; delivered_items integer; cancelled_items integer; processing_items integer; new_status text;
BEGIN
    SELECT COUNT(*), COUNT(*) FILTER (WHERE status = 'delivered'), COUNT(*) FILTER (WHERE status = 'cancelled'), COUNT(*) FILTER (WHERE status = 'processing' OR status = 'shipped')
    INTO total_items, delivered_items, cancelled_items, processing_items FROM public.order_items WHERE order_id = NEW.order_id;
    IF total_items = delivered_items THEN new_status := 'delivered'; ELSIF total_items = cancelled_items THEN new_status := 'cancelled'; ELSIF processing_items > 0 THEN new_status := 'processing'; ELSE new_status := 'pending'; END IF;
    UPDATE public.orders SET status = new_status, updated_at = now() WHERE id = NEW.order_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER tr_sync_order_status_from_items AFTER UPDATE OF status ON public.order_items FOR EACH ROW EXECUTE FUNCTION public.fn_sync_order_status_from_items();

-- Loyalty Points Auto-reward
CREATE OR REPLACE FUNCTION public.fn_award_loyalty_points() RETURNS TRIGGER AS $$
DECLARE v_points_to_award integer;
BEGIN
    IF NEW.status = 'delivered' AND (OLD.status IS DISTINCT FROM 'delivered') THEN
        v_points_to_award := FLOOR((NEW.quantity * NEW.unit_price) / 100);
        IF v_points_to_award > 0 THEN
            UPDATE public.profiles SET loyalty_points = loyalty_points + v_points_to_award WHERE id = (SELECT user_id FROM public.orders WHERE id = NEW.order_id);
            INSERT INTO public.system_logs (user_id, action, details) VALUES ((SELECT user_id FROM public.orders WHERE id = NEW.order_id), 'LOYALTY_AWARD', jsonb_build_object('points', v_points_to_award));
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER tr_award_loyalty_points AFTER UPDATE ON public.order_items FOR EACH ROW EXECUTE FUNCTION public.fn_award_loyalty_points();

-- ==========================================
-- 5. RPC FUNCTIONS (Consolidated API)
-- ==========================================

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

-- Vendor Dashboard Metrics
CREATE OR REPLACE FUNCTION public.get_vendor_dashboard_stats(p_vendor_id UUID, p_days INTEGER DEFAULT 30)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_total_revenue NUMERIC; v_order_count INTEGER; v_avg_order_value NUMERIC; v_best_sellers JSONB; v_sales_trends JSONB; v_stock_health JSONB; v_start_date TIMESTAMPTZ;
BEGIN
    v_start_date := NOW() - (p_days || ' days')::INTERVAL;

    -- Aggregate from order_items for multi-vendor accuracy and only 'delivered' status for actual earnings
    SELECT COALESCE(SUM(quantity * unit_price), 0), COUNT(DISTINCT order_id)
    INTO v_total_revenue, v_order_count
    FROM public.order_items WHERE vendor_id = p_vendor_id AND status = 'delivered' AND created_at >= v_start_date;

    IF v_order_count > 0 THEN v_avg_order_value := v_total_revenue / v_order_count; ELSE v_avg_order_value := 0; END IF;

    SELECT jsonb_agg(t) INTO v_best_sellers FROM (SELECT p.name, SUM(oi.quantity) as units_sold, SUM(oi.quantity * oi.unit_price) as revenue FROM public.order_items oi JOIN public.products p ON oi.product_id = p.id WHERE oi.vendor_id = p_vendor_id AND oi.status = 'delivered' AND oi.created_at >= v_start_date GROUP BY p.name ORDER BY units_sold DESC LIMIT 5) t;

    SELECT jsonb_agg(t) INTO v_sales_trends FROM (WITH date_series AS (SELECT generate_series(v_start_date::date, NOW()::date, '1 day'::interval)::date as d) SELECT ds.d::text as date, COALESCE(SUM(oi.quantity * oi.unit_price), 0) as revenue, COUNT(DISTINCT oi.order_id) as order_count FROM date_series ds LEFT JOIN public.order_items oi ON oi.created_at::date = ds.d AND oi.vendor_id = p_vendor_id AND oi.status = 'delivered' GROUP BY ds.d ORDER BY ds.d ASC) t;

    SELECT jsonb_build_object('total_products', COUNT(*), 'low_stock', COUNT(*) FILTER (WHERE stock_count > 0 AND stock_count <= 5), 'out_of_stock', COUNT(*) FILTER (WHERE stock_count = 0), 'total_stock_value', COALESCE(SUM(price_kes * stock_count), 0)) INTO v_stock_health FROM public.products WHERE vendor_id = p_vendor_id;

    RETURN jsonb_build_object('revenue', v_total_revenue, 'order_count', v_order_count, 'avg_order_value', v_avg_order_value, 'best_sellers', COALESCE(v_best_sellers, '[]'::jsonb), 'sales_trends', COALESCE(v_sales_trends, '[]'::jsonb), 'stock_health', v_stock_health);
END;
$$;

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

-- Wallet Payment Atomic Processor
CREATE OR REPLACE FUNCTION public.process_wallet_payment(p_order_id UUID, p_user_id UUID, p_amount NUMERIC)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE v_current_balance NUMERIC;
BEGIN
    SELECT balance INTO v_current_balance FROM public.wallets WHERE user_id = p_user_id FOR UPDATE;
    IF v_current_balance IS NULL OR v_current_balance < p_amount THEN RETURN jsonb_build_object('success', false, 'message', 'Insufficient wallet balance'); END IF;
    UPDATE public.wallets SET balance = balance - p_amount, updated_at = now() WHERE user_id = p_user_id;
    UPDATE public.orders SET status = 'paid', payment_status = 'paid', payment_method = 'wallet', updated_at = now() WHERE id = p_order_id;
    INSERT INTO public.wallet_transactions (wallet_id, amount, type, reference_type, reference_id, description) SELECT id, p_amount, 'debit', 'order', p_order_id, 'Payment for order #' || substring(p_order_id::text, 1, 8) FROM public.wallets WHERE user_id = p_user_id;
    INSERT INTO public.system_logs (user_id, action, details) VALUES (p_user_id, 'WALLET_PAYMENT_SUCCESS', jsonb_build_object('order_id', p_order_id, 'amount', p_amount));
    RETURN jsonb_build_object('success', true, 'message', 'Payment successful');
END;
$$;

-- ==========================================
-- 6. VIEWS
-- ==========================================

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

CREATE OR REPLACE VIEW public.vendor_order_feed
WITH (security_invoker = true)
AS
SELECT
    oi.id as order_item_id,
    oi.order_id,
    oi.product_id,
    oi.vendor_id,
    oi.quantity,
    oi.unit_price,
    oi.size,
    oi.color,
    oi.status as item_status,
    oi.embroidery_name,
    oi.created_at,
    o.user_id as customer_id,
    p.full_name as customer_name,
    p.email as customer_email,
    p.phone_number as customer_phone,
    prod.name as product_name,
    prod.images as product_images,
    o.total_amount as order_total,
    o.status as order_status,
    o.shipping_address,
    o.shipping_method,
    o.payment_status
FROM public.order_items oi
JOIN public.orders o ON oi.order_id = o.id
JOIN public.profiles p ON o.user_id = p.id
JOIN public.products prod ON oi.product_id = prod.id;

-- ==========================================
-- 7. RLS POLICIES (Consolidated & Recursion-Free)
-- ==========================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.banners ENABLE ROW LEVEL SECURITY;

CREATE POLICY "profiles_universal_select" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "profiles_self_update" ON public.profiles FOR UPDATE USING (auth.uid() = id OR public.is_admin());

-- Products RLS
DROP POLICY IF EXISTS "products_universal_select" ON public.products;
DROP POLICY IF EXISTS "products_vendor_all" ON public.products;

CREATE POLICY "products_select_policy" ON public.products
FOR SELECT USING (is_active = true OR auth.uid() = vendor_id OR public.is_admin());

CREATE POLICY "products_insert_policy" ON public.products
FOR INSERT WITH CHECK (
    auth.uid() = vendor_id
    AND public.get_user_role(auth.uid()) = 'vendor'
    AND EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND status = 'active')
);

CREATE POLICY "products_update_policy" ON public.products
FOR UPDATE USING (auth.uid() = vendor_id OR public.is_admin());

CREATE POLICY "products_delete_policy" ON public.products
FOR DELETE USING (auth.uid() = vendor_id OR public.is_admin());

-- Banners RLS
CREATE POLICY "banners_universal_select" ON public.banners
FOR SELECT USING (status = 'approved' OR auth.uid() = vendor_id OR public.is_admin());

CREATE POLICY "banners_vendor_insert" ON public.banners
FOR INSERT WITH CHECK (auth.uid() = vendor_id AND public.get_user_role(auth.uid()) = 'vendor');

CREATE POLICY "banners_vendor_update" ON public.banners
FOR UPDATE USING (auth.uid() = vendor_id OR public.is_admin());

CREATE POLICY "banners_vendor_delete" ON public.banners
FOR DELETE USING (auth.uid() = vendor_id OR public.is_admin());

CREATE POLICY "orders_access_policy" ON public.orders FOR SELECT USING (auth.uid() = user_id OR (auth.uid() = vendor_id AND payment_status = 'paid') OR public.is_admin());
CREATE POLICY "orders_self_insert" ON public.orders FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "order_items_access_policy" ON public.order_items FOR SELECT USING (EXISTS (SELECT 1 FROM public.orders o WHERE o.id = order_id AND (o.user_id = auth.uid() OR (o.vendor_id = auth.uid() AND o.payment_status = 'paid') OR public.is_admin())));

CREATE POLICY "wallets_self_select" ON public.wallets FOR SELECT USING (auth.uid() = user_id OR public.is_admin());

-- ==========================================
-- 8. STORAGE & REALTIME
-- ==========================================

INSERT INTO storage.buckets (id, name, public) VALUES ('receipts', 'receipts', true), ('reports', 'reports', false), ('deployments', 'deployments', true), ('product-images', 'product-images', true) ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN CREATE PUBLICATION supabase_realtime; END IF;
    ALTER PUBLICATION supabase_realtime ADD TABLE public.orders, public.products, public.messages, public.notifications, public.system_logs;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

GRANT USAGE ON SCHEMA public TO anon, authenticated;
GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO authenticated;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO authenticated, service_role;
