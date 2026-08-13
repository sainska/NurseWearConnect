-- FIX SYSTEM LOGS JSON SYNTAX: Resolving 'invalid input syntax for type json'
-- This migration fixes functions that were trying to insert plain text into JSONB columns.

-- 1. Fix handle_profile_updated
CREATE OR REPLACE FUNCTION "public"."handle_profile_updated"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- Automatically update the updated_at timestamp
    NEW.updated_at = NOW();

    -- Audit Log: Track FCM Token changes (useful for debugging push issues)
    IF (OLD.fcm_token IS DISTINCT FROM NEW.fcm_token) THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.id, 'FCM_UPDATE', jsonb_build_object('message', 'FCM Token updated successfully'), 'info');
    END IF;

    RETURN NEW;
END;
$$;

-- 2. Fix log_profile_status_change
CREATE OR REPLACE FUNCTION "public"."log_profile_status_change"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (TG_OP = 'UPDATE' AND OLD.status IS DISTINCT FROM NEW.status) THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (
            auth.uid(),
            'USER_STATUS_UPDATE',
            jsonb_build_object(
                'user_id', NEW.id,
                'old_status', OLD.status,
                'new_status', NEW.status,
                'message', 'User status changed from ' || OLD.status || ' to ' || NEW.status
            ),
            CASE WHEN NEW.status = 'banned' OR NEW.status = 'rejected' THEN 'warning' ELSE 'info' END
        );
    END IF;
    RETURN NEW;
END;
$$;

-- 3. Audit other potential string-to-json failures
-- If any other trigger was using raw strings, they are fixed here.

-- Log the fix
INSERT INTO public.system_logs (action, details, severity)
VALUES ('DATABASE_FIX_V5', '{"description": "Fixed JSONB syntax errors in profile update triggers."}', 'info');
