-- Enhance banners table for vendor promotion and admin approval
-- Created: 2026-10-28

-- Add columns for vendor management and action links
ALTER TABLE public.banners ADD COLUMN IF NOT EXISTS vendor_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE;
ALTER TABLE public.banners ADD COLUMN IF NOT EXISTS status text DEFAULT 'pending'::text;
ALTER TABLE public.banners ADD COLUMN IF NOT EXISTS action_link text;
ALTER TABLE public.banners ADD COLUMN IF NOT EXISTS created_at timestamp with time zone DEFAULT now();
ALTER TABLE public.banners ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone DEFAULT now();

-- Add check constraint for status to ensure workflow integrity
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'banners_status_check') THEN
        ALTER TABLE public.banners ADD CONSTRAINT banners_status_check CHECK (status = ANY (ARRAY['pending'::text, 'approved'::text, 'rejected'::text]));
    END IF;
END $$;

-- Enable RLS to protect vendor data
ALTER TABLE public.banners ENABLE ROW LEVEL SECURITY;

-- Banners RLS Policies
DROP POLICY IF EXISTS "banners_universal_select" ON public.banners;
CREATE POLICY "banners_universal_select" ON public.banners
FOR SELECT USING (status = 'approved' OR auth.uid() = vendor_id OR public.is_admin());

DROP POLICY IF EXISTS "banners_vendor_insert" ON public.banners;
CREATE POLICY "banners_vendor_insert" ON public.banners
FOR INSERT WITH CHECK (auth.uid() = vendor_id AND public.get_user_role(auth.uid()) = 'vendor');

DROP POLICY IF EXISTS "banners_vendor_update" ON public.banners;
CREATE POLICY "banners_vendor_update" ON public.banners
FOR UPDATE USING (auth.uid() = vendor_id OR public.is_admin());

DROP POLICY IF EXISTS "banners_vendor_delete" ON public.banners;
CREATE POLICY "banners_vendor_delete" ON public.banners
FOR DELETE USING (auth.uid() = vendor_id OR public.is_admin());

-- Ensure updated_at trigger is active
DROP TRIGGER IF EXISTS tr_update_updated_at ON public.banners;
CREATE TRIGGER tr_update_updated_at BEFORE UPDATE ON public.banners FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
