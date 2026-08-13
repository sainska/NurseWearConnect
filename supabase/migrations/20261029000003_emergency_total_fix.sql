-- EMERGENCY TOTAL FIX: Resolving all blockers for Order Completion
-- Target: 400 Bad Requests and 500 Recursion Errors

-- ==========================================
-- 1. SYSTEM SETTINGS FIX
-- ==========================================
-- Fix the broken edge function URL that was causing crashes in triggers
UPDATE public.system_settings
SET value = 'https://trpsejzasbfqlshrbbae.supabase.co/functions/v1'
WHERE key = 'edge_function_url';

-- ==========================================
-- 2. NOTIFICATION TRIGGER RESILIENCE
-- ==========================================
-- Wrap the FCM bridge in an exception handler so it NEVER blocks a transaction
CREATE OR REPLACE FUNCTION "public"."notify_fcm_bridge"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_fcm_token TEXT;
    v_payload JSONB;
    v_url TEXT;
    v_key TEXT;
BEGIN
    BEGIN
        -- Get the recipient's FCM token
        SELECT fcm_token INTO v_fcm_token FROM public.profiles WHERE id = NEW.user_id;

        IF v_fcm_token IS NOT NULL THEN
            -- Get credentials
            SELECT value INTO v_url FROM public.system_settings WHERE key = 'edge_function_url';
            SELECT value INTO v_key FROM public.system_settings WHERE key = 'service_role_key';

            IF v_url IS NOT NULL AND v_url NOT LIKE '%localhost%' THEN
                v_payload := jsonb_build_object(
                    'to', v_fcm_token,
                    'notification', jsonb_build_object(
                        'title', NEW.title,
                        'body', COALESCE(NEW.body, NEW.message)
                    ),
                    'data', jsonb_build_object(
                        'category', NEW.category,
                        'id', NEW.id
                    )
                );

                PERFORM net.http_post(
                    url := v_url || '/fcm-pusher',
                    headers := jsonb_build_object(
                        'Content-Type', 'application/json',
                        'Authorization', 'Bearer ' || v_key
                    ),
                    body := v_payload
                );
            END IF;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        -- LOG ERROR BUT DO NOT ROLLBACK
        RAISE WARNING 'FCM Bridge failed but allowing transaction to continue: %', SQLERRM;
    END;
    RETURN NEW;
END;
$$;

-- ==========================================
-- 3. FIX VENDOR NOTIFICATION CONTENT
-- ==========================================
-- Ensure both body and message are populated to avoid any constraint issues
CREATE OR REPLACE FUNCTION "public"."on_new_order_notify_vendor"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_msg TEXT;
BEGIN
    IF (NEW.vendor_id IS NOT NULL) THEN
        v_msg := 'You have a new order #' || substring(NEW.id::text, 1, 8) || ' for KES ' || NEW.total_amount;
        INSERT INTO public.notifications (user_id, title, message, body, category, priority_level)
        VALUES (
            NEW.vendor_id,
            'New Order Received',
            v_msg,
            v_msg,
            'vendor_order',
            'high'
        );
    END IF;
    RETURN NEW;
END;
$$;

-- ==========================================
-- 4. CLEANUP NOTIFICATIONS SCHEMA
-- ==========================================
-- Title should also be nullable just in case, and we set a default
ALTER TABLE public.notifications ALTER COLUMN title DROP NOT NULL;
ALTER TABLE public.notifications ALTER COLUMN title SET DEFAULT 'New Notification';

-- ==========================================
-- 5. FINAL POLICY SYNC
-- ==========================================
-- Ensure orders can be inserted by authenticated users
DROP POLICY IF EXISTS "orders_insert" ON public.orders;
CREATE POLICY "orders_insert" ON public.orders FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Ensure order items can be inserted
DROP POLICY IF EXISTS "order_items_insert" ON public.order_items;
CREATE POLICY "order_items_insert" ON public.order_items FOR INSERT WITH CHECK (
    EXISTS (SELECT 1 FROM public.orders WHERE id = order_items.order_id AND user_id = auth.uid())
);

-- ==========================================
-- 6. LOGGING
-- ==========================================
INSERT INTO public.system_logs (action, details, severity)
VALUES ('EMERGENCY_FIX_V4', '{"description": "Fixed broken FCM trigger, updated edge URL, and ensured notification content parity."}', 'info');
