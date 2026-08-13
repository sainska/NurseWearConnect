-- Webhook to trigger password reset email via Edge Function
-- Run: supabase db push

-- 1. Ensure the Edge Function can be called from DB
-- Note: Replace 'http://localhost:54321' with your production URL if needed,
-- but Supabase usually handles 'supabase_functions' alias or internal routing.

CREATE OR REPLACE FUNCTION public.fn_trigger_password_reset_email()
RETURNS TRIGGER AS $$
DECLARE
  v_apikey TEXT := 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRycHNlanphc2JmcWxzaHJiYmFlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU4NDg4NTksImV4cCI6MjA5MTQyNDg1OX0.oD0zM5VDLXxt1onGsqCYo0HGh51bskWZjCFH5boXxSw';
BEGIN
  -- Call the Edge Function
  -- We pass the new record as the body
  PERFORM net.http_post(
    url := 'https://trpsejzasbfqlshrbbae.functions.supabase.co/send-password-reset',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'apikey', v_apikey,
      'Authorization', 'Bearer ' || v_apikey
    ),
    body := jsonb_build_object('record', row_to_json(NEW))
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Create the trigger
DROP TRIGGER IF EXISTS tr_on_password_reset_inserted ON public.password_resets;
CREATE TRIGGER tr_on_password_reset_inserted
  AFTER INSERT ON public.password_resets
  FOR EACH ROW
  EXECUTE FUNCTION public.fn_trigger_password_reset_email();

-- 3. Grant permissions to use http extension if not already
-- (Assumes 'pg_net' extension is enabled in Supabase)
GRANT USAGE ON SCHEMA net TO postgres, service_role;
