-- FIX NOTIFICATIONS SCHEMA: Handling inconsistent column names (message vs body)
-- This migration ensures that notifications work regardless of whether 'message' or 'body' is provided.

-- 1. Relax constraints to prevent 400 errors during transition
ALTER TABLE public.notifications ALTER COLUMN message DROP NOT NULL;
ALTER TABLE public.notifications ALTER COLUMN body DROP NOT NULL;

-- 2. Add a trigger to automatically sync the columns
CREATE OR REPLACE FUNCTION public.sync_notification_content()
RETURNS TRIGGER AS $$
BEGIN
    -- If body is provided but message is null, sync body to message
    IF (NEW.body IS NOT NULL AND NEW.message IS NULL) THEN
        NEW.message := NEW.body;
    END IF;

    -- If message is provided but body is null, sync message to body
    IF (NEW.message IS NOT NULL AND NEW.body IS NULL) THEN
        NEW.body := NEW.message;
    END IF;

    -- Fallback for 'content' which some older triggers might use
    -- If both are still null, set a default
    IF (NEW.message IS NULL AND NEW.body IS NULL) THEN
        NEW.message := 'Notification received';
        NEW.body := 'Notification received';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_notification_content ON public.notifications;
CREATE TRIGGER tr_sync_notification_content
BEFORE INSERT OR UPDATE ON public.notifications
FOR EACH ROW EXECUTE FUNCTION public.sync_notification_content();

-- 3. Fix existing triggers that might be using non-existent columns (like 'content')
-- Searching for triggers that might fail
CREATE OR REPLACE FUNCTION "public"."fn_award_loyalty_points"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.status != 'delivered' AND NEW.status = 'delivered') THEN
        UPDATE public.profiles
        SET loyalty_points = loyalty_points + floor(NEW.total_amount * 0.01)::integer
        WHERE id = NEW.user_id;

        -- Fix: uses 'body' instead of 'content'
        INSERT INTO public.notifications (user_id, title, body, category)
        VALUES (
            NEW.user_id,
            'Loyalty Points Earned!',
            'You earned ' || floor(NEW.total_amount * 0.01)::integer || ' points from your order #' || substring(NEW.id::text, 1, 8),
            'LOYALTY'
        );
    END IF;
    RETURN NEW;
END;
$$;

-- Fix waitlist notification
CREATE OR REPLACE FUNCTION "public"."fn_notify_waitlist"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.stock_count = 0 AND NEW.stock_count > 0) THEN
        INSERT INTO public.notifications (user_id, title, body, category)
        SELECT user_id, 'Item Back in Stock!', 'The ' || NEW.name || ' is now available. Grab yours before it runs out!', 'RESTOCK'
        FROM public.product_waitlist
        WHERE product_id = NEW.id AND notified = false;

        UPDATE public.product_waitlist
        SET notified = true
        WHERE product_id = NEW.id AND notified = false;
    END IF;
    RETURN NEW;
END;
$$;

-- Log the fix
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SCHEMA_FIX_NOTIFICATIONS', '{"description": "Unified message/body columns and fixed broken triggers."}', 'info');
