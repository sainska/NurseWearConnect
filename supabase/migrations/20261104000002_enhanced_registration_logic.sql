-- Enhanced Registration and Profile Logic
-- Run: supabase db push

-- 1. Update handle_new_user to capture all registration metadata
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
    NEW.raw_user_meta_data->>'business_description',
    NEW.raw_user_meta_data->>'business_license_url',
    CASE
      WHEN (NEW.raw_user_meta_data->>'business_license_url') IS NOT NULL THEN 'pending'
      ELSE NULL
    END,
    NEW.raw_user_meta_data->>'institution',
    (NEW.raw_user_meta_data->>'referred_by')::UUID,
    COALESCE(NEW.raw_user_meta_data->>'avatar_url', '')
  );

  -- Wallet is created via on_profile_created_setup trigger in public.profiles
  -- so we don't need to duplicate it here if that migration was applied.

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Ensure system_logs table exists for the audit trail
CREATE TABLE IF NOT EXISTS public.system_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID, -- Can be null for anonymous actions
    action TEXT NOT NULL,
    details JSONB DEFAULT '{}'::jsonb,
    severity TEXT DEFAULT 'info',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- 3. Add index on system_logs for better performance
CREATE INDEX IF NOT EXISTS idx_system_logs_action ON public.system_logs(action);
CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON public.system_logs(created_at);

-- 4. Function to verify email (Alternative to Supabase verify if needed)
-- This is mostly for deep link support via RPC if standard verification is bypassed
CREATE OR REPLACE FUNCTION public.confirm_user_email(p_user_id UUID)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    UPDATE auth.users SET email_confirmed_at = now() WHERE id = p_user_id;
    UPDATE public.profiles SET status = 'active' WHERE id = p_user_id AND role != 'vendor';
END;
$$;
