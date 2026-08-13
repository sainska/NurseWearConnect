-- Enhanced Profiles and Loyalty System

-- 1. Ensure Wallets Table exists and is linked to profiles
-- Using a DO block to ensure balance and currency columns exist if table already exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wallets') THEN
        CREATE TABLE public.wallets (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
            balance DECIMAL(12, 2) DEFAULT 0.00,
            currency VARCHAR(3) DEFAULT 'KES',
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
            UNIQUE(user_id)
        );
    ELSE
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='wallets' AND column_name='balance') THEN
            ALTER TABLE public.wallets ADD COLUMN balance DECIMAL(12, 2) DEFAULT 0.00;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='wallets' AND column_name='currency') THEN
            ALTER TABLE public.wallets ADD COLUMN currency VARCHAR(3) DEFAULT 'KES';
        END IF;
    END IF;
END $$;

-- 2. Loyalty Tiers (Drop and recreate to ensure correct schema for this feature)
DROP TABLE IF EXISTS public.loyalty_tiers CASCADE;
CREATE TABLE public.loyalty_tiers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(20) UNIQUE NOT NULL, -- bronze, silver, gold, platinum
    min_points INTEGER NOT NULL,
    discount_percentage DECIMAL(5, 2) NOT NULL,
    benefits TEXT[]
);

INSERT INTO public.loyalty_tiers (name, min_points, discount_percentage, benefits)
VALUES
('bronze', 0, 0.00, ARRAY['Standard Support']),
('silver', 1000, 5.00, ARRAY['Priority Support', 'Early Access to Sales']),
('gold', 5000, 10.00, ARRAY['Free Express Shipping', 'Exclusive Vouchers', 'Dedicated Support']),
('platinum', 15000, 15.00, ARRAY['Personal Stylist', 'Free Annual Scrub', 'VIP Event Access'])
ON CONFLICT (name) DO UPDATE SET
    min_points = EXCLUDED.min_points,
    discount_percentage = EXCLUDED.discount_percentage,
    benefits = EXCLUDED.benefits;

-- 3. Loyalty History
CREATE TABLE IF NOT EXISTS public.loyalty_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    points_change INTEGER NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- 4. Add new columns to profiles if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='loyalty_points') THEN
        ALTER TABLE public.profiles ADD COLUMN loyalty_points INTEGER DEFAULT 0;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='loyalty_tier') THEN
        ALTER TABLE public.profiles ADD COLUMN loyalty_tier VARCHAR(20) DEFAULT 'bronze';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='measurements') THEN
        ALTER TABLE public.profiles ADD COLUMN measurements JSONB DEFAULT '{"bust": "0\"", "waist": "0\"", "hips": "0\""}'::jsonb;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='biometric_enabled') THEN
        ALTER TABLE public.profiles ADD COLUMN biometric_enabled BOOLEAN DEFAULT false;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='notifications_enabled') THEN
        ALTER TABLE public.profiles ADD COLUMN notifications_enabled BOOLEAN DEFAULT true;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='business_name') THEN
        ALTER TABLE public.profiles ADD COLUMN business_name TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='location') THEN
        ALTER TABLE public.profiles ADD COLUMN location TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='bank_code') THEN
        ALTER TABLE public.profiles ADD COLUMN bank_code TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='profiles' AND column_name='bank_account_number') THEN
        ALTER TABLE public.profiles ADD COLUMN bank_account_number TEXT;
    END IF;
END $$;

-- 5. Trigger for automatic Wallet Creation
CREATE OR REPLACE FUNCTION public.handle_new_user_setup()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.wallets (user_id) VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS on_profile_created_setup ON public.profiles;
CREATE TRIGGER on_profile_created_setup
    AFTER INSERT ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user_setup();

-- 6. Trigger to update loyalty tier based on points
CREATE OR REPLACE FUNCTION public.update_loyalty_tier()
RETURNS TRIGGER AS $$
DECLARE
    new_tier TEXT;
BEGIN
    SELECT name INTO new_tier
    FROM public.loyalty_tiers
    WHERE min_points <= NEW.loyalty_points
    ORDER BY min_points DESC
    LIMIT 1;

    NEW.loyalty_tier := new_tier;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS on_loyalty_points_change ON public.profiles;
CREATE TRIGGER on_loyalty_points_change
    BEFORE UPDATE OF loyalty_points ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.update_loyalty_tier();

-- 7. Realtime setup (Only if not already in publication)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'profiles') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.profiles;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'wallets') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.wallets;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'notifications') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.notifications;
    END IF;
END $$;

-- 8. RLS Policies (Security)
ALTER TABLE public.wallets ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view their own wallet" ON public.wallets;
CREATE POLICY "Users can view their own wallet" ON public.wallets FOR SELECT USING (auth.uid() = user_id);

ALTER TABLE public.loyalty_history ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view their own loyalty history" ON public.loyalty_history;
CREATE POLICY "Users can view their own loyalty history" ON public.loyalty_history FOR SELECT USING (auth.uid() = user_id);

-- Ensure profiles are updated for existing users who might not have wallets
INSERT INTO public.wallets (user_id)
SELECT id FROM public.profiles
ON CONFLICT (user_id) DO NOTHING;
