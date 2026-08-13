-- Fix RLS for system_logs to allow logging from the app
-- Run: supabase db push

-- 1. Allow all authenticated users to insert logs
DROP POLICY IF EXISTS "system_logs_insert" ON public.system_logs;
CREATE POLICY "system_logs_insert" ON public.system_logs
FOR INSERT WITH CHECK (true);

-- 2. Allow users to view their own logs (already done in previous migration, but reinforcing)
DROP POLICY IF EXISTS "system_logs_admin_only" ON public.system_logs;
CREATE POLICY "system_logs_select" ON public.system_logs
FOR SELECT USING (public.is_admin() OR user_id = auth.uid());

-- 3. Ensure the profile trigger captures institution and phone during registration
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (
    id,
    full_name,
    email,
    phone_number,
    role,
    status,
    business_name,
    location,
    bio,
    business_license_url,
    document_status,
    institution,
    referred_by,
    avatar_url
  )
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
    NEW.email,
    NEW.raw_user_meta_data->>'phone_number',
    COALESCE(NEW.raw_user_meta_data->>'role', 'student'),
    CASE
      WHEN (NEW.raw_user_meta_data->>'role') = 'vendor' THEN 'pending'
      ELSE 'active'
    END,
    NEW.raw_user_meta_data->>'business_name',
    NEW.raw_user_meta_data->>'location',
    COALESCE(NEW.raw_user_meta_data->>'business_description', NEW.raw_user_meta_data->>'bio', ''),
    NEW.raw_user_meta_data->>'business_license_url',
    CASE
      WHEN (NEW.raw_user_meta_data->>'business_license_url') IS NOT NULL THEN 'pending'
      ELSE NULL
    END,
    NEW.raw_user_meta_data->>'institution',
    CASE
        WHEN NEW.raw_user_meta_data->>'referred_by' IS NOT NULL AND NEW.raw_user_meta_data->>'referred_by' != ''
        THEN (NEW.raw_user_meta_data->>'referred_by')::UUID
        ELSE NULL
    END,
    COALESCE(NEW.raw_user_meta_data->>'avatar_url', '')
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
