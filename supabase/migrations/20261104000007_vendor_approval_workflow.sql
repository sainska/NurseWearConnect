-- Robust Vendor Approval Workflow Fix
-- Run: supabase db push

-- 1. Data Sanitization: Fix any inconsistent statuses before applying new constraint
UPDATE public.profiles
SET status = 'pending'
WHERE status IS NULL
   OR status = ''
   OR status NOT IN ('active', 'pending', 'suspended', 'rejected', 'deleted');

-- 2. Comprehensive Status Constraint Update
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_status_check;
ALTER TABLE public.profiles ADD CONSTRAINT profiles_status_check
CHECK (status = ANY (ARRAY['active', 'pending', 'suspended', 'rejected', 'deleted', 'pending_corrections']));

-- 3. Unified New User Trigger (Registration Metadata)
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

-- 4. Correction Workflow Function
CREATE OR REPLACE FUNCTION public.request_vendor_corrections(
    p_vendor_id UUID,
    p_notes TEXT,
    p_admin_id UUID
) RETURNS VOID AS $$
BEGIN
    UPDATE public.profiles
    SET
        status = 'pending_corrections',
        status_notes = p_notes,
        updated_at = now()
    WHERE id = p_vendor_id;

    -- Create notification for vendor
    INSERT INTO public.notifications (user_id, title, body, category, priority_level)
    VALUES (
        p_vendor_id,
        'Action Required: Application Corrections',
        'Your vendor application needs some updates. Feedback: ' || p_notes,
        'system',
        'high'
    );

    -- Log admin action
    INSERT INTO public.system_logs (user_id, action, details, severity)
    VALUES (p_admin_id, 'VENDOR_CORRECTION_REQUESTED', jsonb_build_object('vendor_id', p_vendor_id, 'notes', p_notes), 'info');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Permissions
GRANT ALL ON TABLE public.profiles TO postgres, service_role;
GRANT EXECUTE ON FUNCTION public.request_vendor_corrections(UUID, TEXT, UUID) TO service_role;
GRANT EXECUTE ON FUNCTION public.request_vendor_corrections(UUID, TEXT, UUID) TO authenticated;
