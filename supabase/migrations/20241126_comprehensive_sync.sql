-- Comprehensive Sync for Profiles, Orders, and Loyalty

-- 1. Ensure Profiles table has all UI-required columns
DO $$
BEGIN
    -- Basic Info
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='avatar_url') THEN
        ALTER TABLE public.profiles ADD COLUMN avatar_url TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='address') THEN
        ALTER TABLE public.profiles ADD COLUMN address TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='institution') THEN
        ALTER TABLE public.profiles ADD COLUMN institution TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='bio') THEN
        ALTER TABLE public.profiles ADD COLUMN bio TEXT;
    END IF;

    -- Vendor Specifics
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='commission_rate') THEN
        ALTER TABLE public.profiles ADD COLUMN commission_rate DECIMAL(5, 2) DEFAULT 10.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='monthly_sales_target') THEN
        ALTER TABLE public.profiles ADD COLUMN monthly_sales_target DECIMAL(12, 2) DEFAULT 100000.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='rating') THEN
        ALTER TABLE public.profiles ADD COLUMN rating DECIMAL(3, 2) DEFAULT 5.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='status_notes') THEN
        ALTER TABLE public.profiles ADD COLUMN status_notes TEXT;
    END IF;
END $$;

-- 2. Ensure Orders and Order Items have consistent schema
DO $$
BEGIN
    -- Orders
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='orders' AND column_name='digital_receipt_enabled') THEN
        ALTER TABLE public.orders ADD COLUMN digital_receipt_enabled BOOLEAN DEFAULT true;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='orders' AND column_name='is_fitting_service') THEN
        ALTER TABLE public.orders ADD COLUMN is_fitting_service BOOLEAN DEFAULT false;
    END IF;

    -- Order Items
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='order_items' AND column_name='embroidery_name') THEN
        ALTER TABLE public.order_items ADD COLUMN embroidery_name TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='order_items' AND column_name='has_embroidery') THEN
        ALTER TABLE public.order_items ADD COLUMN has_embroidery BOOLEAN DEFAULT false;
    END IF;
END $$;

-- 3. Storage Setup for Avatars and Receipts
-- Note: buckets and policies often require 'supabase_admin' or careful auth.uid() checks.
INSERT INTO storage.buckets (id, name, public)
VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public)
VALUES ('receipts', 'receipts', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public)
VALUES ('product-images', 'product-images', true)
ON CONFLICT (id) DO NOTHING;

-- Storage Policies (Simplified for development, should be tightened for prod)
DROP POLICY IF EXISTS "Public Access" ON storage.objects;
CREATE POLICY "Public Access" ON storage.objects FOR SELECT USING (bucket_id IN ('avatars', 'receipts', 'product-images'));

DROP POLICY IF EXISTS "Authenticated Upload" ON storage.objects;
CREATE POLICY "Authenticated Upload" ON storage.objects FOR INSERT WITH CHECK (auth.role() = 'authenticated');

-- 4. RPC for Vendor Dashboard Stats (High level stats for the home screen)
CREATE OR REPLACE FUNCTION public.get_vendor_dashboard_stats(p_vendor_id UUID, p_days INTEGER DEFAULT 30)
RETURNS JSONB AS $$
DECLARE
    v_revenue DECIMAL(12, 2);
    v_order_count INTEGER;
    v_low_stock_count INTEGER;
    v_result JSONB;
BEGIN
    -- Net Earnings (After 10% Platform Fee - this should ideally use the profile's commission_rate)
    SELECT COALESCE(SUM(final_amount), 0) INTO v_revenue
    FROM public.orders
    WHERE vendor_id = p_vendor_id AND status = 'delivered' AND created_at >= NOW() - (p_days || ' days')::INTERVAL;

    -- Total Sales
    SELECT COUNT(*) INTO v_order_count
    FROM public.orders
    WHERE vendor_id = p_vendor_id AND created_at >= NOW() - (p_days || ' days')::INTERVAL;

    -- Low Stock Items (Threshold 5)
    SELECT COUNT(*) INTO v_low_stock_count
    FROM public.products
    WHERE vendor_id = p_vendor_id AND is_active = true AND stock_count > 0 AND stock_count <= 5;

    v_result := jsonb_build_object(
        'revenue', v_revenue,
        'order_count', v_order_count,
        'low_stock_count', v_low_stock_count,
        'best_sellers', (
            SELECT jsonb_agg(t) FROM (
                SELECT p.id as product_id, p.name as product_name, p.images[1] as product_image, COUNT(oi.id) as sales_count
                FROM public.products p
                JOIN public.order_items oi ON p.id = oi.product_id
                WHERE p.vendor_id = p_vendor_id
                GROUP BY p.id, p.name, p.images
                ORDER BY sales_count DESC
                LIMIT 5
            ) t
        ),
        'stock_health', jsonb_build_object('low_stock_count', v_low_stock_count)
    );

    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

-- 5. Realtime for all core tables
DO $$
DECLARE
    t TEXT;
BEGIN
    FOR t IN SELECT unnest(ARRAY['profiles', 'wallets', 'notifications', 'orders', 'order_items', 'order_status_history', 'products'])
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = t) THEN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
        END IF;
    END LOOP;
END $$;
