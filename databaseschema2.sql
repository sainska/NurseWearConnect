


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE EXTENSION IF NOT EXISTS "pg_net" WITH SCHEMA "public";






CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE OR REPLACE FUNCTION "public"."admin_delete_user"("target_user_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- Authorization check
    IF (SELECT public.get_user_role(auth.uid())) != 'admin' THEN
        RAISE EXCEPTION 'Unauthorized: Administrative privileges required.';
    END IF;

    -- Delete from auth.users (cascades to public.profiles and related tables)
    DELETE FROM auth.users WHERE id = target_user_id;

    -- Log the action
    INSERT INTO public.system_logs (user_id, action, details, severity)
    VALUES (auth.uid(), 'DELETE_USER', jsonb_build_object('target_id', target_user_id), 'warning');
END;
$$;


ALTER FUNCTION "public"."admin_delete_user"("target_user_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."admin_force_update_order"("p_order_id" "uuid", "p_status" "text", "p_payment_status" "text" DEFAULT NULL::"text", "p_admin_notes" "text" DEFAULT NULL::"text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- Only allow if the requester is an admin
    IF public.get_user_role(auth.uid()) != 'admin' THEN
        RAISE EXCEPTION 'Access denied. Admin only.';
    END IF;

    UPDATE public.orders
    SET
        status = p_status,
        payment_status = COALESCE(p_payment_status, payment_status),
        updated_at = now()
    WHERE id = p_order_id;

    -- Log to history
    INSERT INTO public.order_status_history (order_id, status, notes, changed_by)
    VALUES (p_order_id, p_status, COALESCE(p_admin_notes, 'Force updated by Admin'), auth.uid());
END;
$$;


ALTER FUNCTION "public"."admin_force_update_order"("p_order_id" "uuid", "p_status" "text", "p_payment_status" "text", "p_admin_notes" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."audit_log_trigger"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO public.system_logs (user_id, action, details, severity)
    VALUES (
        auth.uid(),
        TG_TABLE_NAME || '_' || TG_OP,
        jsonb_build_object(
            'old', CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE to_jsonb(OLD) END,
            'new', CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE to_jsonb(NEW) END
        ),
        'info'
    );
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."audit_log_trigger"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."award_loyalty_points"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.status != 'delivered' AND NEW.status = 'delivered') THEN
        INSERT INTO public.loyalty_points (user_id, points, action_type, order_id)
        VALUES (NEW.user_id, floor(NEW.total_amount / 100), 'purchase', NEW.id);

        -- Send notification
        INSERT INTO public.notifications (user_id, title, body, category)
        VALUES (NEW.user_id, 'Points Earned!', 'You earned ' || floor(NEW.total_amount / 100) || ' points from your last order.', 'system');
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."award_loyalty_points"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."broadcast_notification"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
  -- We use Supabase Realtime for instant UI updates.
  -- The Edge Function (fcm-pusher) will be triggered via a Webhook on this table.
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."broadcast_notification"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."bulk_update_notifications"("notification_ids" "uuid"[], "new_is_read" boolean DEFAULT NULL::boolean, "new_is_archived" boolean DEFAULT NULL::boolean) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.notifications
    SET
        is_read = COALESCE(new_is_read, is_read),
        is_archived = COALESCE(new_is_archived, is_archived)
    WHERE id = ANY(notification_ids) AND user_id = auth.uid();
END;
$$;


ALTER FUNCTION "public"."bulk_update_notifications"("notification_ids" "uuid"[], "new_is_read" boolean, "new_is_archived" boolean) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."calculate_order_total"("p_order_id" "uuid") RETURNS numeric
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_total numeric;
BEGIN
    SELECT
        (SUM(unit_price * quantity) +
        CASE WHEN o.shipping_method = 'Express' THEN 500 ELSE 250 END -
        COALESCE(o.discount_amount, 0))
    INTO v_total
    FROM public.order_items oi
    JOIN public.orders o ON oi.order_id = o.id
    WHERE oi.order_id = p_order_id
    GROUP BY o.id, o.shipping_method, o.discount_amount;

    RETURN v_total;
END;
$$;


ALTER FUNCTION "public"."calculate_order_total"("p_order_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."can_access_order"("p_order_id" "uuid") RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.orders
        WHERE id = p_order_id
        AND (
            user_id = auth.uid() OR
            vendor_id = auth.uid() OR
            EXISTS (
                SELECT 1 FROM public.order_items
                WHERE order_id = p_order_id
                AND vendor_id = auth.uid()
            ) OR
            public.get_user_role(auth.uid()) = 'admin'
        )
    );
END;
$$;


ALTER FUNCTION "public"."can_access_order"("p_order_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."check_marketing_expirations"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- Deactivate expired coupons
    UPDATE public.coupons
    SET active = false
    WHERE active = true
      AND (
        (end_date IS NOT NULL AND end_date < NOW()) OR
        (usage_limit IS NOT NULL AND usage_count >= usage_limit)
      );

    -- Deactivate expired banners
    -- Assuming banners might also have an end_date in the future,
    -- but for now we manage them via status.
    UPDATE public.banners
    SET active = false
    WHERE active = true AND status = 'approved'
      AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'banners' AND column_name = 'end_date')
      -- Placeholder for if we add end_date to banners later
      ;
END;
$$;


ALTER FUNCTION "public"."check_marketing_expirations"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."check_return_eligibility"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- Check if order exists, is delivered, and is within 7 days of the last update (delivery date)
    IF NOT EXISTS (
        SELECT 1 FROM public.orders
        WHERE id = NEW.order_id
        AND status = 'delivered'
        AND (now() - updated_at) <= interval '7 days'
    ) THEN
        RAISE EXCEPTION 'This order is not eligible for return. Either it was not delivered or the 7-day return window has passed.';
    END IF;

    -- Prevent duplicate return requests for the same order
    IF EXISTS (
        SELECT 1 FROM public.return_requests
        WHERE order_id = NEW.order_id
        AND status != 'rejected'
    ) THEN
        RAISE EXCEPTION 'A return request for this order is already in progress.';
    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."check_return_eligibility"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."check_review_eligibility"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.orders o
    JOIN public.order_items oi ON o.id = oi.order_id
    WHERE o.user_id = NEW.user_id
    AND oi.product_id = NEW.product_id
    AND o.status = 'delivered'
  ) THEN
    RAISE EXCEPTION 'You can only review products that have been delivered to you.';
  END IF;
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."check_review_eligibility"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."check_stock_levels"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (NEW.stock_count < 5 AND (OLD.stock_count >= 5 OR OLD IS NULL)) THEN
        INSERT INTO public.notifications (user_id, title, body, category, priority_level)
        VALUES (
            NEW.vendor_id,
            'Low Stock Alert',
            'Your product "' || NEW.name || '" is running low (' || NEW.stock_count || ' left).',
            'inventory',
            'high'
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."check_stock_levels"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."create_subscription"("p_product_id" "uuid", "p_quantity" integer, "p_size" "text", "p_color" "text", "p_frequency_days" integer, "p_address_id" "uuid") RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_sub_id UUID;
BEGIN
    INSERT INTO public.subscriptions (user_id, product_id, quantity, size, color, frequency_days, shipping_address_id)
    VALUES (auth.uid(), p_product_id, p_quantity, p_size, p_color, p_frequency_days, p_address_id)
    RETURNING id INTO v_sub_id;

    RETURN v_sub_id;
END;
$$;


ALTER FUNCTION "public"."create_subscription"("p_product_id" "uuid", "p_quantity" integer, "p_size" "text", "p_color" "text", "p_frequency_days" integer, "p_address_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."finalize_successful_payment"("p_order_id" "uuid", "p_transaction_id" "text", "p_amount" numeric, "p_method" "text", "p_response" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- 1. Insert/Update Payment
    INSERT INTO public.payments (order_id, transaction_id, amount, payment_method, status, provider_response)
    VALUES (p_order_id, p_transaction_id, p_amount, p_method, 'completed', p_response)
    ON CONFLICT (transaction_id) DO UPDATE SET
        status = 'completed',
        provider_response = p_response,
        updated_at = now();

    -- 2. Update Order
    UPDATE public.orders
    SET
        payment_status = 'paid',
        status = 'processing',
        payment_id = p_transaction_id,
        payment_method = p_method,
        updated_at = now()
    WHERE id = p_order_id;
END;
$$;


ALTER FUNCTION "public"."finalize_successful_payment"("p_order_id" "uuid", "p_transaction_id" "text", "p_amount" numeric, "p_method" "text", "p_response" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_audit_trigger"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO public.audit_logs (user_id, action, details, severity)
    VALUES (
        auth.uid(),
        TG_OP || ' ' || TG_TABLE_NAME,
        jsonb_build_object(
            'table', TG_TABLE_NAME,
            'record_id', CASE WHEN TG_OP = 'DELETE' THEN OLD.id ELSE NEW.id END,
            'old', CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE to_jsonb(OLD) END,
            'new', CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE to_jsonb(NEW) END
        ),
        'info'
    );
    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."fn_audit_trigger"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_award_loyalty_points"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.status != 'delivered' AND NEW.status = 'delivered') THEN
        UPDATE public.profiles
        SET loyalty_points = loyalty_points + floor(NEW.total_amount * 0.01)::integer
        WHERE id = NEW.user_id;

        -- Also create a notification
        INSERT INTO public.notifications (user_id, title, content, type)
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


ALTER FUNCTION "public"."fn_award_loyalty_points"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_award_referral_bonus"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_referrer_id UUID;
    v_order_count INTEGER;
BEGIN
    -- Check if this is the user's first delivered order
    SELECT count(*) INTO v_order_count
    FROM public.orders
    WHERE user_id = NEW.user_id AND status = 'delivered';

    IF (OLD.status != 'delivered' AND NEW.status = 'delivered' AND v_order_count = 1) THEN
        -- Get the referrer
        SELECT referred_by_id INTO v_referrer_id FROM public.profiles WHERE id = NEW.user_id;

        IF v_referrer_id IS NOT NULL THEN
            -- Award KSh 500 to referrer's wallet
            UPDATE public.wallets
            SET balance = balance + 500, updated_at = now()
            WHERE user_id = v_referrer_id;

            INSERT INTO public.wallet_transactions (wallet_id, amount, type, reference_type, reference_id, description)
            SELECT id, 500, 'credit', 'bonus', NEW.id, 'Referral bonus for ' || NEW.user_id::text
            FROM public.wallets WHERE user_id = v_referrer_id;

            INSERT INTO public.notifications (user_id, title, content, type)
            VALUES (
                v_referrer_id,
                'Referral Bonus Awarded!',
                'You received KSh 500 because someone you referred completed their first order!',
                'REWARD'
            );
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_award_referral_bonus"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_calculate_item_delivery_fee"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_base_fee NUMERIC;
    v_existing_items_count INTEGER;
BEGIN
    -- Get vendor shipping settings
    SELECT base_shipping_fee INTO v_base_fee
    FROM public.profiles
    WHERE id = NEW.vendor_id;

    -- Check if this is the first item from this vendor in this order
    SELECT COUNT(*) INTO v_existing_items_count
    FROM public.order_items
    WHERE order_id = NEW.order_id AND vendor_id = NEW.vendor_id;

    IF v_existing_items_count = 0 THEN
        NEW.delivery_fee := COALESCE(v_base_fee, 150);
    ELSE
        NEW.delivery_fee := 0;
    END IF;

    -- Initialize fulfillment_data if null
    IF NEW.fulfillment_data IS NULL THEN
        NEW.fulfillment_data := '{}'::JSONB;
    END IF;

    NEW.fulfillment_data := NEW.fulfillment_data || jsonb_build_object(
        'shipping_calc_ver', '1.0',
        'is_primary_shipping_item', (v_existing_items_count = 0)
    );

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_calculate_item_delivery_fee"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_calculate_vendor_earnings"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_commission_rate NUMERIC;
    v_commission NUMERIC;
    v_net NUMERIC;
BEGIN
    IF (OLD.status != 'delivered' AND NEW.status = 'delivered' AND NEW.vendor_id IS NOT NULL) THEN
        -- Get vendor commission rate
        SELECT commission_rate INTO v_commission_rate FROM public.profiles WHERE id = NEW.vendor_id;
        v_commission_rate := COALESCE(v_commission_rate, 10.0);

        v_commission := NEW.total_amount * (v_commission_rate / 100.0);
        v_net := NEW.total_amount - v_commission;

        INSERT INTO public.vendor_payouts (vendor_id, order_id, gross_amount, commission_amount, net_amount)
        VALUES (NEW.vendor_id, NEW.id, NEW.total_amount, v_commission, v_net);
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_calculate_vendor_earnings"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_check_wallet_balance"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_user_balance NUMERIC;
BEGIN
    IF NEW.payment_method = 'wallet' THEN
        SELECT wallet_balance INTO v_user_balance FROM public.profiles WHERE id = NEW.user_id;
        IF v_user_balance < NEW.total_amount THEN
            RAISE EXCEPTION 'Insufficient wallet balance. Please top up.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_check_wallet_balance"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_ensure_default_after_delete"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF OLD.is_default = true THEN
        -- Make the most recently created address the new default
        UPDATE public.addresses
        SET is_default = true
        WHERE id = (
            SELECT id FROM public.addresses
            WHERE user_id = OLD.user_id
            ORDER BY created_at DESC
            LIMIT 1
        );
    END IF;
    RETURN OLD;
END;
$$;


ALTER FUNCTION "public"."fn_ensure_default_after_delete"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_generate_referral_code"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    IF NEW.referral_code IS NULL THEN
        NEW.referral_code := 'NWC-' || substring(md5(random()::text), 1, 6);
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_generate_referral_code"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_handle_item_stock_on_cancel"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (NEW.status = 'cancelled' AND OLD.status IS DISTINCT FROM 'cancelled') THEN
        UPDATE public.products
        SET stock_count = stock_count + NEW.quantity,
            in_stock = true
        WHERE id = NEW.product_id;

        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (
            auth.uid(),
            'STOCK_RESTORED',
            jsonb_build_object('product_id', NEW.product_id, 'quantity', NEW.quantity, 'reason', 'Item Cancelled'),
            'info'
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_handle_item_stock_on_cancel"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_manage_default_address"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- If the new/updated address is set as default
    IF NEW.is_default = true THEN
        -- Set all other addresses for this user to NOT default
        UPDATE public.addresses
        SET is_default = false
        WHERE user_id = NEW.user_id AND id <> NEW.id;
    END IF;

    -- If there's only one address left and it's not default, make it default
    IF NOT EXISTS (SELECT 1 FROM public.addresses WHERE user_id = NEW.user_id AND is_default = true) THEN
        NEW.is_default = true;
    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_manage_default_address"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_notify_restock"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.stock_count <= 0 AND NEW.stock_count > 0) THEN
        -- Insert notifications for all users in waitlist
        INSERT INTO public.notifications (user_id, title, message, type)
        SELECT
            user_id,
            'Back in Stock!',
            'The item "' || NEW.name || '" is now available. Grab yours before it runs out again!',
            'inventory'
        FROM public.product_waitlist
        WHERE product_id = NEW.id AND notified = false;

        -- Mark as notified
        UPDATE public.product_waitlist
        SET notified = true
        WHERE product_id = NEW.id AND notified = false;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_notify_restock"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_notify_waitlist"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.stock_count = 0 AND NEW.stock_count > 0) THEN
        -- Queue notifications for all users in waitlist
        INSERT INTO public.notifications (user_id, title, content, type)
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


ALTER FUNCTION "public"."fn_notify_waitlist"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_process_loyalty_award"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_user_id UUID;
    v_points_to_award INTEGER;
BEGIN
    -- Only award when item is DELIVERED
    IF NEW.status = 'delivered' AND (OLD.status IS DISTINCT FROM 'delivered') THEN
        -- Get the order's user_id
        SELECT user_id INTO v_user_id FROM public.orders WHERE id = NEW.order_id;

        -- Calculate 1 point per 100 KSh (approx 1% of item value)
        v_points_to_award := FLOOR((NEW.quantity * NEW.unit_price) / 100);

        IF v_points_to_award > 0 THEN
            -- Update profile balance
            UPDATE public.profiles
            SET loyalty_points = COALESCE(loyalty_points, 0) + v_points_to_award
            WHERE id = v_user_id;

            -- Insert into history table
            INSERT INTO public.loyalty_points (user_id, points, action_type, order_id)
            VALUES (v_user_id, v_points_to_award, 'purchase', NEW.order_id);

            -- Trigger notification
            INSERT INTO public.notifications (user_id, title, body, category)
            VALUES (
                v_user_id,
                'Points Earned!',
                'You earned ' || v_points_to_award || ' points from your purchase of ' || (SELECT name FROM public.products WHERE id = NEW.product_id),
                'system'
            );
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_process_loyalty_award"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_process_referral"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_referrer_id UUID;
BEGIN
    -- Check if a referral code was used (passed in metadata during sign up)
    -- This trigger should run on profiles table after handle_new_user.
    -- We assume 'referred_by_code' is stored in raw_user_meta_data for now.
    -- OR we check a column if handle_new_user already mapped it.

    -- Simplified: Award if referred_by column is set (requires adding the column)
    -- For this implementation, let's look for the code in user metadata.
    -- We need to find the user who OWNS the referral code.

    IF (NEW.role = 'student' OR NEW.role = 'nurse') THEN
        -- This logic is better triggered by an explicit action or handled in profile creation.
        -- Let's assume we have a column referred_by_id.
        NULL; -- Placeholder for complex referral matching
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_process_referral"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_process_vendor_payout_on_delivery"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_commission_rate NUMERIC;
    v_net_amount NUMERIC;
    v_order_currency TEXT;
BEGIN
    -- Only trigger if status changed to 'delivered'
    IF (NEW.status = 'delivered' AND OLD.status IS DISTINCT FROM 'delivered') THEN

        -- Get vendor's commission rate
        SELECT commission_rate INTO v_commission_rate
        FROM public.profiles
        WHERE id = NEW.vendor_id;

        -- Get order currency
        SELECT currency INTO v_order_currency
        FROM public.orders
        WHERE id = NEW.order_id;

        -- Calculate net share (Total - Commission)
        v_net_amount := (NEW.quantity * NEW.unit_price) * (1 - (COALESCE(v_commission_rate, 10.0) / 100.0));

        -- Credit Vendor Wallet
        UPDATE public.wallets
        SET balance = balance + v_net_amount, updated_at = now()
        WHERE user_id = NEW.vendor_id;

        -- Log Transaction
        INSERT INTO public.wallet_transactions (
            wallet_id, amount, type, reference_type, reference_id, description
        )
        SELECT
            id, v_net_amount, 'credit', 'payout', NEW.id,
            'Payout for item in order #' || substring(NEW.order_id::text, 1, 8)
        FROM public.wallets WHERE user_id = NEW.vendor_id;

        -- Log System Action
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (
            NEW.vendor_id,
            'VENDOR_PAYOUT',
            jsonb_build_object(
                'order_item_id', NEW.id,
                'order_id', NEW.order_id,
                'amount', v_net_amount,
                'commission_rate', v_commission_rate
            ),
            'info'
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_process_vendor_payout_on_delivery"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_queue_search_sync"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO public.search_index_queue (entity_type, entity_id, action)
    VALUES (TG_TABLE_NAME, COALESCE(NEW.id, OLD.id), CASE WHEN TG_OP = 'DELETE' THEN 'delete' ELSE 'index' END);
    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."fn_queue_search_sync"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_restrict_order_item_edits"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    -- Only check for vendors
    IF public.get_user_role(auth.uid()) = 'vendor' THEN
        -- 1. Prevent price/quantity changes
        IF (OLD.unit_price IS DISTINCT FROM NEW.unit_price) OR
           (OLD.quantity IS DISTINCT FROM NEW.quantity) OR
           (OLD.product_id IS DISTINCT FROM NEW.product_id) THEN
             RAISE EXCEPTION 'Vendors cannot modify item price, quantity, or product details after an order is placed.';
        END IF;

        -- 2. Prevent invalid status reversals
        IF (OLD.status = 'delivered' AND NEW.status != 'delivered') THEN
            RAISE EXCEPTION 'Items marked as delivered cannot be reverted to a previous status.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_restrict_order_item_edits"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_sync_global_order_status"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_total_items INTEGER;
    v_delivered_items INTEGER;
    v_cancelled_items INTEGER;
    v_shipped_items INTEGER;
    v_new_status TEXT;
BEGIN
    -- Count item statuses for the parent order
    SELECT COUNT(*),
           COUNT(*) FILTER (WHERE status = 'delivered'),
           COUNT(*) FILTER (WHERE status = 'cancelled'),
           COUNT(*) FILTER (WHERE status = 'shipped')
    INTO v_total_items, v_delivered_items, v_cancelled_items, v_shipped_items
    FROM public.order_items
    WHERE order_id = NEW.order_id;

    -- Determination Logic
    IF v_total_items = v_delivered_items THEN
        v_new_status := 'delivered';
    ELSIF v_total_items = v_cancelled_items THEN
        v_new_status := 'cancelled';
    ELSIF v_shipped_items > 0 THEN
        v_new_status := 'shipped';
    ELSIF v_delivered_items > 0 THEN
         -- Partial delivery state, keep as processing or shipped
        v_new_status := 'shipped';
    ELSE
        v_new_status := 'processing';
    END IF;

    -- Apply update to parent order
    UPDATE public.orders
    SET status = v_new_status, updated_at = now()
    WHERE id = NEW.order_id AND status != v_new_status;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_sync_global_order_status"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_sync_order_item_vendor"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    SELECT vendor_id INTO NEW.vendor_id
    FROM public.products
    WHERE id = NEW.product_id;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_sync_order_item_vendor"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_sync_order_shipping_total"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.orders
    SET shipping_cost = (
        SELECT COALESCE(SUM(delivery_fee), 0)
        FROM public.order_items
        WHERE order_id = COALESCE(NEW.order_id, OLD.order_id)
    ),
    updated_at = now()
    WHERE id = COALESCE(NEW.order_id, OLD.order_id);

    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."fn_sync_order_shipping_total"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_sync_order_status_from_items"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_total_items integer;
    v_shipped_items integer;
    v_delivered_items integer;
BEGIN
    -- Get counts
    SELECT count(*),
           count(*) FILTER (WHERE status = 'shipped' OR status = 'delivered'),
           count(*) FILTER (WHERE status = 'delivered')
    INTO v_total_items, v_shipped_items, v_delivered_items
    FROM public.order_items
    WHERE order_id = NEW.order_id;

    IF v_delivered_items = v_total_items AND v_total_items > 0 THEN
        UPDATE public.orders SET status = 'delivered', updated_at = now() WHERE id = NEW.order_id AND status != 'delivered';
    ELSIF v_shipped_items = v_total_items AND v_total_items > 0 THEN
        UPDATE public.orders SET status = 'shipped', updated_at = now() WHERE id = NEW.order_id AND status NOT IN ('shipped', 'delivered');
    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_sync_order_status_from_items"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fn_update_user_tier"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_new_tier TEXT;
BEGIN
    SELECT tier_name INTO v_new_tier
    FROM public.loyalty_tiers
    WHERE NEW.loyalty_points >= min_points
    ORDER BY min_points DESC
    LIMIT 1;

    IF v_new_tier IS NOT NULL AND v_new_tier != NEW.loyalty_tier THEN
        NEW.loyalty_tier := v_new_tier;

        -- Notify the user about tier upgrade
        INSERT INTO public.notifications (user_id, title, content, type)
        VALUES (NEW.id, 'New Loyalty Tier!', 'Congratulations! You have been promoted to ' || v_new_tier || ' tier.', 'LOYALTY');
    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."fn_update_user_tier"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."generate_subscription_orders"() RETURNS integer
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_sub RECORD;
    v_order_id UUID;
    v_count INTEGER := 0;
BEGIN
    FOR v_sub IN
        SELECT * FROM public.subscriptions
        WHERE status = 'active' AND next_delivery_date <= now()
    LOOP
        -- 1. Create Order
        INSERT INTO public.orders (user_id, total_amount, final_amount, status, shipping_address)
        SELECT v_sub.user_id, p.price_kes * v_sub.quantity, p.price_kes * v_sub.quantity, 'pending', a.address_line
        FROM public.products p, public.addresses a
        WHERE p.id = v_sub.product_id AND a.id = v_sub.shipping_address_id
        RETURNING id INTO v_order_id;

        -- 2. Create Order Item
        INSERT INTO public.order_items (order_id, product_id, quantity, unit_price, size, color)
        SELECT v_order_id, v_sub.product_id, v_sub.quantity, p.price_kes, v_sub.size, v_sub.color
        FROM public.products p WHERE p.id = v_sub.product_id;

        -- 3. Update Subscription next date
        UPDATE public.subscriptions
        SET next_delivery_date = next_delivery_date + (frequency_days || ' days')::interval,
            updated_at = now()
        WHERE id = v_sub.id;

        v_count := v_count + 1;
    END LOOP;

    RETURN v_count;
END;
$$;


ALTER FUNCTION "public"."generate_subscription_orders"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_active_flash_sales"() RETURNS TABLE("sale_id" "uuid", "sale_name" "text", "end_time" timestamp with time zone, "discount_percent" numeric, "products" "jsonb")
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        fs.id as sale_id,
        fs.name as sale_name,
        fs.end_time,
        fs.discount_percent,
        jsonb_agg(
            jsonb_build_object(
                'id', p.id,
                'name', p.name,
                'price', p.price_kes,
                'image', p.images[1],
                'discounted_price', p.price_kes * (1 - (COALESCE(fsi.discount_override, fs.discount_percent) / 100.0))
            )
        ) as products
    FROM public.flash_sales fs
    JOIN public.flash_sale_items fsi ON fs.id = fsi.flash_sale_id
    JOIN public.products p ON fsi.product_id = p.id
    WHERE fs.is_active = true
      AND fs.start_time <= now()
      AND fs.end_time > now()
    GROUP BY fs.id;
END;
$$;


ALTER FUNCTION "public"."get_active_flash_sales"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_admin_dashboard_stats"() RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    result JSONB;
BEGIN
    SELECT jsonb_build_object(
        'total_revenue', COALESCE(SUM(final_amount), 0),
        'total_orders', COUNT(*),
        'pending_approvals', (SELECT COUNT(*) FROM public.profiles WHERE status = 'pending' AND role = 'vendor'),
        'pending_payouts', (SELECT COUNT(*) FROM public.payouts WHERE status = 'pending'),
        'low_stock_alerts', (SELECT COUNT(*) FROM public.products WHERE stock_count <= 5 AND is_active = true),
        'active_users_24h', (SELECT COUNT(DISTINCT user_id) FROM public.user_sessions WHERE last_active > now() - interval '24 hours')
    ) INTO result
    FROM public.orders
    WHERE status = 'delivered';

    RETURN result;
END;
$$;


ALTER FUNCTION "public"."get_admin_dashboard_stats"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_admin_detailed_sales_report"() RETURNS TABLE("order_id" "uuid", "order_date" timestamp with time zone, "customer_name" "text", "vendor_name" "text", "total_amount" numeric, "commission_earned" numeric, "status" "text")
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        o.id as order_id,
        o.created_at as order_date,
        p.full_name as customer_name,
        v.business_name as vendor_name,
        o.total_amount,
        (o.total_amount * 0.10) as commission_earned, -- Assuming 10% flat for report
        o.status
    FROM public.orders o
    JOIN public.profiles p ON o.user_id = p.id
    LEFT JOIN public.profiles v ON o.vendor_id = v.id
    ORDER BY o.created_at DESC;
END;
$$;


ALTER FUNCTION "public"."get_admin_detailed_sales_report"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_coupon_performance"() RETURNS TABLE("coupon_code" "text", "usage_count" bigint, "total_discount_given" numeric, "total_revenue_generated" numeric)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        o.coupon_code,
        COUNT(o.id) as usage_count,
        SUM(o.discount_amount) as total_discount_given,
        SUM(o.total_amount) as total_revenue_generated
    FROM public.orders o
    WHERE o.coupon_code IS NOT NULL
    GROUP BY o.coupon_code
    ORDER BY usage_count DESC;
END;
$$;


ALTER FUNCTION "public"."get_coupon_performance"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_demand_forecasting"("p_vendor_id" "uuid" DEFAULT NULL::"uuid") RETURNS TABLE("product_id" "uuid", "product_name" "text", "current_stock" integer, "avg_daily_sales" numeric, "forecasted_demand_30d" numeric, "recommended_restock" integer, "risk_level" "text")
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    WITH product_sales AS (
        SELECT
            oi.product_id,
            p.name,
            p.stock_count,
            COUNT(oi.id)::numeric / 30.0 as daily_avg
        FROM public.order_items oi
        JOIN public.products p ON oi.product_id = p.id
        WHERE oi.created_at >= now() - interval '30 days'
          AND (p_vendor_id IS NULL OR p.vendor_id = p_vendor_id)
        GROUP BY oi.product_id, p.name, p.stock_count
    )
    SELECT
        ps.product_id,
        ps.name,
        ps.stock_count,
        ps.daily_avg,
        (ps.daily_avg * 30.0 * 1.2) as forecasted_demand, -- 20% buffer for growth
        GREATEST(0, ceil((ps.daily_avg * 30.0 * 1.2) - ps.stock_count))::int as restock,
        CASE
            WHEN ps.stock_count < (ps.daily_avg * 7) THEN 'HIGH'
            WHEN ps.stock_count < (ps.daily_avg * 14) THEN 'MEDIUM'
            ELSE 'LOW'
        END as risk
    FROM product_sales ps;
END;
$$;


ALTER FUNCTION "public"."get_demand_forecasting"("p_vendor_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_financial_summary"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone) RETURNS TABLE("total_revenue" numeric, "total_commissions" numeric, "total_vendor_earnings" numeric, "order_count" bigint, "avg_order_value" numeric)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        COALESCE(SUM(final_amount), 0)::numeric(12,2),
        COALESCE(SUM(final_amount * (commission_rate / 100.0)), 0)::numeric(12,2),
        COALESCE(SUM(final_amount * (1 - (commission_rate / 100.0))), 0)::numeric(12,2),
        COUNT(*),
        COALESCE(AVG(final_amount), 0)::numeric(12,2)
    FROM public.orders o
    JOIN public.profiles v ON o.vendor_id = v.id
    WHERE o.created_at >= p_start_date
      AND o.created_at <= p_end_date
      AND o.status = 'delivered'; -- Only count completed business
END;
$$;


ALTER FUNCTION "public"."get_financial_summary"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_financial_summary_v2"("p_start_date" "text" DEFAULT NULL::"text", "p_end_date" "text" DEFAULT NULL::"text") RETURNS TABLE("total_gross_revenue" numeric, "platform_commission" numeric, "vendor_net_payouts" numeric, "order_count" bigint, "avg_order_value" numeric, "return_rate_percent" numeric)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        COALESCE(SUM(o.final_amount), 0)::numeric,
        COALESCE(SUM(o.final_amount * (COALESCE(v.commission_rate, 10.0) / 100.0)), 0)::numeric,
        COALESCE(SUM(o.final_amount * (1 - (COALESCE(v.commission_rate, 10.0) / 100.0))), 0)::numeric,
        COUNT(o.id)::bigint,
        CASE WHEN COUNT(o.id) > 0 THEN (SUM(o.final_amount) / COUNT(o.id))::numeric ELSE 0 END,
        (COUNT(o.id) FILTER (WHERE o.status = 'returned')::numeric / GREATEST(COUNT(o.id), 1) * 100)::numeric
    FROM public.orders o
    LEFT JOIN public.profiles v ON o.vendor_id = v.id
    WHERE (p_start_date IS NULL OR o.created_at >= p_start_date::timestamptz)
      AND (p_end_date IS NULL OR o.created_at <= p_end_date::timestamptz)
      AND o.status != 'cancelled';
END;
$$;


ALTER FUNCTION "public"."get_financial_summary_v2"("p_start_date" "text", "p_end_date" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_inventory_health"("p_vendor_id" "uuid" DEFAULT NULL::"uuid") RETURNS TABLE("total_skus" bigint, "out_of_stock_count" bigint, "low_stock_count" bigint, "healthy_stock_count" bigint, "total_stock_value" numeric)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*) as total_skus,
        COUNT(*) FILTER (WHERE stock_count <= 0) as out_of_stock_count,
        COUNT(*) FILTER (WHERE stock_count > 0 AND stock_count <= 10) as low_stock_count,
        COUNT(*) FILTER (WHERE stock_count > 10) as healthy_stock_count,
        SUM(stock_count * price_kes) as total_stock_value
    FROM public.products
    WHERE (p_vendor_id IS NULL OR vendor_id = p_vendor_id)
      AND is_active = true;
END;
$$;


ALTER FUNCTION "public"."get_inventory_health"("p_vendor_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_live_users"() RETURNS TABLE("id" "uuid", "full_name" "text", "email" "text", "role" "text", "session_start" timestamp with time zone, "last_sign_out_at" timestamp with time zone, "session_duration_minutes" double precision)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        p.id,
        p.full_name,
        p.email,
        p.role,
        p.session_start,
        p.last_sign_out_at,
        COALESCE(EXTRACT(EPOCH FROM (NOW() - p.session_start)) / 60, 0)::DOUBLE PRECISION AS session_duration_minutes
    FROM public.profiles p
    WHERE p.session_start IS NOT NULL
      AND p.session_start > (NOW() - INTERVAL '15 minutes'); -- Consider live if active in last 15 mins
END;
$$;


ALTER FUNCTION "public"."get_live_users"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_my_role"() RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'auth'
    AS $$
BEGIN
    RETURN COALESCE(auth.jwt() -> 'user_metadata' ->> 'role', 'user');
END;
$$;


ALTER FUNCTION "public"."get_my_role"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_sales_trends"("p_start_date" "text" DEFAULT NULL::"text", "p_end_date" "text" DEFAULT NULL::"text", "p_interval" "text" DEFAULT 'day'::"text", "p_vendor_id" "uuid" DEFAULT NULL::"uuid") RETURNS TABLE("trend_date" "text", "revenue" numeric, "net_revenue" numeric, "order_count" bigint)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        to_char(date_trunc(p_interval, o.created_at),
                CASE
                    WHEN p_interval = 'day' THEN 'YYYY-MM-DD'
                    WHEN p_interval = 'month' THEN 'YYYY-MM'
                    ELSE 'YYYY-MM-DD'
                END) as trend_date,
        SUM(o.final_amount)::numeric as revenue,
        SUM(o.final_amount::numeric * (1 - (COALESCE(v.commission_rate, 10.0) / 100.0)))::numeric as net_revenue,
        COUNT(*)::bigint as order_count
    FROM public.orders o
    JOIN public.profiles v ON o.vendor_id = v.id
    WHERE (p_vendor_id IS NULL OR o.vendor_id = p_vendor_id)
      AND (p_start_date IS NULL OR o.created_at >= p_start_date::timestamptz)
      AND (p_end_date IS NULL OR o.created_at <= p_end_date::timestamptz)
      AND o.status = 'delivered'
    GROUP BY 1, v.commission_rate
    ORDER BY 1 ASC;
END;
$$;


ALTER FUNCTION "public"."get_sales_trends"("p_start_date" "text", "p_end_date" "text", "p_interval" "text", "p_vendor_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_sales_trends"("p_start_date" timestamp with time zone DEFAULT NULL::timestamp with time zone, "p_end_date" timestamp with time zone DEFAULT NULL::timestamp with time zone, "p_interval" "text" DEFAULT 'day'::"text", "p_vendor_id" "uuid" DEFAULT NULL::"uuid") RETURNS TABLE("period" timestamp without time zone, "total_sales" numeric, "order_count" bigint)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        date_trunc(p_interval, o.created_at) as period,
        SUM(o.total_amount) as total_sales,
        COUNT(o.id) as order_count
    FROM public.orders o
    WHERE (p_start_date IS NULL OR o.created_at >= p_start_date)
      AND (p_end_date IS NULL OR o.created_at <= p_end_date)
      AND (p_vendor_id IS NULL OR o.vendor_id = p_vendor_id)
      AND o.status != 'cancelled'
    GROUP BY 1
    ORDER BY 1 ASC;
END;
$$;


ALTER FUNCTION "public"."get_sales_trends"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone, "p_interval" "text", "p_vendor_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_sales_trends_v2"("p_interval" "text" DEFAULT 'day'::"text", "p_vendor_id" "uuid" DEFAULT NULL::"uuid") RETURNS TABLE("label" "text", "revenue" numeric, "orders_count" bigint)
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        to_char(date_trunc(p_interval, o.created_at),
            CASE WHEN p_interval = 'day' THEN 'Mon DD' ELSE 'Mon YYYY' END) as label,
        SUM(o.final_amount)::numeric,
        COUNT(o.id)::bigint
    FROM public.orders o
    WHERE (p_vendor_id IS NULL OR o.vendor_id = p_vendor_id)
      AND o.status = 'delivered'
      AND o.created_at >= (now() - interval '3 months')
    GROUP BY 1, date_trunc(p_interval, o.created_at)
    ORDER BY date_trunc(p_interval, o.created_at) ASC;
END;
$$;


ALTER FUNCTION "public"."get_sales_trends_v2"("p_interval" "text", "p_vendor_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_user_role"("user_id" "uuid") RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'auth', 'public'
    AS $$
BEGIN
    -- Use JWT metadata to avoid table lookups (Breaks 42P17 recursion)
    IF user_id = auth.uid() THEN
        RETURN COALESCE(auth.jwt() -> 'user_metadata' ->> 'role', 'user');
    END IF;
    -- Admin override (Direct table access bypasses RLS)
    RETURN (SELECT COALESCE(raw_user_meta_data->>'role', 'user') FROM auth.users WHERE id = user_id);
END;
$$;


ALTER FUNCTION "public"."get_user_role"("user_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_vendor_analytics"("vendor_uuid" "uuid", "start_date" timestamp with time zone) RETURNS TABLE("total_sales" bigint, "total_revenue" numeric, "unique_customers" bigint, "top_selling_product" "text")
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(o.id) as total_sales,
        COALESCE(SUM(o.final_amount), 0)::NUMERIC as total_revenue,
        COUNT(DISTINCT o.user_id) as unique_customers,
        (
            SELECT p.name
            FROM public.order_items oi
            JOIN public.products p ON oi.product_id = p.id
            JOIN public.orders o2 ON oi.order_id = o2.id
            WHERE o2.vendor_id = vendor_uuid
            GROUP BY p.name
            ORDER BY COUNT(oi.id) DESC
            LIMIT 1
        ) as top_selling_product
    FROM public.orders o
    WHERE o.vendor_id = vendor_uuid
      AND o.created_at >= start_date
      AND o.status = 'delivered';
END;
$$;


ALTER FUNCTION "public"."get_vendor_analytics"("vendor_uuid" "uuid", "start_date" timestamp with time zone) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_vendor_dashboard_stats"("p_vendor_id" "uuid", "p_days" integer DEFAULT 30) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_total_revenue NUMERIC;
    v_order_count INTEGER;
    v_avg_order_value NUMERIC;
    v_best_sellers JSONB;
    v_sales_trends JSONB;
    v_stock_health JSONB;
    v_start_date TIMESTAMPTZ;
BEGIN
    v_start_date := NOW() - (p_days || ' days')::INTERVAL;

    -- 1. General Metrics (Completed/Delivered orders)
    SELECT
        COALESCE(SUM(total_amount), 0),
        COUNT(*),
        COALESCE(AVG(total_amount), 0)
    INTO v_total_revenue, v_order_count, v_avg_order_value
    FROM public.orders
    WHERE vendor_id = p_vendor_id
      AND status NOT IN ('cancelled', 'failed')
      AND created_at >= v_start_date;

    -- 2. Best Sellers (Top 5 products by quantity sold)
    SELECT jsonb_agg(t) INTO v_best_sellers FROM (
        SELECT
            p.name,
            SUM(oi.quantity) as units_sold,
            SUM(oi.quantity * oi.unit_price) as revenue
        FROM public.order_items oi
        JOIN public.products p ON oi.product_id = p.id
        JOIN public.orders o ON oi.order_id = o.id
        WHERE o.vendor_id = p_vendor_id
          AND o.status NOT IN ('cancelled', 'failed')
          AND o.created_at >= v_start_date
        GROUP BY p.name
        ORDER BY units_sold DESC
        LIMIT 5
    ) t;

    -- 3. Sales Trends (Daily revenue over the period)
    SELECT jsonb_agg(t) INTO v_sales_trends FROM (
        WITH date_series AS (
            SELECT generate_series(
                v_start_date::date,
                NOW()::date,
                '1 day'::interval
            )::date as d
        )
        SELECT
            ds.d::text as date,
            COALESCE(SUM(o.total_amount), 0) as revenue,
            COUNT(o.id) as order_count
        FROM date_series ds
        LEFT JOIN public.orders o ON o.created_at::date = ds.d
          AND o.vendor_id = p_vendor_id
          AND o.status NOT IN ('cancelled', 'failed')
        GROUP BY ds.d
        ORDER BY ds.d ASC
    ) t;

    -- 4. Stock Health
    SELECT jsonb_build_object(
        'total_products', COUNT(*),
        'low_stock', COUNT(*) FILTER (WHERE stock_count > 0 AND stock_count <= 5),
        'out_of_stock', COUNT(*) FILTER (WHERE stock_count = 0),
        'total_stock_value', COALESCE(SUM(price_kes * stock_count), 0)
    ) INTO v_stock_health
    FROM public.products
    WHERE vendor_id = p_vendor_id;

    RETURN jsonb_build_object(
        'revenue', v_total_revenue,
        'order_count', v_order_count,
        'avg_order_value', v_avg_order_value,
        'best_sellers', COALESCE(v_best_sellers, '[]'::jsonb),
        'sales_trends', COALESCE(v_sales_trends, '[]'::jsonb),
        'stock_health', v_stock_health
    );
END;
$$;


ALTER FUNCTION "public"."get_vendor_dashboard_stats"("p_vendor_id" "uuid", "p_days" integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_vendor_inventory_stats"("p_vendor_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    result JSONB;
BEGIN
    SELECT jsonb_build_object(
        'total_items', COUNT(*),
        'low_stock_count', COUNT(*) FILTER (WHERE stock_count > 0 AND stock_count <= 5),
        'out_of_stock_count', COUNT(*) FILTER (WHERE stock_count = 0),
        'total_stock_value', COALESCE(SUM(price_kes * stock_count), 0),
        'active_items', COUNT(*) FILTER (WHERE is_active = true)
    ) INTO result
    FROM public.products
    WHERE vendor_id = p_vendor_id;

    RETURN result;
END;
$$;


ALTER FUNCTION "public"."get_vendor_inventory_stats"("p_vendor_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_banner_resubmission"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    -- If a vendor (not admin) updates the banner content, reset status
    IF (public.get_user_role(auth.uid()) = 'vendor' AND
        (OLD.title IS DISTINCT FROM NEW.title OR
         OLD.subtitle IS DISTINCT FROM NEW.subtitle OR
         OLD.image_url IS DISTINCT FROM NEW.image_url)) THEN
        NEW.status := 'pending';
        NEW.active := false;
        NEW.rejection_notes := NULL;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_banner_resubmission"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_coupon_usage"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    IF (NEW.status = 'delivered' AND OLD.status != 'delivered' AND NEW.coupon_id IS NOT NULL) THEN
        UPDATE public.coupons
        SET usage_count = usage_count + 1
        WHERE id = NEW.coupon_id;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_coupon_usage"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_mpesa_callback_update"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- Transition from 'pending' to 'completed'
    IF NEW.status = 'completed' AND (OLD.status IS NULL OR OLD.status = 'pending') THEN
        -- Mark order as paid
        UPDATE public.orders
        SET status = 'paid',
            updated_at = now()
        WHERE id = NEW.order_id;

        -- Log status change in history
        INSERT INTO public.order_status_history (order_id, status, notes)
        VALUES (NEW.order_id, 'paid', 'Payment confirmed via M-Pesa. Receipt: ' || COALESCE(NEW.mpesa_receipt_number, 'N/A'));

        -- Deduct stock from products
        UPDATE public.products p
        SET stock_count = p.stock_count - oi.quantity,
            in_stock = (p.stock_count - oi.quantity > 0)
        FROM public.order_items oi
        WHERE oi.order_id = NEW.order_id AND p.id = oi.product_id;

        -- Send notification to the user
        INSERT INTO public.notifications (user_id, title, message, type)
        SELECT user_id, 'Payment Received', 'We have received your payment for Order #' || substring(id::text, 1, 8), 'success'
        FROM public.orders WHERE id = NEW.order_id;

    ELSIF NEW.status = 'failed' AND (OLD.status IS NULL OR OLD.status != 'failed') THEN
        -- Mark order as payment_failed
        UPDATE public.orders SET status = 'payment_failed', updated_at = now() WHERE id = NEW.order_id;

        -- Log failure in history
        INSERT INTO public.order_status_history (order_id, status, notes)
        VALUES (NEW.order_id, 'payment_failed', 'M-Pesa transaction failed or was cancelled.');
    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_mpesa_callback_update"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_new_password_reset"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
  project_url TEXT;
BEGIN
  -- We use the current project's host from request headers if available,
  -- or fallback to a hardcoded/config value if needed.
  -- In Supabase, the host is usually available in the headers during API calls.
  PERFORM
    net.http_post(
      url := 'https://' || (current_setting('request.headers')::json->>'host') || '/functions/v1/send-password-reset',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'Authorization', current_setting('request.headers')::json->>'authorization'
      ),
      body := jsonb_build_object('record', row_to_json(NEW))
    );
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_new_password_reset"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_new_user"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  INSERT INTO public.profiles (id, full_name, email, role, status, avatar_url)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
    NEW.email,
    COALESCE(NEW.raw_user_meta_data->>'role', 'student'),
    CASE WHEN (NEW.raw_user_meta_data->>'role') = 'vendor' THEN 'pending' ELSE 'active' END,
    COALESCE(NEW.raw_user_meta_data->>'avatar_url', '')
  );

  -- Create wallet for new user
  INSERT INTO public.wallets (user_id) VALUES (NEW.id);

  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_new_user"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_payment_completion"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (NEW.status = 'completed' AND (OLD.status IS NULL OR OLD.status != 'completed')) THEN
        -- Update the main order status
        UPDATE public.orders
        SET
            status = 'paid',
            updated_at = now()
        WHERE id = NEW.order_id;

        -- Deduct inventory
        UPDATE public.products p
        SET
            stock_count = p.stock_count - oi.quantity,
            in_stock = CASE WHEN (p.stock_count - oi.quantity) > 0 THEN true ELSE false END
        FROM public.order_items oi
        WHERE oi.order_id = NEW.order_id AND p.id = oi.product_id;

        -- Create a notification for the user
        INSERT INTO public.notifications (user_id, title, message, type)
        SELECT user_id, 'Payment Received', 'Your payment for order #' || substring(id::text, 1, 8) || ' was successful.', 'success'
        FROM public.orders WHERE id = NEW.order_id;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_payment_completion"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_product_update_time"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_product_update_time"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_profile_updated"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- Automatically update the updated_at timestamp
    NEW.updated_at = NOW();

    -- Audit Log: Track FCM Token changes (useful for debugging push issues)
    IF (OLD.fcm_token IS DISTINCT FROM NEW.fcm_token) THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.id, 'FCM_UPDATE', 'FCM Token updated successfully', 'info');
    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_profile_updated"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_referral_reward"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_referrer_id UUID;
    v_referrer_wallet_id UUID;
BEGIN
    -- If the new user was referred by someone
    IF (NEW.referred_by IS NOT NULL) THEN
        -- Award 500 to the Referrer
        SELECT id INTO v_referrer_wallet_id FROM public.wallets WHERE user_id = NEW.referred_by;

        IF v_referrer_wallet_id IS NOT NULL THEN
            UPDATE public.wallets
            SET balance = balance + 500, updated_at = now()
            WHERE id = v_referrer_wallet_id;

            INSERT INTO public.wallet_transactions (wallet_id, amount, type, reference_type, reference_id, description)
            VALUES (v_referrer_wallet_id, 500, 'credit', 'bonus', NEW.id, 'Referral bonus for inviting ' || NEW.full_name);

            -- Notify Referrer
            INSERT INTO public.notifications (user_id, title, message, type)
            VALUES (NEW.referred_by, 'Referral Bonus!', 'You earned KSh 500 for referring ' || NEW.full_name, 'reward');
        END IF;

        -- Optional: Award 500 to the New User as well (Welcome Bonus)
        -- UPDATE public.wallets SET balance = balance + 500 WHERE user_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_referral_reward"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_stock_on_order_cancel"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  IF NEW.status = 'cancelled' AND OLD.status != 'cancelled' THEN
    UPDATE public.products p
    SET stock_count = p.stock_count + oi.quantity
    FROM public.order_items oi
    WHERE oi.order_id = NEW.id AND p.id = oi.product_id;
  END IF;
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_stock_on_order_cancel"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_stock_on_order_item"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.products
    SET stock_count = stock_count - NEW.quantity,
        in_stock = (stock_count - NEW.quantity > 0)
    WHERE id = NEW.product_id;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_stock_on_order_item"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_successful_payment"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    IF NEW.status = 'success' THEN
        UPDATE public.orders SET payment_status = 'paid', status = 'processing' WHERE id = NEW.order_id;
        INSERT INTO public.system_logs (user_id, action, details)
        VALUES ((SELECT user_id FROM public.orders WHERE id = NEW.order_id), 'PAYMENT_COMPLETE', jsonb_build_object('ref', NEW.transaction_id));
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."handle_successful_payment"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_user_login"("target_user_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.profiles SET last_login = now() WHERE id = target_user_id;
    INSERT INTO public.system_logs (user_id, action, details, severity)
    VALUES (target_user_id, 'USER_LOGIN', jsonb_build_object('message', 'User logged into the system'), 'info');
END;
$$;


ALTER FUNCTION "public"."handle_user_login"("target_user_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."handle_user_logout"("target_user_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO public.system_logs (user_id, action, details, severity)
    VALUES (target_user_id, 'USER_LOGOUT', '{}', 'info');
END;
$$;


ALTER FUNCTION "public"."handle_user_logout"("target_user_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."is_admin"() RETURNS boolean
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  RETURN (SELECT (raw_user_meta_data->>'role')::text FROM auth.users WHERE id = auth.uid()) = 'admin';
END;
$$;


ALTER FUNCTION "public"."is_admin"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."join_product_waitlist"("p_product_id" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO public.product_waitlist (user_id, product_id)
    VALUES (auth.uid(), p_product_id)
    ON CONFLICT (user_id, product_id, notified) DO NOTHING;
END;
$$;


ALTER FUNCTION "public"."join_product_waitlist"("p_product_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."jsonb_diff"("l" "jsonb", "r" "jsonb") RETURNS "jsonb"
    LANGUAGE "plpgsql" IMMUTABLE
    AS $$
DECLARE
    result JSONB := '{}'::jsonb;
    key TEXT;
    value JSONB;
BEGIN
    FOR key, value IN SELECT * FROM jsonb_each(l) LOOP
        IF NOT (r ? key) OR (r -> key) != value THEN
            result := result || jsonb_build_object(key, value);
        END IF;
    END LOOP;
    RETURN result;
END;
$$;


ALTER FUNCTION "public"."jsonb_diff"("l" "jsonb", "r" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_address_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.user_id, 'ADDRESS_ADDED', jsonb_build_object('address_name', NEW.name), 'info');
    ELSIF (TG_OP = 'UPDATE') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.user_id, 'ADDRESS_UPDATED', jsonb_build_object('address_name', NEW.name), 'info');
    ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (OLD.user_id, 'ADDRESS_DELETED', jsonb_build_object('address_name', OLD.name), 'info');
    END IF;
    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."log_address_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_coupon_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (auth.uid(), 'COUPON_CREATED', jsonb_build_object('coupon_id', NEW.id, 'code', NEW.code, 'discount_percent', NEW.discount_percent), 'info');
    ELSIF (TG_OP = 'UPDATE') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (auth.uid(), 'COUPON_UPDATED', jsonb_build_object('coupon_id', NEW.id, 'code', NEW.code, 'is_active', NEW.is_active), 'info');
    ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (auth.uid(), 'COUPON_DELETED', jsonb_build_object('coupon_id', OLD.id, 'code', OLD.code), 'warning');
    END IF;
    RETURN OLD; -- For DELETE
END;
$$;


ALTER FUNCTION "public"."log_coupon_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_favorite_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.user_id, 'FAVORITE_ADDED', jsonb_build_object('product_id', NEW.product_id), 'info');
    ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (OLD.user_id, 'FAVORITE_REMOVED', jsonb_build_object('product_id', OLD.product_id), 'info');
    END IF;
    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."log_favorite_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_order_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.user_id, 'ORDER_CREATED', jsonb_build_object('order_id', NEW.id, 'status', NEW.status), 'info');
    ELSIF (TG_OP = 'UPDATE') THEN
        IF (OLD.status IS DISTINCT FROM NEW.status) THEN
            INSERT INTO public.system_logs (user_id, action, details, severity)
            VALUES (NEW.user_id, 'ORDER_STATUS_UPDATED', jsonb_build_object('order_id', NEW.id, 'old_status', OLD.status, 'new_status', NEW.status), 'info');
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_order_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_order_status_change"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.order_status_history (order_id, status, notes)
        VALUES (NEW.id, NEW.status, 'Order placed');
    ELSIF (TG_OP = 'UPDATE' AND OLD.status IS DISTINCT FROM NEW.status) THEN
        INSERT INTO public.order_status_history (order_id, status, changed_by)
        VALUES (NEW.id, NEW.status, auth.uid());
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_order_status_change"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_payment_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_user_id UUID;
BEGIN
    -- Get user_id from the related order
    SELECT user_id INTO v_user_id FROM public.orders WHERE id = NEW.order_id;

    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (v_user_id, 'PAYMENT_INITIATED', jsonb_build_object('payment_id', NEW.id, 'order_id', NEW.order_id, 'amount', NEW.amount, 'status', NEW.status), 'info');
    ELSIF (TG_OP = 'UPDATE') THEN
        IF (OLD.status IS DISTINCT FROM NEW.status) THEN
            INSERT INTO public.system_logs (user_id, action, details, severity)
            VALUES (v_user_id, 'PAYMENT_STATUS_UPDATED', jsonb_build_object('payment_id', NEW.id, 'order_id', NEW.order_id, 'old_status', OLD.status, 'new_status', NEW.status, 'mpesa_receipt', NEW.mpesa_receipt_number), 'info');
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_payment_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_payout_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.vendor_id, 'PAYOUT_REQUESTED', jsonb_build_object('amount', NEW.amount, 'status', NEW.status), 'info');
    ELSIF (TG_OP = 'UPDATE') THEN
        IF (OLD.status IS DISTINCT FROM NEW.status) THEN
            INSERT INTO public.system_logs (user_id, action, details, severity)
            VALUES (NEW.vendor_id, 'PAYOUT_STATUS_UPDATED', jsonb_build_object('amount', NEW.amount, 'old_status', OLD.status, 'new_status', NEW.status), 'info');
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_payout_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_product_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.vendor_id, 'PRODUCT_CREATED', jsonb_build_object('product_id', NEW.id, 'name', NEW.name, 'price', NEW.price_kes), 'info');
    ELSIF (TG_OP = 'UPDATE') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.vendor_id, 'PRODUCT_UPDATED', jsonb_build_object('product_id', NEW.id, 'name', NEW.name, 'stock_count', NEW.stock_count, 'is_active', NEW.is_active), 'info');
    ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (OLD.vendor_id, 'PRODUCT_DELETED', jsonb_build_object('product_id', OLD.id, 'name', OLD.name), 'warning');
    END IF;
    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."log_product_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_profile_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.status IS DISTINCT FROM NEW.status OR OLD.role IS DISTINCT FROM NEW.role OR OLD.is_verified_vendor IS DISTINCT FROM NEW.is_verified_vendor) THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.id, 'PROFILE_CRITICAL_UPDATE', jsonb_build_object(
            'old_status', OLD.status, 'new_status', NEW.status,
            'old_role', OLD.role, 'new_role', NEW.role,
            'old_verified', OLD.is_verified_vendor, 'new_verified', NEW.is_verified_vendor
        ), 'warning');
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_profile_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_profile_changes"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO public.system_logs (user_id, action, details)
    VALUES (NEW.id, 'PROFILE_UPDATE', jsonb_build_object('changed_fields', public.jsonb_diff(to_jsonb(NEW), to_jsonb(OLD))));
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_profile_changes"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_profile_status_change"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    IF (TG_OP = 'UPDATE' AND OLD.status IS DISTINCT FROM NEW.status) THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (
            auth.uid(),
            'USER_STATUS_UPDATE',
            'User ' || NEW.id || ' status changed: ' || OLD.status || ' -> ' || NEW.status,
            CASE WHEN NEW.status = 'banned' OR NEW.status = 'rejected' THEN 'warning' ELSE 'info' END
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_profile_status_change"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_report_generation"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO public.audit_logs (user_id, action, details, severity)
    VALUES (NEW.generated_by, 'REPORT_GENERATED',
            'Generated ' || NEW.report_type || ' in ' || NEW.format || ' format.',
            'info');
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_report_generation"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_review_activity"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.user_id, 'REVIEW_CREATED', jsonb_build_object('product_id', NEW.product_id, 'rating', NEW.rating), 'info');
    ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (OLD.user_id, 'REVIEW_DELETED', jsonb_build_object('product_id', OLD.product_id), 'info');
    END IF;
    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."log_review_activity"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_storage_upload"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO public.system_logs (user_id, action, details, severity)
    VALUES (
        NEW.owner,
        'FILE_UPLOAD',
        jsonb_build_object(
            'bucket_id', NEW.bucket_id,
            'name', NEW.name,
            'metadata', NEW.metadata
        ),
        'info'
    );
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_storage_upload"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_user_login"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
  BEGIN
    INSERT INTO public.system_logs (user_id, action, details)
    VALUES (NEW.id, 'LOGIN', jsonb_build_object('email', NEW.email));
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_user_login"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_user_status_change"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (
            auth.uid(),
            'USER_STATUS_UPDATE',
            jsonb_build_object(
                'target_user_id', NEW.id,
                'old_status', OLD.status,
                'new_status', NEW.status,
                'notes', NEW.status_notes
            ),
            CASE WHEN NEW.status = 'banned' THEN 'critical' ELSE 'info' END
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_user_status_change"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."mark_all_active_notifications_read"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.notifications
    SET is_read = true
    WHERE user_id = auth.uid()
    AND is_read = false
    AND is_archived = false;
END;
$$;


ALTER FUNCTION "public"."mark_all_active_notifications_read"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."mark_messages_delivered"("receiver_uuid" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.messages SET is_delivered = true
    WHERE receiver_id = receiver_uuid AND is_delivered = false;
END;
$$;


ALTER FUNCTION "public"."mark_messages_delivered"("receiver_uuid" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."mark_messages_read"("sender_uuid" "uuid", "receiver_uuid" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.messages
    SET is_read = true, is_delivered = true
    WHERE sender_id = sender_uuid AND receiver_id = receiver_uuid AND is_read = false;
END;
$$;


ALTER FUNCTION "public"."mark_messages_read"("sender_uuid" "uuid", "receiver_uuid" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."notify_fcm_bridge"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_fcm_token TEXT;
    v_payload JSONB;
BEGIN
    -- Get the recipient's FCM token
    SELECT fcm_token INTO v_fcm_token FROM public.profiles WHERE id = NEW.user_id;

    IF v_fcm_token IS NOT NULL THEN
        v_payload := jsonb_build_object(
            'to', v_fcm_token,
            'notification', jsonb_build_object(
                'title', NEW.title,
                'body', NEW.body
            ),
            'data', jsonb_build_object(
                'category', NEW.category,
                'id', NEW.id
            )
        );

        -- Invoke Edge Function (Assumes function name 'fcm-pusher')
        PERFORM net.http_post(
            url := (SELECT value FROM public.system_settings WHERE key = 'edge_function_url') || '/fcm-pusher',
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'Authorization', 'Bearer ' || (SELECT value FROM public.system_settings WHERE key = 'service_role_key')
            ),
            body := v_payload
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."notify_fcm_bridge"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."notify_vendor_status_change"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.status IS DISTINCT FROM NEW.status) THEN
        INSERT INTO public.notifications (user_id, title, message, type)
        VALUES (
            NEW.id,
            'Account Status Updated',
            'Your account status is now ' || UPPER(NEW.status) || '. ' || COALESCE(NEW.status_notes, ''),
            'system'
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."notify_vendor_status_change"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."on_new_order_notify_vendor"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (NEW.vendor_id IS NOT NULL) THEN
        INSERT INTO public.notifications (user_id, title, body, category, priority_level)
        VALUES (
            NEW.vendor_id,
            'New Order Received',
            'You have a new order #' || substring(NEW.id::text, 1, 8) || ' for KES ' || NEW.total_amount,
            'vendor_order',
            'high'
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."on_new_order_notify_vendor"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."on_order_cancelled_refund"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.status != 'cancelled' AND NEW.status = 'cancelled' AND NEW.payment_status = 'paid') THEN
        -- Refund to Wallet
        UPDATE public.wallets
        SET balance = balance + NEW.total_amount, updated_at = now()
        WHERE user_id = NEW.user_id;

        INSERT INTO public.wallet_transactions (wallet_id, amount, type, reference_type, reference_id, description)
        SELECT id, NEW.total_amount, 'credit', 'refund', NEW.id, 'Refund for cancelled order #' || substring(NEW.id::text, 1, 8)
        FROM public.wallets WHERE user_id = NEW.user_id;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."on_order_cancelled_refund"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."on_order_status_change_notify"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_user_id UUID;
    v_title TEXT;
    v_body TEXT;
    v_category TEXT := 'order';
    v_priority TEXT := 'high';
BEGIN
    -- Only notify on meaningful status changes
    IF (OLD.status IS DISTINCT FROM NEW.status) THEN
        v_user_id := NEW.user_id;

        CASE NEW.status
            WHEN 'processing' THEN
                v_title := 'Order Confirmed';
                v_body := 'Your order #' || substring(NEW.id::text, 1, 8) || ' is now being processed.';
            WHEN 'shipped' THEN
                v_title := 'Order Shipped';
                v_body := 'Great news! Your order #' || substring(NEW.id::text, 1, 8) || ' is on its way.';
            WHEN 'delivered' THEN
                v_title := 'Order Delivered';
                v_body := 'Your order #' || substring(NEW.id::text, 1, 8) || ' has been delivered. Enjoy your NurseWear!';
            WHEN 'cancelled' THEN
                v_title := 'Order Cancelled';
                v_body := 'Your order #' || substring(NEW.id::text, 1, 8) || ' has been cancelled.';
                v_priority := 'normal';
            ELSE
                RETURN NEW;
        END CASE;

        INSERT INTO public.notifications (user_id, title, body, category, priority_level)
        VALUES (v_user_id, v_title, v_body, v_category, v_priority);
    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."on_order_status_change_notify"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."on_urgent_message_notify"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF NEW.priority = 'urgent' THEN
        INSERT INTO public.notifications (user_id, title, body, category, priority_level, action_url)
        VALUES (
            NEW.receiver_id,
            'Urgent Message',
            LEFT(NEW.message, 50) || '...',
            'needs_reply',
            'red',
            '/chat/' || NEW.sender_id
        );
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."on_urgent_message_notify"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."process_refund"("p_return_id" "uuid", "p_admin_notes" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_order_id uuid;
    v_user_id uuid;
    v_amount numeric;
BEGIN
    -- 1. Get return request details
    SELECT order_id, user_id INTO v_order_id, v_user_id
    FROM public.return_requests
    WHERE id = p_return_id AND status = 'item_received';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Return request not found or item not yet received.';
    END IF;

    -- 2. Get order amount
    SELECT final_amount INTO v_amount
    FROM public.orders
    WHERE id = v_order_id;

    -- 3. Refund to wallet
    UPDATE public.profiles
    SET wallet_balance = wallet_balance + v_amount
    WHERE id = v_user_id;

    -- 4. Update return status
    UPDATE public.return_requests
    SET status = 'refunded', admin_notes = p_admin_notes, updated_at = now()
    WHERE id = p_return_id;

    -- 5. Update order status
    UPDATE public.orders
    SET payment_status = 'refunded', status = 'cancelled', updated_at = now()
    WHERE id = v_order_id;

    -- 6. Log activity (if logging exists)
    -- INSERT INTO public.activity_logs (user_id, action, details) ...
END;
$$;


ALTER FUNCTION "public"."process_refund"("p_return_id" "uuid", "p_admin_notes" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."process_wallet_payment"("p_order_id" "uuid", "p_user_id" "uuid", "p_amount" numeric) RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_current_balance NUMERIC;
    v_order_status TEXT;
BEGIN
    -- 1. Check current balance with row-level locking
    SELECT balance INTO v_current_balance
    FROM public.wallets
    WHERE user_id = p_user_id
    FOR UPDATE;

    IF v_current_balance IS NULL OR v_current_balance < p_amount THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', 'Insufficient wallet balance'
        );
    END IF;

    -- 2. Verify order status
    SELECT status INTO v_order_status
    FROM public.orders
    WHERE id = p_order_id;

    IF v_order_status <> 'pending' AND v_order_status <> 'unpaid' THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', 'Order is not in a payable state'
        );
    END IF;

    -- 3. Deduct from wallet
    UPDATE public.wallets
    SET balance = balance - p_amount,
        updated_at = now()
    WHERE user_id = p_user_id;

    -- 4. Update order status to 'paid'
    UPDATE public.orders
    SET status = 'paid',
        payment_status = 'paid',
        payment_method = 'wallet',
        updated_at = now()
    WHERE id = p_order_id;

    -- 5. Record Wallet Transaction
    INSERT INTO public.wallet_transactions (
        wallet_id,
        amount,
        type,
        reference_type,
        reference_id,
        description
    )
    SELECT
        id,
        p_amount,
        'debit',
        'order',
        p_order_id,
        'Payment for order #' || substring(p_order_id::text, 1, 8)
    FROM public.wallets
    WHERE user_id = p_user_id;

    -- 6. Log System Action
    INSERT INTO public.system_logs (user_id, action, details, severity)
    VALUES (
        p_user_id,
        'WALLET_PAYMENT_SUCCESS',
        jsonb_build_object('order_id', p_order_id, 'amount', p_amount),
        'info'
    );

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Payment processed successfully',
        'transaction_id', p_order_id
    );

EXCEPTION WHEN OTHERS THEN
    RETURN jsonb_build_object(
        'success', false,
        'message', 'Transaction failed: ' || SQLERRM
    );
END;
$$;


ALTER FUNCTION "public"."process_wallet_payment"("p_order_id" "uuid", "p_user_id" "uuid", "p_amount" numeric) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."protect_profile_fields"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    -- Only Admins can change roles, status, or verification
    IF (public.get_user_role(auth.uid()) <> 'admin') THEN
        IF (NEW.role <> OLD.role OR
            NEW.status <> OLD.status OR
            NEW.is_verified_vendor <> OLD.is_verified_vendor OR
            NEW.commission_rate <> OLD.commission_rate) THEN
            RAISE EXCEPTION 'Unauthorized: Only administrators can modify role, status, or verification fields.';
        END IF;
    END IF;

    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."protect_profile_fields"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."request_fitting_service"("p_order_id" "uuid", "p_preferred_date" "text", "p_preferred_slot" "text", "p_address" "text", "p_user_id" "uuid" DEFAULT NULL::"uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_user_id UUID;
    v_vendor_id UUID;
    v_scheduled_at TIMESTAMP WITH TIME ZONE;
BEGIN
    -- Resolve User ID
    v_user_id := COALESCE(p_user_id, auth.uid());

    -- Get vendor from order
    SELECT vendor_id INTO v_vendor_id FROM public.orders WHERE id = p_order_id;

    -- Update order flag
    UPDATE public.orders
    SET is_fitting_service = TRUE,
        fitting_status = 'scheduled',
        fitting_notes = 'Slot: ' || p_preferred_slot || ' at ' || p_address,
        updated_at = now()
    WHERE id = p_order_id;

    -- Insert into fitting_appointments
    -- Note: Since we only have date/slot strings from UI for now, we store them in instructions/notes
    -- unless we want to cast them. For reliability, we'll store the text.
    INSERT INTO public.fitting_appointments (
        order_id,
        user_id,
        vendor_id,
        scheduled_at,
        status,
        special_instructions
    ) VALUES (
        p_order_id,
        v_user_id,
        COALESCE(v_vendor_id, v_user_id), -- Fallback to user if no vendor
        now() + interval '1 day', -- Placeholder for actual parsed date
        'pending',
        'Requested Slot: ' || p_preferred_date || ' ' || p_preferred_slot || '. Address: ' || p_address
    );

    -- Log to status history
    INSERT INTO public.order_status_history (order_id, status, notes)
    VALUES (p_order_id, 'processing', 'Home fitting requested for ' || p_preferred_date || ' (' || p_preferred_slot || ')');
END;
$$;


ALTER FUNCTION "public"."request_fitting_service"("p_order_id" "uuid", "p_preferred_date" "text", "p_preferred_slot" "text", "p_address" "text", "p_user_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."reset_password_with_otp"("target_email" "text", "otp_code" "text", "new_password" "text") RETURNS json
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    reset_record_id UUID;
BEGIN
    -- Check for a valid (unexpired) OTP.
    -- We allow both used and unused codes here because the app's verifyOtp step
    -- might have already marked it as used before this RPC is called.
    SELECT id INTO reset_record_id
    FROM public.password_resets
    WHERE email = target_email
      AND code = otp_code
      AND expires_at > now()
    ORDER BY created_at DESC
    LIMIT 1;

    IF reset_record_id IS NULL THEN
        RETURN json_build_object('success', false, 'message', 'Invalid or expired OTP code.');
    END IF;

    -- Update the password in auth.users
    UPDATE auth.users
    SET encrypted_password = crypt(new_password, gen_salt('bf')),
        updated_at = now()
    WHERE email = target_email;

    -- Ensure the record is marked as used
    UPDATE public.password_resets
    SET is_used = true
    WHERE id = reset_record_id;

    RETURN json_build_object('success', true, 'message', 'Password updated successfully.');
END;
$$;


ALTER FUNCTION "public"."reset_password_with_otp"("target_email" "text", "otp_code" "text", "new_password" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."restore_all_archived_notifications"() RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.notifications
    SET is_archived = false
    WHERE user_id = auth.uid()
    AND is_archived = true;
END;
$$;


ALTER FUNCTION "public"."restore_all_archived_notifications"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."restore_stock_on_order_cancel"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF (OLD.status != 'cancelled' AND NEW.status = 'cancelled') THEN
        -- Increase stock for each item in the order
        UPDATE public.products p
        SET stock_count = p.stock_count + oi.quantity,
            in_stock = true
        FROM public.order_items oi
        WHERE oi.product_id = p.id
        AND oi.order_id = NEW.id;

        -- Log the action
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.user_id, 'STOCK_RESTORED_CANCEL', jsonb_build_object('order_id', NEW.id), 'info');
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."restore_stock_on_order_cancel"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rls_auto_enable"() RETURNS "event_trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'pg_catalog'
    AS $$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$$;


ALTER FUNCTION "public"."rls_auto_enable"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."set_product_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."set_product_updated_at"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sync_cart_items"("p_items" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    item JSONB;
BEGIN
    FOR item IN SELECT * FROM jsonb_array_elements(p_items)
    LOOP
        INSERT INTO public.cart_items (
            user_id, product_id, quantity, size, color_name, color_hex, embroidery_name, updated_at
        ) VALUES (
            auth.uid(),
            (item->>'product_id')::UUID,
            (item->>'quantity')::INTEGER,
            item->>'size',
            item->>'color_name',
            (item->>'color_hex')::BIGINT,
            item->>'embroidery_name',
            now()
        )
        ON CONFLICT (user_id, product_id, size, color_name, embroidery_name)
        DO UPDATE SET
            quantity = EXCLUDED.quantity,
            updated_at = now();
    END LOOP;
END;
$$;


ALTER FUNCTION "public"."sync_cart_items"("p_items" "jsonb") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sync_order_final_amount"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    IF NEW.final_amount IS NULL THEN
        NEW.final_amount := NEW.total_amount;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."sync_order_final_amount"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sync_order_return_status"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    IF NEW.status = 'approved' AND OLD.status = 'pending' THEN
        UPDATE public.orders
        SET status = 'processing' -- Or 'returning' if we add that status
        WHERE id = NEW.order_id;
    ELSIF NEW.status = 'refunded' AND OLD.status != 'refunded' THEN
        -- Add logic for wallet refund if necessary, or just mark order
        UPDATE public.orders
        SET status = 'cancelled' -- Reflected as returned/refunded in UI
        WHERE id = NEW.order_id;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."sync_order_return_status"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sync_product_stock_status"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    IF NEW.stock_count <= 0 THEN
        NEW.in_stock := false;
    ELSE
        NEW.in_stock := true;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."sync_product_stock_status"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sync_profile_to_auth"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  UPDATE auth.users
  SET raw_user_meta_data = raw_user_meta_data ||
    jsonb_build_object('full_name', NEW.full_name, 'role', NEW.role)
  WHERE id = NEW.id;
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."sync_profile_to_auth"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."sync_ratings_on_review"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    target_product_id UUID;
    target_vendor_id UUID;
BEGIN
    IF (TG_OP = 'DELETE') THEN
        target_product_id := OLD.product_id;
    ELSE
        target_product_id := NEW.product_id;
    END IF;

    UPDATE public.products
    SET
        rating = COALESCE((SELECT AVG(rating)::DECIMAL(3,2) FROM public.reviews WHERE product_id = target_product_id), 5.0),
        reviews_count = (SELECT COUNT(*) FROM public.reviews WHERE product_id = target_product_id)
    WHERE id = target_product_id;

    SELECT vendor_id INTO target_vendor_id FROM public.products WHERE id = target_product_id;

    IF target_vendor_id IS NOT NULL THEN
        UPDATE public.profiles
        SET
            rating = COALESCE((SELECT AVG(rating)::DECIMAL(3,2) FROM public.products WHERE vendor_id = target_vendor_id AND reviews_count > 0), 5.0),
            reviews_count = COALESCE((SELECT SUM(reviews_count) FROM public.products WHERE vendor_id = target_vendor_id), 0)
        WHERE id = target_vendor_id;
    END IF;

    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."sync_ratings_on_review"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."track_stock_update"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    IF (OLD.stock_count IS DISTINCT FROM NEW.stock_count) THEN
        NEW.last_stock_update := NOW();
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."track_stock_update"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_loyalty_tier"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.loyalty_tier := CASE
        WHEN NEW.loyalty_points > 5000 THEN 'gold'
        WHEN NEW.loyalty_points > 1000 THEN 'silver'
        ELSE 'bronze'
    END;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."update_loyalty_tier"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_order_tracking"("p_order_id" "uuid", "p_tracking_number" "text", "p_courier_name" "text", "p_status" "text" DEFAULT NULL::"text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.orders
    SET
        tracking_number = p_tracking_number,
        courier_name = p_courier_name,
        status = COALESCE(p_status, status),
        updated_at = now()
    WHERE id = p_order_id;

    -- Also log in status history if status changed
    IF p_status IS NOT NULL THEN
        INSERT INTO public.order_status_history (order_id, status, notes)
        VALUES (p_order_id, p_status, 'Tracking updated via ' || p_courier_name);
    END IF;
END;
$$;


ALTER FUNCTION "public"."update_order_tracking"("p_order_id" "uuid", "p_tracking_number" "text", "p_courier_name" "text", "p_status" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_product_inventory"("p_product_id" "uuid", "p_stock_count" integer, "p_in_stock" boolean, "p_is_active" boolean) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.products
    SET stock_count = p_stock_count,
        in_stock = p_in_stock,
        is_active = p_is_active,
        updated_at = NOW()
    WHERE id = p_product_id
      AND (vendor_id = auth.uid() OR public.get_user_role(auth.uid()) = 'admin');
END;
$$;


ALTER FUNCTION "public"."update_product_inventory"("p_product_id" "uuid", "p_stock_count" integer, "p_in_stock" boolean, "p_is_active" boolean) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_is_active" boolean DEFAULT NULL::boolean, "p_stock_count" integer DEFAULT NULL::integer) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.products
    SET
        is_active = COALESCE(p_is_active, is_active),
        stock_count = COALESCE(p_stock_count, stock_count),
        updated_at = NOW()
    WHERE id = p_product_id;
END;
$$;


ALTER FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_is_active" boolean, "p_stock_count" integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_stock_count" integer DEFAULT NULL::integer, "p_is_active" boolean DEFAULT NULL::boolean) RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE public.products
    SET
        stock_count = COALESCE(p_stock_count, stock_count),
        is_active = COALESCE(p_is_active, is_active),
        updated_at = NOW()
    WHERE id = p_product_id
      AND (vendor_id = auth.uid() OR public.get_user_role(auth.uid()) = 'admin');

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Product not found or unauthorized';
    END IF;
END;
$$;


ALTER FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_stock_count" integer, "p_is_active" boolean) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_product_rating"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
  UPDATE public.products
  SET
    rating = (SELECT AVG(rating) FROM public.reviews WHERE product_id = NEW.product_id),
    reviews_count = (SELECT COUNT(*) FROM public.reviews WHERE product_id = NEW.product_id)
  WHERE id = NEW.product_id;
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."update_product_rating"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_product_stock_status"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.in_stock := (NEW.stock_count > 0);
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."update_product_stock_status"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."update_updated_at_column"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."update_updated_at_column"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."validate_inventory"("p_order_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_item RECORD;
    v_valid boolean := true;
    v_message text := 'Inventory is valid';
BEGIN
    FOR v_item IN
        SELECT oi.product_id, oi.quantity, p.name, p.stock_count, p.in_stock
        FROM public.order_items oi
        JOIN public.products p ON oi.product_id = p.id
        WHERE oi.order_id = p_order_id
    LOOP
        IF NOT v_item.in_stock OR v_item.stock_count < v_item.quantity THEN
            v_valid := false;
            v_message := 'Product ' || v_item.name || ' is out of stock or has insufficient quantity.';
            EXIT;
        END IF;
    END LOOP;

    RETURN jsonb_build_object(
        'valid', v_valid,
        'message', v_message
    );
END;
$$;


ALTER FUNCTION "public"."validate_inventory"("p_order_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."validate_inventory_v2"("p_order_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
DECLARE
    v_item RECORD;
    v_all_valid boolean := true;
    v_error_msg text := '';
BEGIN
    FOR v_item IN
        SELECT oi.product_id, oi.quantity, p.name, p.stock_count, p.in_stock
        FROM public.order_items oi
        JOIN public.products p ON oi.product_id = p.id
        WHERE oi.order_id = p_order_id
    LOOP
        IF NOT v_item.in_stock OR v_item.stock_count < v_item.quantity THEN
            v_all_valid := false;
            v_error_msg := v_item.name || ' is out of stock or has insufficient quantity.';
            EXIT;
        END IF;
    END LOOP;

    RETURN jsonb_build_object(
        'valid', v_all_valid,
        'message', COALESCE(v_error_msg, 'Inventory validated successfully')
    );
END;
$$;


ALTER FUNCTION "public"."validate_inventory_v2"("p_order_id" "uuid") OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."products" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "vendor_id" "uuid",
    "category_id" "uuid",
    "name" "text" NOT NULL,
    "price_kes" integer NOT NULL,
    "stock_count" integer DEFAULT 0,
    "description" "text",
    "featured" boolean DEFAULT false,
    "images" "text"[] DEFAULT '{}'::"text"[],
    "available_sizes" "text"[] DEFAULT '{XS,S,M,L,XL,XXL}'::"text"[],
    "available_colors" "jsonb" DEFAULT '[]'::"jsonb",
    "tag" "text",
    "in_stock" boolean DEFAULT true,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "rating" numeric DEFAULT 5.0,
    "reviews_count" integer DEFAULT 0,
    "is_active" boolean DEFAULT true,
    "category" "text",
    "gender" "text",
    "last_stock_update" timestamp with time zone DEFAULT "now"(),
    "cost_price_kes" integer DEFAULT 0,
    "sub_category" "text",
    "material" "text" DEFAULT 'High-quality fabric'::"text",
    "features" "text"[] DEFAULT '{}'::"text"[],
    "measurement_guide" "jsonb" DEFAULT '{}'::"jsonb",
    "visual_tags" "text"[] DEFAULT '{}'::"text"[],
    "flash_sale_end" timestamp with time zone,
    "flash_sale_price" numeric
);

ALTER TABLE ONLY "public"."products" REPLICA IDENTITY FULL;


ALTER TABLE "public"."products" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."visual_search_products"("p_visual_tags" "text"[]) RETURNS SETOF "public"."products"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM public.products
    WHERE visual_tags && p_visual_tags
    AND is_active = true
    ORDER BY (
        SELECT count(*)
        FROM unnest(visual_tags) v
        WHERE v = ANY(p_visual_tags)
    ) DESC
    LIMIT 20;
END;
$$;


ALTER FUNCTION "public"."visual_search_products"("p_visual_tags" "text"[]) OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."addresses" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "name" "text" NOT NULL,
    "address_line" "text" NOT NULL,
    "city" "text",
    "is_default" boolean DEFAULT false,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."addresses" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."order_items" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "order_id" "uuid",
    "product_id" "uuid",
    "vendor_id" "uuid",
    "quantity" integer DEFAULT 1,
    "price_at_purchase" numeric NOT NULL,
    "size" "text",
    "color" "jsonb",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "unit_price" numeric DEFAULT 0,
    "embroidery_name" "text",
    "has_embroidery" boolean DEFAULT false,
    "status" "text" DEFAULT 'pending'::"text",
    "delivery_fee" numeric DEFAULT 0,
    "fulfillment_data" "jsonb" DEFAULT '{}'::"jsonb",
    CONSTRAINT "order_items_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'processing'::"text", 'shipped'::"text", 'delivered'::"text", 'cancelled'::"text", 'returned'::"text"])))
);


ALTER TABLE "public"."order_items" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."orders" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "total_amount" numeric NOT NULL,
    "discount_amount" numeric DEFAULT 0,
    "final_amount" numeric DEFAULT 0,
    "shipping_address" "text",
    "status" "text" DEFAULT 'Pending'::"text",
    "payment_status" "text" DEFAULT 'unpaid'::"text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "vendor_id" "uuid",
    "coupon_id" "uuid",
    "payment_method" "text",
    "shipping_method" "text" DEFAULT 'Standard'::"text",
    "shipping_cost" integer DEFAULT 0,
    "tax_amount" integer DEFAULT 0,
    "currency" "text" DEFAULT 'KES'::"text",
    "coupon_code" "text",
    "digital_receipt_enabled" boolean DEFAULT true,
    "tracking_number" "text",
    "courier_name" "text",
    "last_known_location" "text",
    "estimated_arrival" timestamp with time zone,
    "is_fitting_service" boolean DEFAULT false,
    "fitting_status" "text",
    "fitting_scheduled_at" timestamp with time zone,
    "fitting_notes" "text",
    CONSTRAINT "orders_payment_status_check" CHECK (("payment_status" = ANY (ARRAY['unpaid'::"text", 'paid'::"text", 'failed'::"text"]))),
    CONSTRAINT "orders_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'processing'::"text", 'shipped'::"text", 'delivered'::"text", 'cancelled'::"text", 'paid'::"text", 'failed'::"text"])))
);


ALTER TABLE "public"."orders" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."payments" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "order_id" "uuid",
    "checkout_request_id" "text",
    "amount" numeric NOT NULL,
    "phone_number" "text",
    "status" "text" DEFAULT 'pending'::"text",
    "mpesa_receipt_number" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "transaction_id" "text",
    "payment_method" "text" DEFAULT 'mpesa'::"text",
    "currency" "text" DEFAULT 'KES'::"text",
    "provider_response" "jsonb"
);


ALTER TABLE "public"."payments" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."profiles" (
    "id" "uuid" NOT NULL,
    "full_name" "text",
    "email" "text",
    "phone_number" "text",
    "role" "text" DEFAULT 'student'::"text",
    "status" "text" DEFAULT 'active'::"text",
    "status_notes" "text",
    "institution" "text",
    "business_name" "text",
    "business_description" "text",
    "location" "text",
    "avatar_url" "text",
    "bio" "text",
    "commission_rate" numeric DEFAULT 10.0,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "rating" numeric DEFAULT 5.0,
    "reviews_count" integer DEFAULT 0,
    "is_verified_vendor" boolean DEFAULT false,
    "total_sales_count" integer DEFAULT 0,
    "rejection_reason" "text",
    "measurements" "jsonb" DEFAULT '{"bust": "0\"", "hips": "0\"", "waist": "0\""}'::"jsonb",
    "biometric_enabled" boolean DEFAULT false,
    "notifications_enabled" boolean DEFAULT true,
    "business_license_url" "text",
    "document_status" "text" DEFAULT 'pending'::"text",
    "session_start" timestamp with time zone,
    "last_sign_out_at" timestamp with time zone,
    "paystack_recipient_code" "text",
    "bank_code" "text",
    "bank_account_number" "text",
    "fcm_token" "text",
    "loyalty_points" integer DEFAULT 0,
    "loyalty_tier" "text" DEFAULT 'bronze'::"text",
    "base_shipping_fee" numeric DEFAULT 150,
    "free_shipping_threshold" numeric DEFAULT 5000,
    "shipping_tier" "text" DEFAULT 'standard'::"text",
    "referral_code" "text" DEFAULT SUBSTRING("md5"(("random"())::"text") FROM 1 FOR 8),
    "referred_by" "uuid",
    "referred_by_id" "uuid",
    "last_login" timestamp with time zone,
    "address" "text",
    CONSTRAINT "profiles_loyalty_tier_check" CHECK (("loyalty_tier" = ANY (ARRAY['bronze'::"text", 'silver'::"text", 'gold'::"text"]))),
    CONSTRAINT "profiles_role_check" CHECK (("role" = ANY (ARRAY['student'::"text", 'professional'::"text", 'nurse'::"text", 'vendor'::"text", 'admin'::"text"]))),
    CONSTRAINT "profiles_status_check" CHECK (("status" = ANY (ARRAY['active'::"text", 'pending'::"text", 'suspended'::"text", 'rejected'::"text", 'banned'::"text"])))
);


ALTER TABLE "public"."profiles" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."admin_detailed_sales_report" AS
 SELECT "o"."id" AS "order_id",
    "o"."created_at" AS "order_date",
    "p"."full_name" AS "customer_name",
    "v"."business_name" AS "vendor_name",
    "pr"."name" AS "product_name",
    "oi"."quantity",
    "oi"."unit_price",
    "o"."total_amount",
    "o"."status",
    "pm"."status" AS "payment_status",
    "pm"."transaction_id" AS "payment_reference",
    "pm"."payment_method",
    ((("oi"."unit_price" * ("oi"."quantity")::numeric) * (COALESCE("v"."commission_rate", 10.0) / 100.0)))::numeric(10,2) AS "commission_earned"
   FROM ((((("public"."orders" "o"
     JOIN "public"."order_items" "oi" ON (("o"."id" = "oi"."order_id")))
     JOIN "public"."products" "pr" ON (("oi"."product_id" = "pr"."id")))
     JOIN "public"."profiles" "p" ON (("o"."user_id" = "p"."id")))
     LEFT JOIN "public"."profiles" "v" ON (("o"."vendor_id" = "v"."id")))
     LEFT JOIN "public"."payments" "pm" ON (("o"."id" = "pm"."order_id")));


ALTER VIEW "public"."admin_detailed_sales_report" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."admin_financial_summary" AS
 SELECT "v"."id" AS "vendor_id",
    "v"."business_name",
    COALESCE("sum"("o"."final_amount"), (0)::numeric) AS "total_revenue",
    COALESCE("sum"(("o"."final_amount" * ("v"."commission_rate" / 100.0))), (0)::numeric) AS "platform_commission",
    COALESCE("sum"(("o"."final_amount" * ((1)::numeric - ("v"."commission_rate" / 100.0)))), (0)::numeric) AS "vendor_net_share"
   FROM ("public"."profiles" "v"
     LEFT JOIN "public"."orders" "o" ON ((("v"."id" = "o"."vendor_id") AND ("o"."status" = 'delivered'::"text"))))
  WHERE ("v"."role" = 'vendor'::"text")
  GROUP BY "v"."id", "v"."business_name", "v"."commission_rate";


ALTER VIEW "public"."admin_financial_summary" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."admin_master_transaction_log" AS
 SELECT "oi"."id" AS "transaction_id",
    "o"."id" AS "order_id",
    "o"."created_at",
    "p"."full_name" AS "customer_name",
    "v"."business_name" AS "vendor_name",
    "pr"."name" AS "product_name",
    "oi"."quantity",
    "oi"."unit_price",
    (("oi"."quantity")::numeric * "oi"."unit_price") AS "gross_merchandise_value",
    "oi"."delivery_fee" AS "shipping_revenue",
    ((("oi"."quantity")::numeric * "oi"."unit_price") * (COALESCE("v"."commission_rate", 10.0) / 100.0)) AS "platform_commission",
    ((("oi"."quantity")::numeric * "oi"."unit_price") * ((1)::numeric - (COALESCE("v"."commission_rate", 10.0) / 100.0))) AS "vendor_payout",
    "oi"."status" AS "fulfillment_status",
    "o"."status" AS "order_status"
   FROM (((("public"."order_items" "oi"
     JOIN "public"."orders" "o" ON (("oi"."order_id" = "o"."id")))
     JOIN "public"."profiles" "p" ON (("o"."user_id" = "p"."id")))
     LEFT JOIN "public"."profiles" "v" ON (("oi"."vendor_id" = "v"."id")))
     JOIN "public"."products" "pr" ON (("oi"."product_id" = "pr"."id")));


ALTER VIEW "public"."admin_master_transaction_log" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."audit_logs" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "action" "text" NOT NULL,
    "details" "jsonb",
    "severity" "text" DEFAULT 'info'::"text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."audit_logs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."banners" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "title" "text",
    "subtitle" "text",
    "image_url" "text",
    "action_link" "text",
    "active" boolean DEFAULT true,
    "sort_order" integer DEFAULT 0,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "vendor_id" "uuid",
    "status" "text" DEFAULT 'pending'::"text",
    "rejection_notes" "text",
    "vendor_name" "text",
    CONSTRAINT "banners_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'approved'::"text", 'rejected'::"text"])))
);


ALTER TABLE "public"."banners" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."bundle_items" (
    "bundle_id" "uuid" NOT NULL,
    "product_id" "uuid" NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."bundle_items" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."bundles" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "description" "text",
    "discount_percent" numeric NOT NULL,
    "is_active" boolean DEFAULT true,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "bundles_discount_percent_check" CHECK ((("discount_percent" > (0)::numeric) AND ("discount_percent" <= (100)::numeric)))
);


ALTER TABLE "public"."bundles" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."cart_items" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "product_id" "uuid" NOT NULL,
    "quantity" integer DEFAULT 1 NOT NULL,
    "size" "text" NOT NULL,
    "color_name" "text",
    "color_hex" bigint,
    "embroidery_name" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "cart_items_quantity_check" CHECK (("quantity" > 0))
);


ALTER TABLE "public"."cart_items" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."categories" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "description" "text",
    "icon_name" "text",
    "is_active" boolean DEFAULT true,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."categories" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."catalog_products" WITH ("security_invoker"='true') AS
 SELECT "p"."id",
    "p"."vendor_id",
    "p"."category_id",
    "p"."name",
    "p"."price_kes",
    "p"."stock_count",
    "p"."description",
    "p"."featured",
    "p"."images",
    "p"."available_sizes",
    "p"."available_colors",
    "p"."tag",
    "p"."in_stock",
    "p"."created_at",
    "p"."updated_at",
    "p"."rating",
    "p"."reviews_count",
    "p"."is_active",
    "p"."category",
    "p"."gender",
    "p"."last_stock_update",
    "p"."cost_price_kes",
    "p"."sub_category",
    "p"."material",
    "p"."features",
    "p"."measurement_guide",
    "p"."visual_tags",
    "p"."flash_sale_end",
    "p"."flash_sale_price",
    "c"."name" AS "category_name",
    "prof"."full_name" AS "vendor_name",
    "prof"."business_name" AS "vendor_business_name",
    "prof"."rating" AS "vendor_rating",
    "prof"."avatar_url" AS "vendor_avatar"
   FROM (("public"."products" "p"
     JOIN "public"."profiles" "prof" ON (("p"."vendor_id" = "prof"."id")))
     LEFT JOIN "public"."categories" "c" ON (("p"."category_id" = "c"."id")))
  WHERE (("prof"."status" = 'active'::"text") AND ("p"."is_active" = true));


ALTER VIEW "public"."catalog_products" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."coupons" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "code" "text" NOT NULL,
    "discount_percent" integer,
    "expiry_date" timestamp with time zone,
    "usage_limit" integer DEFAULT 100,
    "active" boolean DEFAULT true,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "min_spend_kes" numeric DEFAULT 0,
    "start_date" timestamp with time zone DEFAULT "now"(),
    "end_date" timestamp with time zone,
    "usage_count" integer DEFAULT 0,
    "vendor_id" "uuid",
    "description" "text",
    CONSTRAINT "coupons_discount_percent_check" CHECK ((("discount_percent" > 0) AND ("discount_percent" <= 100)))
);


ALTER TABLE "public"."coupons" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."coupon_performance" AS
 SELECT "c"."code",
    "c"."discount_percent",
    "count"("o"."id") AS "usage_count",
    "sum"("o"."final_amount") AS "total_sales_with_coupon"
   FROM ("public"."coupons" "c"
     LEFT JOIN "public"."orders" "o" ON (("o"."coupon_code" = "c"."code")))
  GROUP BY "c"."code", "c"."discount_percent";


ALTER VIEW "public"."coupon_performance" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."coupon_usage_stats" AS
 SELECT "id",
    "code",
    "description",
    "usage_count",
    "usage_limit",
        CASE
            WHEN ("usage_limit" > 0) THEN "round"(((("usage_count")::double precision / ("usage_limit")::double precision) * (100)::double precision))
            ELSE (0)::double precision
        END AS "usage_percent",
    "active",
    "start_date",
    "end_date",
    "min_spend_kes",
    "vendor_id"
   FROM "public"."coupons";


ALTER VIEW "public"."coupon_usage_stats" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."favorites" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "product_id" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."favorites" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."fitting_appointments" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "order_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "vendor_id" "uuid" NOT NULL,
    "scheduled_at" timestamp with time zone NOT NULL,
    "status" "text" DEFAULT 'pending'::"text" NOT NULL,
    "address_id" "uuid",
    "special_instructions" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "fitting_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'confirmed'::"text", 'completed'::"text", 'cancelled'::"text"])))
);


ALTER TABLE "public"."fitting_appointments" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."flash_sale_items" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "flash_sale_id" "uuid" NOT NULL,
    "product_id" "uuid" NOT NULL,
    "discount_override" numeric,
    "stock_limit" integer,
    "sold_count" integer DEFAULT 0,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."flash_sale_items" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."flash_sales" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "start_time" timestamp with time zone NOT NULL,
    "end_time" timestamp with time zone NOT NULL,
    "discount_percent" numeric NOT NULL,
    "is_active" boolean DEFAULT true,
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."flash_sales" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."generated_reports" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "report_type" "text" NOT NULL,
    "format" "text" NOT NULL,
    "generated_by" "uuid",
    "start_date" timestamp with time zone,
    "end_date" timestamp with time zone,
    "storage_path" "text",
    "metadata" "jsonb" DEFAULT '{}'::"jsonb",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."generated_reports" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."logistics_webhook_logs" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "order_id" "uuid",
    "partner_name" "text",
    "raw_payload" "jsonb",
    "processed_status" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."logistics_webhook_logs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."loyalty_points" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "points" integer DEFAULT 0,
    "action_type" "text",
    "order_id" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."loyalty_points" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."loyalty_tiers" (
    "tier_name" "text" NOT NULL,
    "min_points" integer NOT NULL,
    "discount_percent" numeric DEFAULT 0,
    "benefits" "jsonb" DEFAULT '[]'::"jsonb",
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."loyalty_tiers" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."messageable_contacts" AS
 SELECT "id",
    "full_name",
    "email",
    "phone_number",
    "avatar_url",
    "role",
    "status"
   FROM "public"."profiles"
  WHERE (("id" <> "auth"."uid"()) AND ((( SELECT "profiles_1"."role"
           FROM "public"."profiles" "profiles_1"
          WHERE ("profiles_1"."id" = "auth"."uid"())) = 'admin'::"text") OR ("role" = ANY (ARRAY['admin'::"text", 'vendor'::"text"]))));


ALTER VIEW "public"."messageable_contacts" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."messages" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "sender_id" "uuid" NOT NULL,
    "receiver_id" "uuid",
    "message" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "is_read" boolean DEFAULT false,
    "category" "text" DEFAULT 'direct'::"text",
    "priority" "text" DEFAULT 'normal'::"text",
    "metadata" "jsonb" DEFAULT '{}'::"jsonb",
    "is_archived_by_sender" boolean DEFAULT false,
    "is_archived_by_receiver" boolean DEFAULT false,
    "image_url" "text",
    "is_delivered" boolean DEFAULT false,
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "messages_category_check" CHECK (("category" = ANY (ARRAY['direct'::"text", 'group'::"text", 'system'::"text", 'order_update'::"text"]))),
    CONSTRAINT "messages_priority_check" CHECK (("priority" = ANY (ARRAY['low'::"text", 'normal'::"text", 'urgent'::"text", 'critical'::"text"])))
);


ALTER TABLE "public"."messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."notifications" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "title" "text" NOT NULL,
    "message" "text" NOT NULL,
    "is_read" boolean DEFAULT false,
    "type" "text" DEFAULT 'info'::"text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "is_archived" boolean DEFAULT false,
    "priority_level" "text" DEFAULT 'low'::"text",
    "body" "text" NOT NULL,
    "category" "text" DEFAULT 'general'::"text"
);


ALTER TABLE "public"."notifications" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."order_receipt_view" AS
 SELECT "o"."id" AS "order_id",
    "o"."user_id",
    "o"."created_at",
    "o"."total_amount",
    "o"."status" AS "order_status",
    "p"."status" AS "payment_status",
    "p"."mpesa_receipt_number",
    "p"."phone_number",
    "u"."full_name" AS "customer_name",
    "u"."email" AS "customer_email"
   FROM (("public"."orders" "o"
     JOIN "public"."profiles" "u" ON (("o"."user_id" = "u"."id")))
     LEFT JOIN "public"."payments" "p" ON (("o"."id" = "p"."order_id")));


ALTER VIEW "public"."order_receipt_view" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."order_status_history" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "order_id" "uuid",
    "status" "text" NOT NULL,
    "changed_by" "uuid",
    "notes" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."order_status_history" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."password_resets" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "email" "text" NOT NULL,
    "code" character varying(4) NOT NULL,
    "expires_at" timestamp with time zone NOT NULL,
    "is_used" boolean DEFAULT false,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."password_resets" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."payouts" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "vendor_id" "uuid",
    "amount" integer NOT NULL,
    "status" "text" DEFAULT 'pending'::"text",
    "reference_number" "text",
    "processed_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "paid_at" timestamp with time zone,
    "reference" "text",
    "metadata" "jsonb" DEFAULT '{}'::"jsonb",
    CONSTRAINT "payouts_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'paid'::"text", 'failed'::"text"])))
);


ALTER TABLE "public"."payouts" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."pending_vendors" AS
 SELECT "id",
    "full_name",
    "email",
    "phone_number",
    "business_name",
    "location",
    "business_description",
    "status",
    "status_notes",
    "rejection_reason",
    "created_at",
    "business_license_url",
    "document_status"
   FROM "public"."profiles"
  WHERE (("role" = 'vendor'::"text") AND (("status" = 'pending'::"text") OR ("status" = 'rejected'::"text")));


ALTER VIEW "public"."pending_vendors" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."product_waitlist" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "product_id" "uuid" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "notified" boolean DEFAULT false,
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."product_waitlist" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."return_requests" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "order_id" "uuid" NOT NULL,
    "user_id" "uuid" NOT NULL,
    "reason" "text" NOT NULL,
    "status" "text" DEFAULT 'pending'::"text" NOT NULL,
    "admin_notes" "text",
    "images" "text"[] DEFAULT '{}'::"text"[],
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "return_requests_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'approved'::"text", 'rejected'::"text", 'item_received'::"text", 'refunded'::"text"])))
);


ALTER TABLE "public"."return_requests" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."reviews" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "product_id" "uuid",
    "user_id" "uuid",
    "rating" integer,
    "comment" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "images" "text"[] DEFAULT '{}'::"text"[],
    CONSTRAINT "reviews_rating_check" CHECK ((("rating" >= 1) AND ("rating" <= 5)))
);


ALTER TABLE "public"."reviews" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."search_index_queue" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "entity_type" "text" NOT NULL,
    "entity_id" "uuid" NOT NULL,
    "action" "text" NOT NULL,
    "processed" boolean DEFAULT false,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."search_index_queue" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."stock_alerts" AS
 SELECT "id" AS "product_id",
    "name" AS "product_name",
    "vendor_id",
    "stock_count",
    "category",
        CASE
            WHEN ("stock_count" = 0) THEN 'critical'::"text"
            WHEN ("stock_count" <= 5) THEN 'warning'::"text"
            ELSE 'info'::"text"
        END AS "alert_level"
   FROM "public"."products" "p"
  WHERE (("stock_count" <= 5) AND ("is_active" = true));


ALTER VIEW "public"."stock_alerts" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."student_catalog_view" AS
 SELECT "p"."id",
    "p"."name",
    "p"."description",
    "p"."price_kes",
    "p"."stock_count",
    "p"."in_stock",
    "p"."category",
    "p"."images",
    "p"."rating",
    "p"."reviews_count",
    "p"."vendor_id",
    "v"."business_name" AS "vendor_name",
    "v"."is_verified_vendor" AS "vendor_verified",
    "v"."status" AS "vendor_status"
   FROM ("public"."products" "p"
     JOIN "public"."profiles" "v" ON (("p"."vendor_id" = "v"."id")))
  WHERE (("p"."is_active" = true) AND ("v"."status" = 'active'::"text") AND ("p"."stock_count" > 0));


ALTER VIEW "public"."student_catalog_view" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."subscriptions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "product_id" "uuid" NOT NULL,
    "quantity" integer DEFAULT 1,
    "size" "text",
    "color" "text",
    "frequency_days" integer DEFAULT 30,
    "status" "text" DEFAULT 'active'::"text",
    "next_delivery_date" timestamp with time zone DEFAULT ("now"() + '30 days'::interval),
    "shipping_address_id" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "subscriptions_quantity_check" CHECK (("quantity" > 0)),
    CONSTRAINT "subscriptions_status_check" CHECK (("status" = ANY (ARRAY['active'::"text", 'paused'::"text", 'cancelled'::"text"])))
);


ALTER TABLE "public"."subscriptions" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."system_logs" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "action" "text" NOT NULL,
    "details" "jsonb",
    "severity" "text" DEFAULT 'info'::"text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "system_logs_severity_check" CHECK (("severity" = ANY (ARRAY['info'::"text", 'warning'::"text", 'error'::"text", 'critical'::"text"])))
);

ALTER TABLE ONLY "public"."system_logs" REPLICA IDENTITY FULL;


ALTER TABLE "public"."system_logs" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."system_settings" (
    "key" "text" NOT NULL,
    "value" "text" NOT NULL,
    "description" "text",
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."system_settings" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."user_conversations" AS
 WITH "last_messages" AS (
         SELECT "m"."id",
            "m"."sender_id",
            "m"."receiver_id",
            "m"."message",
            "m"."created_at",
            "m"."is_read",
            "m"."category",
            "m"."priority",
            "m"."metadata",
            "m"."is_archived_by_sender",
            "m"."is_archived_by_receiver",
            "m"."image_url",
            "m"."is_delivered",
            "row_number"() OVER (PARTITION BY LEAST("m"."sender_id", "m"."receiver_id"), GREATEST("m"."sender_id", "m"."receiver_id") ORDER BY "m"."created_at" DESC) AS "rn"
           FROM "public"."messages" "m"
        ), "latest_chats" AS (
         SELECT "last_messages"."id",
            "last_messages"."sender_id",
            "last_messages"."receiver_id",
            "last_messages"."message",
            "last_messages"."created_at",
            "last_messages"."is_read",
            "last_messages"."category",
            "last_messages"."priority",
            "last_messages"."metadata",
            "last_messages"."is_archived_by_sender",
            "last_messages"."is_archived_by_receiver",
            "last_messages"."image_url",
            "last_messages"."is_delivered",
            "last_messages"."rn"
           FROM "last_messages"
          WHERE ("last_messages"."rn" = 1)
        ), "unread_counts" AS (
         SELECT "messages"."sender_id",
            "messages"."receiver_id",
            "count"(*) AS "count"
           FROM "public"."messages"
          WHERE ("messages"."is_read" = false)
          GROUP BY "messages"."sender_id", "messages"."receiver_id"
        )
 SELECT "lc"."id" AS "last_message_id",
        CASE
            WHEN ("auth"."uid"() = "lc"."sender_id") THEN "lc"."receiver_id"
            ELSE "lc"."sender_id"
        END AS "other_user_id",
    "p"."full_name" AS "other_user_name",
    "p"."avatar_url" AS "other_user_avatar",
    "p"."email" AS "other_user_email",
    "p"."phone_number" AS "other_user_phone",
    "lc"."message" AS "last_message",
    "lc"."created_at" AS "last_message_time",
    "lc"."priority" AS "last_message_priority",
    (COALESCE(( SELECT "unread_counts"."count"
           FROM "unread_counts"
          WHERE (("unread_counts"."sender_id" = "p"."id") AND ("unread_counts"."receiver_id" = "auth"."uid"()))), (0)::bigint))::integer AS "unread_count"
   FROM ("latest_chats" "lc"
     JOIN "public"."profiles" "p" ON (("p"."id" =
        CASE
            WHEN ("auth"."uid"() = "lc"."sender_id") THEN "lc"."receiver_id"
            ELSE "lc"."sender_id"
        END)))
  WHERE (("auth"."uid"() = "lc"."sender_id") OR ("auth"."uid"() = "lc"."receiver_id"));


ALTER VIEW "public"."user_conversations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."user_sessions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "device_name" "text",
    "location" "text",
    "ip_address" "text",
    "last_active" timestamp with time zone DEFAULT "now"(),
    "is_current" boolean DEFAULT false,
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."user_sessions" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."v_loyalty_history" AS
 SELECT "lp"."id",
    "lp"."user_id",
    "lp"."points",
    "lp"."action_type",
    "lp"."order_id",
    "lp"."created_at",
    "o"."total_amount" AS "order_amount",
    "o"."status" AS "order_status"
   FROM ("public"."loyalty_points" "lp"
     LEFT JOIN "public"."orders" "o" ON (("lp"."order_id" = "o"."id")))
  ORDER BY "lp"."created_at" DESC;


ALTER VIEW "public"."v_loyalty_history" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."v_order_tracking_timeline" AS
 SELECT "osh"."id",
    "osh"."order_id",
    "osh"."status",
    "osh"."notes",
    "osh"."created_at",
    "p"."full_name" AS "updated_by_name",
    "p"."role" AS "updated_by_role"
   FROM ("public"."order_status_history" "osh"
     JOIN "public"."profiles" "p" ON (("osh"."changed_by" = "p"."id")))
  ORDER BY "osh"."created_at" DESC;


ALTER VIEW "public"."v_order_tracking_timeline" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."v_vendor_performance" AS
 SELECT "p"."id" AS "vendor_id",
    "p"."full_name" AS "vendor_name",
    "p"."business_name",
    "count"(DISTINCT "oi"."order_id") AS "total_orders",
    "sum"((("oi"."quantity")::numeric * "oi"."unit_price")) AS "gross_sales",
    "avg"("r"."rating") AS "average_rating",
    "count"("r"."id") AS "review_count"
   FROM (("public"."profiles" "p"
     LEFT JOIN "public"."order_items" "oi" ON (("p"."id" = ( SELECT "products"."vendor_id"
           FROM "public"."products"
          WHERE ("products"."id" = "oi"."product_id")))))
     LEFT JOIN "public"."reviews" "r" ON (("p"."id" = ( SELECT "products"."vendor_id"
           FROM "public"."products"
          WHERE ("products"."id" = "r"."product_id")))))
  WHERE ("p"."role" = 'vendor'::"text")
  GROUP BY "p"."id", "p"."full_name", "p"."business_name";


ALTER VIEW "public"."v_vendor_performance" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."vendor_order_performance" AS
 SELECT "v"."id" AS "vendor_id",
    "count"("oi"."id") AS "total_items_ordered",
    "count"("oi"."id") FILTER (WHERE ("oi"."status" = 'pending'::"text")) AS "pending_fulfillment",
    "count"("oi"."id") FILTER (WHERE ("oi"."status" = 'delivered'::"text")) AS "completed_payouts",
    "sum"((("oi"."quantity")::numeric * "oi"."unit_price")) AS "gross_sales",
    "sum"(((("oi"."quantity")::numeric * "oi"."unit_price") * ((1)::numeric - (COALESCE("v"."commission_rate", 10.0) / 100.0)))) AS "net_earnings"
   FROM ("public"."profiles" "v"
     LEFT JOIN "public"."order_items" "oi" ON (("v"."id" = "oi"."vendor_id")))
  WHERE ("v"."role" = 'vendor'::"text")
  GROUP BY "v"."id";


ALTER VIEW "public"."vendor_order_performance" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."vendor_payouts" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "vendor_id" "uuid",
    "order_id" "uuid",
    "gross_amount" numeric(12,2) NOT NULL,
    "commission_amount" numeric(12,2) NOT NULL,
    "net_amount" numeric(12,2) NOT NULL,
    "status" "text" DEFAULT 'pending'::"text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "vendor_payouts_status_check" CHECK (("status" = ANY (ARRAY['pending'::"text", 'paid'::"text", 'failed'::"text"])))
);


ALTER TABLE "public"."vendor_payouts" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."vendor_rankings" AS
 SELECT "v"."id" AS "vendor_id",
    "v"."full_name",
    "v"."business_name",
    "count"(DISTINCT "o"."id") AS "total_orders",
    "sum"("o"."final_amount") AS "total_revenue",
    "avg"("r"."rating") AS "avg_rating"
   FROM (((("public"."profiles" "v"
     JOIN "public"."products" "p" ON (("p"."vendor_id" = "v"."id")))
     JOIN "public"."order_items" "oi" ON (("oi"."product_id" = "p"."id")))
     JOIN "public"."orders" "o" ON (("o"."id" = "oi"."order_id")))
     LEFT JOIN "public"."reviews" "r" ON (("r"."product_id" = "p"."id")))
  WHERE (("v"."role" = 'vendor'::"text") AND ("o"."status" = 'delivered'::"text"))
  GROUP BY "v"."id", "v"."full_name", "v"."business_name";


ALTER VIEW "public"."vendor_rankings" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."vendor_ratings" AS
 SELECT "p"."vendor_id",
    ("avg"("r"."rating"))::numeric(3,2) AS "avg_rating",
    "count"("r"."id") AS "total_reviews"
   FROM ("public"."reviews" "r"
     JOIN "public"."products" "p" ON (("r"."product_id" = "p"."id")))
  GROUP BY "p"."vendor_id";


ALTER VIEW "public"."vendor_ratings" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."vendor_stats" AS
 SELECT "vendor_id",
    "count"("id") AS "total_products",
    "sum"(
        CASE
            WHEN "in_stock" THEN 1
            ELSE 0
        END) AS "in_stock_count",
    "sum"("reviews_count") AS "total_product_reviews",
    ( SELECT "count"(*) AS "count"
           FROM ("public"."order_items" "oi"
             JOIN "public"."products" "p2" ON (("oi"."product_id" = "p2"."id")))
          WHERE ("p2"."vendor_id" = "p"."vendor_id")) AS "total_items_sold"
   FROM "public"."products" "p"
  GROUP BY "vendor_id";


ALTER VIEW "public"."vendor_stats" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."wallet_transactions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "wallet_id" "uuid",
    "amount" numeric(12,2) NOT NULL,
    "type" "text",
    "reference_type" "text",
    "reference_id" "uuid",
    "description" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "wallet_transactions_type_check" CHECK (("type" = ANY (ARRAY['credit'::"text", 'debit'::"text"])))
);


ALTER TABLE "public"."wallet_transactions" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."wallets" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "balance" numeric DEFAULT 0.0,
    "currency" "text" DEFAULT 'KES'::"text",
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "wallets_balance_check" CHECK (("balance" >= (0)::numeric))
);


ALTER TABLE "public"."wallets" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."wishlist" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid",
    "product_id" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."wishlist" OWNER TO "postgres";


ALTER TABLE ONLY "public"."addresses"
    ADD CONSTRAINT "addresses_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."audit_logs"
    ADD CONSTRAINT "audit_logs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."banners"
    ADD CONSTRAINT "banners_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."bundle_items"
    ADD CONSTRAINT "bundle_items_pkey" PRIMARY KEY ("bundle_id", "product_id");



ALTER TABLE ONLY "public"."bundles"
    ADD CONSTRAINT "bundles_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."cart_items"
    ADD CONSTRAINT "cart_items_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."cart_items"
    ADD CONSTRAINT "cart_items_user_id_product_id_size_color_name_embroidery_na_key" UNIQUE ("user_id", "product_id", "size", "color_name", "embroidery_name");



ALTER TABLE ONLY "public"."categories"
    ADD CONSTRAINT "categories_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."categories"
    ADD CONSTRAINT "categories_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."coupons"
    ADD CONSTRAINT "coupons_code_key" UNIQUE ("code");



ALTER TABLE ONLY "public"."coupons"
    ADD CONSTRAINT "coupons_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."favorites"
    ADD CONSTRAINT "favorites_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."favorites"
    ADD CONSTRAINT "favorites_user_id_product_id_key" UNIQUE ("user_id", "product_id");



ALTER TABLE ONLY "public"."fitting_appointments"
    ADD CONSTRAINT "fitting_appointments_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."flash_sale_items"
    ADD CONSTRAINT "flash_sale_items_flash_sale_id_product_id_key" UNIQUE ("flash_sale_id", "product_id");



ALTER TABLE ONLY "public"."flash_sale_items"
    ADD CONSTRAINT "flash_sale_items_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."flash_sales"
    ADD CONSTRAINT "flash_sales_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."generated_reports"
    ADD CONSTRAINT "generated_reports_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."logistics_webhook_logs"
    ADD CONSTRAINT "logistics_webhook_logs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."loyalty_points"
    ADD CONSTRAINT "loyalty_points_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."loyalty_tiers"
    ADD CONSTRAINT "loyalty_tiers_pkey" PRIMARY KEY ("tier_name");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."order_items"
    ADD CONSTRAINT "order_items_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."order_status_history"
    ADD CONSTRAINT "order_status_history_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."orders"
    ADD CONSTRAINT "orders_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."password_resets"
    ADD CONSTRAINT "password_resets_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_checkout_request_id_key" UNIQUE ("checkout_request_id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_transaction_id_key" UNIQUE ("transaction_id");



ALTER TABLE ONLY "public"."payouts"
    ADD CONSTRAINT "payouts_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."product_waitlist"
    ADD CONSTRAINT "product_waitlist_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."product_waitlist"
    ADD CONSTRAINT "product_waitlist_user_id_product_id_notified_key" UNIQUE ("user_id", "product_id", "notified");



ALTER TABLE ONLY "public"."products"
    ADD CONSTRAINT "products_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_email_key" UNIQUE ("email");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_referral_code_key" UNIQUE ("referral_code");



ALTER TABLE ONLY "public"."return_requests"
    ADD CONSTRAINT "return_requests_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."reviews"
    ADD CONSTRAINT "reviews_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."reviews"
    ADD CONSTRAINT "reviews_product_id_user_id_key" UNIQUE ("product_id", "user_id");



ALTER TABLE ONLY "public"."search_index_queue"
    ADD CONSTRAINT "search_index_queue_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."subscriptions"
    ADD CONSTRAINT "subscriptions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."system_logs"
    ADD CONSTRAINT "system_logs_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."system_settings"
    ADD CONSTRAINT "system_settings_pkey" PRIMARY KEY ("key");



ALTER TABLE ONLY "public"."user_sessions"
    ADD CONSTRAINT "user_sessions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."vendor_payouts"
    ADD CONSTRAINT "vendor_payouts_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wallet_transactions"
    ADD CONSTRAINT "wallet_transactions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wallets"
    ADD CONSTRAINT "wallets_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wallets"
    ADD CONSTRAINT "wallets_user_id_key" UNIQUE ("user_id");



ALTER TABLE ONLY "public"."wishlist"
    ADD CONSTRAINT "wishlist_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."wishlist"
    ADD CONSTRAINT "wishlist_user_id_product_id_key" UNIQUE ("user_id", "product_id");



CREATE INDEX "idx_messages_conversation" ON "public"."messages" USING "btree" ("sender_id", "receiver_id");



CREATE INDEX "idx_messages_created_at" ON "public"."messages" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_messages_sender_receiver" ON "public"."messages" USING "btree" ("sender_id", "receiver_id");



CREATE INDEX "idx_messages_status_delivered" ON "public"."messages" USING "btree" ("is_delivered") WHERE ("is_delivered" = false);



CREATE INDEX "idx_messages_status_read" ON "public"."messages" USING "btree" ("is_read") WHERE ("is_read" = false);



CREATE INDEX "idx_notifications_user_id" ON "public"."notifications" USING "btree" ("user_id");



CREATE INDEX "idx_notifications_user_id_archived" ON "public"."notifications" USING "btree" ("user_id", "is_archived");



CREATE INDEX "idx_notifications_user_unread" ON "public"."notifications" USING "btree" ("user_id") WHERE ("is_read" = false);



CREATE INDEX "idx_order_items_order" ON "public"."order_items" USING "btree" ("order_id");



CREATE INDEX "idx_order_items_order_id" ON "public"."order_items" USING "btree" ("order_id");



CREATE INDEX "idx_order_items_vendor" ON "public"."order_items" USING "btree" ("vendor_id");



CREATE INDEX "idx_orders_user" ON "public"."orders" USING "btree" ("user_id");



CREATE INDEX "idx_orders_user_id" ON "public"."orders" USING "btree" ("user_id");



CREATE INDEX "idx_orders_vendor_id" ON "public"."orders" USING "btree" ("vendor_id");



CREATE INDEX "idx_password_resets_email_code" ON "public"."password_resets" USING "btree" ("email", "code");



CREATE INDEX "idx_products_category" ON "public"."products" USING "btree" ("category_id");



CREATE INDEX "idx_products_vendor" ON "public"."products" USING "btree" ("vendor_id");



CREATE INDEX "idx_products_vendor_id" ON "public"."products" USING "btree" ("vendor_id");



CREATE INDEX "idx_profiles_fcm_token" ON "public"."profiles" USING "btree" ("fcm_token") WHERE ("fcm_token" IS NOT NULL);



CREATE INDEX "products_search_idx" ON "public"."products" USING "gin" ("to_tsvector"('"english"'::"regconfig", ((((COALESCE("name", ''::"text") || ' '::"text") || COALESCE("category", ''::"text")) || ' '::"text") || COALESCE("description", ''::"text"))));



CREATE OR REPLACE TRIGGER "on_password_reset_created" AFTER INSERT ON "public"."password_resets" FOR EACH ROW EXECUTE FUNCTION "public"."handle_new_password_reset"();



CREATE OR REPLACE TRIGGER "on_profile_update" AFTER UPDATE OF "full_name", "role" ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."sync_profile_to_auth"();



CREATE OR REPLACE TRIGGER "on_profile_updated_trigger" BEFORE UPDATE ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."handle_profile_updated"();



CREATE OR REPLACE TRIGGER "on_review_added" AFTER INSERT OR DELETE OR UPDATE ON "public"."reviews" FOR EACH ROW EXECUTE FUNCTION "public"."sync_ratings_on_review"();



CREATE OR REPLACE TRIGGER "on_vendor_status_change" AFTER UPDATE OF "status" ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."notify_vendor_status_change"();



CREATE OR REPLACE TRIGGER "tr_audit_order_items" AFTER INSERT OR DELETE OR UPDATE ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_audit_trigger"();



CREATE OR REPLACE TRIGGER "tr_audit_orders" AFTER INSERT OR DELETE OR UPDATE ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."audit_log_trigger"();



CREATE OR REPLACE TRIGGER "tr_audit_products" AFTER INSERT OR DELETE OR UPDATE ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."audit_log_trigger"();



CREATE OR REPLACE TRIGGER "tr_award_loyalty_points" AFTER UPDATE OF "status" ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."fn_award_loyalty_points"();



CREATE OR REPLACE TRIGGER "tr_award_referral_bonus" AFTER UPDATE OF "status" ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."fn_award_referral_bonus"();



CREATE OR REPLACE TRIGGER "tr_calculate_item_delivery_fee" BEFORE INSERT ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_calculate_item_delivery_fee"();



CREATE OR REPLACE TRIGGER "tr_calculate_vendor_earnings" AFTER UPDATE OF "status" ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."fn_calculate_vendor_earnings"();



CREATE OR REPLACE TRIGGER "tr_check_return_eligibility" BEFORE INSERT ON "public"."return_requests" FOR EACH ROW EXECUTE FUNCTION "public"."check_return_eligibility"();



CREATE OR REPLACE TRIGGER "tr_check_review_eligibility" BEFORE INSERT ON "public"."reviews" FOR EACH ROW EXECUTE FUNCTION "public"."check_review_eligibility"();



CREATE OR REPLACE TRIGGER "tr_check_stock_levels" AFTER UPDATE OF "stock_count" ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."check_stock_levels"();



CREATE OR REPLACE TRIGGER "tr_check_wallet_balance" BEFORE INSERT ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."fn_check_wallet_balance"();



CREATE OR REPLACE TRIGGER "tr_ensure_default_after_delete" AFTER DELETE ON "public"."addresses" FOR EACH ROW EXECUTE FUNCTION "public"."fn_ensure_default_after_delete"();



CREATE OR REPLACE TRIGGER "tr_generate_referral_code" BEFORE INSERT ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."fn_generate_referral_code"();



CREATE OR REPLACE TRIGGER "tr_handle_banner_resubmission" BEFORE UPDATE ON "public"."banners" FOR EACH ROW EXECUTE FUNCTION "public"."handle_banner_resubmission"();



CREATE OR REPLACE TRIGGER "tr_handle_coupon_usage" AFTER UPDATE OF "status" ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."handle_coupon_usage"();



CREATE OR REPLACE TRIGGER "tr_handle_item_stock_on_cancel" AFTER UPDATE OF "status" ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_handle_item_stock_on_cancel"();



CREATE OR REPLACE TRIGGER "tr_handle_product_update_time" BEFORE UPDATE ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."handle_product_update_time"();



CREATE OR REPLACE TRIGGER "tr_log_address_activity" AFTER INSERT OR DELETE OR UPDATE ON "public"."addresses" FOR EACH ROW EXECUTE FUNCTION "public"."log_address_activity"();



CREATE OR REPLACE TRIGGER "tr_log_coupon_activity" AFTER INSERT OR DELETE OR UPDATE ON "public"."coupons" FOR EACH ROW EXECUTE FUNCTION "public"."log_coupon_activity"();



CREATE OR REPLACE TRIGGER "tr_log_favorite_activity" AFTER INSERT OR DELETE ON "public"."favorites" FOR EACH ROW EXECUTE FUNCTION "public"."log_favorite_activity"();



CREATE OR REPLACE TRIGGER "tr_log_order_activity" AFTER INSERT OR UPDATE ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."log_order_activity"();



CREATE OR REPLACE TRIGGER "tr_log_order_status_history" AFTER INSERT OR UPDATE OF "status" ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."log_order_status_change"();



CREATE OR REPLACE TRIGGER "tr_log_payment_activity" AFTER INSERT OR UPDATE ON "public"."payments" FOR EACH ROW EXECUTE FUNCTION "public"."log_payment_activity"();



CREATE OR REPLACE TRIGGER "tr_log_payout_activity" AFTER INSERT OR UPDATE ON "public"."payouts" FOR EACH ROW EXECUTE FUNCTION "public"."log_payout_activity"();



CREATE OR REPLACE TRIGGER "tr_log_product_activity" AFTER INSERT OR DELETE OR UPDATE ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."log_product_activity"();



CREATE OR REPLACE TRIGGER "tr_log_profile_activity" AFTER UPDATE ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."log_profile_activity"();



CREATE OR REPLACE TRIGGER "tr_log_profile_changes" AFTER UPDATE ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."log_profile_changes"();



CREATE OR REPLACE TRIGGER "tr_log_profile_status_change" AFTER UPDATE OF "status" ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."log_profile_status_change"();



CREATE OR REPLACE TRIGGER "tr_log_report_gen" AFTER INSERT ON "public"."generated_reports" FOR EACH ROW EXECUTE FUNCTION "public"."log_report_generation"();



CREATE OR REPLACE TRIGGER "tr_log_review_activity" AFTER INSERT OR DELETE ON "public"."reviews" FOR EACH ROW EXECUTE FUNCTION "public"."log_review_activity"();



CREATE OR REPLACE TRIGGER "tr_log_user_status_change" AFTER UPDATE OF "status" ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."log_user_status_change"();



CREATE OR REPLACE TRIGGER "tr_loyalty_tier_update" BEFORE UPDATE OF "loyalty_points" ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."update_loyalty_tier"();



CREATE OR REPLACE TRIGGER "tr_manage_default_address" BEFORE INSERT OR UPDATE OF "is_default" ON "public"."addresses" FOR EACH ROW EXECUTE FUNCTION "public"."fn_manage_default_address"();



CREATE OR REPLACE TRIGGER "tr_mpesa_callback_update" AFTER UPDATE OF "status" ON "public"."payments" FOR EACH ROW EXECUTE FUNCTION "public"."handle_mpesa_callback_update"();



CREATE OR REPLACE TRIGGER "tr_new_order_vendor_notification" AFTER INSERT ON "public"."orders" FOR EACH ROW WHEN ((("new"."status" = 'pending'::"text") OR ("new"."status" = 'processing'::"text"))) EXECUTE FUNCTION "public"."on_new_order_notify_vendor"();



CREATE OR REPLACE TRIGGER "tr_notify_fcm_bridge" AFTER INSERT ON "public"."notifications" FOR EACH ROW EXECUTE FUNCTION "public"."notify_fcm_bridge"();



CREATE OR REPLACE TRIGGER "tr_notify_waitlist" AFTER UPDATE OF "stock_count" ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."fn_notify_waitlist"();



CREATE OR REPLACE TRIGGER "tr_on_payment_completed" AFTER UPDATE OF "status" ON "public"."payments" FOR EACH ROW EXECUTE FUNCTION "public"."handle_payment_completion"();



CREATE OR REPLACE TRIGGER "tr_on_payment_success" AFTER INSERT OR UPDATE OF "status" ON "public"."payments" FOR EACH ROW EXECUTE FUNCTION "public"."handle_successful_payment"();



CREATE OR REPLACE TRIGGER "tr_on_vendor_status_change" AFTER UPDATE OF "status" ON "public"."profiles" FOR EACH ROW WHEN (("new"."role" = 'vendor'::"text")) EXECUTE FUNCTION "public"."notify_vendor_status_change"();



CREATE OR REPLACE TRIGGER "tr_order_refund_to_wallet" AFTER UPDATE OF "status" ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."on_order_cancelled_refund"();



CREATE OR REPLACE TRIGGER "tr_order_status_notification" AFTER UPDATE OF "status" ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."on_order_status_change_notify"();



CREATE OR REPLACE TRIGGER "tr_process_loyalty_award" AFTER UPDATE OF "status" ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_process_loyalty_award"();



CREATE OR REPLACE TRIGGER "tr_process_vendor_payout_on_delivery" AFTER UPDATE OF "status" ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_process_vendor_payout_on_delivery"();



CREATE OR REPLACE TRIGGER "tr_product_updated_at" BEFORE UPDATE ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."set_product_updated_at"();



CREATE OR REPLACE TRIGGER "tr_protect_profile_fields" BEFORE UPDATE ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."protect_profile_fields"();



CREATE OR REPLACE TRIGGER "tr_referral_reward" AFTER INSERT ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."handle_referral_reward"();



CREATE OR REPLACE TRIGGER "tr_restock_notification" AFTER UPDATE OF "stock_count" ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."fn_notify_restock"();



CREATE OR REPLACE TRIGGER "tr_restore_stock_on_cancel" AFTER UPDATE OF "status" ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."restore_stock_on_order_cancel"();



CREATE OR REPLACE TRIGGER "tr_restrict_order_item_edits" BEFORE UPDATE ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_restrict_order_item_edits"();



CREATE OR REPLACE TRIGGER "tr_stock_alert" AFTER UPDATE OF "stock_count" ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."check_stock_levels"();



CREATE OR REPLACE TRIGGER "tr_sync_global_order_status" AFTER UPDATE OF "status" ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_sync_global_order_status"();



CREATE OR REPLACE TRIGGER "tr_sync_order_final_amount" BEFORE INSERT OR UPDATE ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."sync_order_final_amount"();



CREATE OR REPLACE TRIGGER "tr_sync_order_item_vendor" BEFORE INSERT ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_sync_order_item_vendor"();



CREATE OR REPLACE TRIGGER "tr_sync_order_return_status" AFTER UPDATE ON "public"."return_requests" FOR EACH ROW EXECUTE FUNCTION "public"."sync_order_return_status"();



CREATE OR REPLACE TRIGGER "tr_sync_order_shipping_total" AFTER INSERT OR DELETE OR UPDATE OF "delivery_fee" ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_sync_order_shipping_total"();



CREATE OR REPLACE TRIGGER "tr_sync_order_status_from_items" AFTER UPDATE OF "status" ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."fn_sync_order_status_from_items"();



CREATE OR REPLACE TRIGGER "tr_sync_orders_search" AFTER INSERT OR DELETE OR UPDATE ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."fn_queue_search_sync"();



CREATE OR REPLACE TRIGGER "tr_sync_product_stock_status" BEFORE INSERT OR UPDATE OF "stock_count" ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."sync_product_stock_status"();



CREATE OR REPLACE TRIGGER "tr_sync_products_search" AFTER INSERT OR DELETE OR UPDATE ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."fn_queue_search_sync"();



CREATE OR REPLACE TRIGGER "tr_track_stock_update" BEFORE UPDATE OF "stock_count" ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."track_stock_update"();



CREATE OR REPLACE TRIGGER "tr_update_stock_on_item_insert" AFTER INSERT ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."handle_stock_on_order_item"();



CREATE OR REPLACE TRIGGER "tr_update_stock_status" BEFORE INSERT OR UPDATE OF "stock_count" ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."update_product_stock_status"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."addresses" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."audit_logs" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."banners" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."bundle_items" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."bundles" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."cart_items" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."categories" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."coupons" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."favorites" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."fitting_appointments" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."flash_sale_items" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."flash_sales" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."generated_reports" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."logistics_webhook_logs" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."loyalty_points" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."loyalty_tiers" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."messages" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."notifications" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."order_status_history" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."password_resets" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."payments" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."payouts" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."product_waitlist" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."return_requests" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."reviews" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."search_index_queue" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."subscriptions" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."system_logs" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."system_settings" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."user_sessions" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."vendor_payouts" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."wallet_transactions" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."wallets" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at" BEFORE UPDATE ON "public"."wishlist" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_addresses" BEFORE UPDATE ON "public"."addresses" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_audit_logs" BEFORE UPDATE ON "public"."audit_logs" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_banners" BEFORE UPDATE ON "public"."banners" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_categories" BEFORE UPDATE ON "public"."categories" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_coupons" BEFORE UPDATE ON "public"."coupons" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_favorites" BEFORE UPDATE ON "public"."favorites" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_messages" BEFORE UPDATE ON "public"."messages" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_notifications" BEFORE UPDATE ON "public"."notifications" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_order_items" BEFORE UPDATE ON "public"."order_items" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_orders" BEFORE UPDATE ON "public"."orders" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_payments" BEFORE UPDATE ON "public"."payments" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_payouts" BEFORE UPDATE ON "public"."payouts" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_products" BEFORE UPDATE ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_profiles" BEFORE UPDATE ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_reviews" BEFORE UPDATE ON "public"."reviews" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_updated_at_user_sessions" BEFORE UPDATE ON "public"."user_sessions" FOR EACH ROW EXECUTE FUNCTION "public"."update_updated_at_column"();



CREATE OR REPLACE TRIGGER "tr_update_user_tier" BEFORE UPDATE OF "loyalty_points" ON "public"."profiles" FOR EACH ROW EXECUTE FUNCTION "public"."fn_update_user_tier"();



CREATE OR REPLACE TRIGGER "tr_urgent_message_notification" AFTER INSERT ON "public"."messages" FOR EACH ROW EXECUTE FUNCTION "public"."on_urgent_message_notify"();



ALTER TABLE ONLY "public"."banners"
    ADD CONSTRAINT "banners_vendor_id_fkey" FOREIGN KEY ("vendor_id") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."bundle_items"
    ADD CONSTRAINT "bundle_items_bundle_id_fkey" FOREIGN KEY ("bundle_id") REFERENCES "public"."bundles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."bundle_items"
    ADD CONSTRAINT "bundle_items_product_id_fkey" FOREIGN KEY ("product_id") REFERENCES "public"."products"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."cart_items"
    ADD CONSTRAINT "cart_items_product_id_fkey" FOREIGN KEY ("product_id") REFERENCES "public"."products"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."cart_items"
    ADD CONSTRAINT "cart_items_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."coupons"
    ADD CONSTRAINT "coupons_vendor_id_fkey" FOREIGN KEY ("vendor_id") REFERENCES "public"."profiles"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."fitting_appointments"
    ADD CONSTRAINT "fitting_appointments_address_id_fkey" FOREIGN KEY ("address_id") REFERENCES "public"."addresses"("id");



ALTER TABLE ONLY "public"."fitting_appointments"
    ADD CONSTRAINT "fitting_appointments_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."fitting_appointments"
    ADD CONSTRAINT "fitting_appointments_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."fitting_appointments"
    ADD CONSTRAINT "fitting_appointments_vendor_id_fkey" FOREIGN KEY ("vendor_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."flash_sale_items"
    ADD CONSTRAINT "flash_sale_items_flash_sale_id_fkey" FOREIGN KEY ("flash_sale_id") REFERENCES "public"."flash_sales"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."flash_sale_items"
    ADD CONSTRAINT "flash_sale_items_product_id_fkey" FOREIGN KEY ("product_id") REFERENCES "public"."products"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."generated_reports"
    ADD CONSTRAINT "generated_reports_generated_by_fkey" FOREIGN KEY ("generated_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."logistics_webhook_logs"
    ADD CONSTRAINT "logistics_webhook_logs_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id");



ALTER TABLE ONLY "public"."loyalty_points"
    ADD CONSTRAINT "loyalty_points_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id");



ALTER TABLE ONLY "public"."loyalty_points"
    ADD CONSTRAINT "loyalty_points_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_receiver_id_fkey" FOREIGN KEY ("receiver_id") REFERENCES "auth"."users"("id");



ALTER TABLE ONLY "public"."messages"
    ADD CONSTRAINT "messages_sender_id_fkey" FOREIGN KEY ("sender_id") REFERENCES "auth"."users"("id");



ALTER TABLE ONLY "public"."notifications"
    ADD CONSTRAINT "notifications_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."order_items"
    ADD CONSTRAINT "order_items_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."order_items"
    ADD CONSTRAINT "order_items_product_id_fkey" FOREIGN KEY ("product_id") REFERENCES "public"."products"("id");



ALTER TABLE ONLY "public"."order_items"
    ADD CONSTRAINT "order_items_vendor_id_fkey" FOREIGN KEY ("vendor_id") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."order_status_history"
    ADD CONSTRAINT "order_status_history_changed_by_fkey" FOREIGN KEY ("changed_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."order_status_history"
    ADD CONSTRAINT "order_status_history_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."orders"
    ADD CONSTRAINT "orders_coupon_id_fkey" FOREIGN KEY ("coupon_id") REFERENCES "public"."coupons"("id");



ALTER TABLE ONLY "public"."orders"
    ADD CONSTRAINT "orders_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."orders"
    ADD CONSTRAINT "orders_vendor_id_fkey" FOREIGN KEY ("vendor_id") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."payouts"
    ADD CONSTRAINT "payouts_vendor_id_fkey" FOREIGN KEY ("vendor_id") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."product_waitlist"
    ADD CONSTRAINT "product_waitlist_product_id_fkey" FOREIGN KEY ("product_id") REFERENCES "public"."products"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."product_waitlist"
    ADD CONSTRAINT "product_waitlist_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."products"
    ADD CONSTRAINT "products_category_id_fkey" FOREIGN KEY ("category_id") REFERENCES "public"."categories"("id");



ALTER TABLE ONLY "public"."products"
    ADD CONSTRAINT "products_vendor_id_fkey" FOREIGN KEY ("vendor_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_id_fkey" FOREIGN KEY ("id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_referred_by_fkey" FOREIGN KEY ("referred_by") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."profiles"
    ADD CONSTRAINT "profiles_referred_by_id_fkey" FOREIGN KEY ("referred_by_id") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."return_requests"
    ADD CONSTRAINT "return_requests_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."return_requests"
    ADD CONSTRAINT "return_requests_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."subscriptions"
    ADD CONSTRAINT "subscriptions_product_id_fkey" FOREIGN KEY ("product_id") REFERENCES "public"."products"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."subscriptions"
    ADD CONSTRAINT "subscriptions_shipping_address_id_fkey" FOREIGN KEY ("shipping_address_id") REFERENCES "public"."addresses"("id");



ALTER TABLE ONLY "public"."subscriptions"
    ADD CONSTRAINT "subscriptions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."system_logs"
    ADD CONSTRAINT "system_logs_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id");



ALTER TABLE ONLY "public"."user_sessions"
    ADD CONSTRAINT "user_sessions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."vendor_payouts"
    ADD CONSTRAINT "vendor_payouts_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."vendor_payouts"
    ADD CONSTRAINT "vendor_payouts_vendor_id_fkey" FOREIGN KEY ("vendor_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wallet_transactions"
    ADD CONSTRAINT "wallet_transactions_wallet_id_fkey" FOREIGN KEY ("wallet_id") REFERENCES "public"."wallets"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wallets"
    ADD CONSTRAINT "wallets_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wishlist"
    ADD CONSTRAINT "wishlist_product_id_fkey" FOREIGN KEY ("product_id") REFERENCES "public"."products"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."wishlist"
    ADD CONSTRAINT "wishlist_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."profiles"("id") ON DELETE CASCADE;



CREATE POLICY "Admins and Vendors manage their own coupons" ON "public"."coupons" USING ((("public"."get_user_role"("auth"."uid"()) = 'admin'::"text") OR ("vendor_id" = "auth"."uid"())));



CREATE POLICY "Admins can manage all reports" ON "public"."generated_reports" USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins can manage all return requests" ON "public"."return_requests" USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins can update all profiles" ON "public"."profiles" FOR UPDATE USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins can view all fitting appointments" ON "public"."fitting_appointments" FOR SELECT USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins can view all sessions" ON "public"."user_sessions" FOR SELECT USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins have full access to banners" ON "public"."banners" USING ((EXISTS ( SELECT 1
   FROM "public"."profiles"
  WHERE (("profiles"."id" = "auth"."uid"()) AND ("profiles"."role" = 'admin'::"text")))));



CREATE POLICY "Admins have full access to reports" ON "public"."generated_reports" USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins manage addresses" ON "public"."addresses" USING ("public"."is_admin"());



CREATE POLICY "Admins manage audit_logs" ON "public"."audit_logs" USING ("public"."is_admin"());



CREATE POLICY "Admins manage banners" ON "public"."banners" USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins manage categories" ON "public"."categories" USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins manage coupons" ON "public"."coupons" USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins manage favorites" ON "public"."favorites" USING ("public"."is_admin"());



CREATE POLICY "Admins manage messages" ON "public"."messages" USING ("public"."is_admin"());



CREATE POLICY "Admins manage notifications" ON "public"."notifications" USING ("public"."is_admin"());



CREATE POLICY "Admins manage payouts" ON "public"."payouts" USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



CREATE POLICY "Admins manage reviews" ON "public"."reviews" USING ("public"."is_admin"());



CREATE POLICY "Admins manage user_sessions" ON "public"."user_sessions" USING ("public"."is_admin"());



CREATE POLICY "Allow service role to manage password resets" ON "public"."password_resets" USING (true) WITH CHECK (true);



CREATE POLICY "Anyone can view active banners" ON "public"."banners" FOR SELECT USING (("active" = true));



CREATE POLICY "Anyone can view active categories" ON "public"."categories" FOR SELECT USING (("is_active" = true));



CREATE POLICY "Anyone can view approved banners" ON "public"."banners" FOR SELECT USING ((("status" = 'approved'::"text") AND ("active" = true)));



CREATE POLICY "Authenticated: Post reviews" ON "public"."reviews" FOR INSERT WITH CHECK ((("auth"."uid"() = "user_id") AND (EXISTS ( SELECT 1
   FROM "public"."profiles"
  WHERE (("profiles"."id" = "auth"."uid"()) AND ("profiles"."role" = 'student'::"text"))))));



CREATE POLICY "Banners are viewable by everyone" ON "public"."banners" FOR SELECT USING ((("status" = 'approved'::"text") OR ("auth"."uid"() = "vendor_id") OR ("public"."get_user_role"("auth"."uid"()) = 'admin'::"text")));



CREATE POLICY "Buyers can review" ON "public"."reviews" FOR INSERT WITH CHECK (("auth"."uid"() = "user_id"));



CREATE POLICY "Categories viewable by everyone" ON "public"."categories" FOR SELECT USING (true);



CREATE POLICY "Coupons viewable by everyone" ON "public"."coupons" FOR SELECT USING (true);



CREATE POLICY "Enable insert for password resets" ON "public"."password_resets" FOR INSERT TO "anon" WITH CHECK (true);



CREATE POLICY "Enable select for valid resets" ON "public"."password_resets" FOR SELECT TO "anon" USING ((("is_used" = false) AND ("expires_at" > "now"())));



CREATE POLICY "Enable update for marking as used" ON "public"."password_resets" FOR UPDATE TO "anon" USING ((("is_used" = false) AND ("expires_at" > "now"()))) WITH CHECK (("is_used" = true));



CREATE POLICY "Everyone can view active bundles" ON "public"."bundles" FOR SELECT USING (("is_active" = true));



CREATE POLICY "Favorites viewable by everyone" ON "public"."favorites" FOR SELECT USING (true);



CREATE POLICY "Marketing viewable by everyone" ON "public"."coupons" FOR SELECT USING (true);



CREATE POLICY "Order history access policy" ON "public"."order_status_history" FOR SELECT USING ("public"."can_access_order"("order_id"));



CREATE POLICY "Order items access policy" ON "public"."order_items" FOR SELECT USING ((("vendor_id" = "auth"."uid"()) OR ("public"."get_user_role"("auth"."uid"()) = 'admin'::"text") OR (EXISTS ( SELECT 1
   FROM "public"."orders" "o"
  WHERE (("o"."id" = "order_items"."order_id") AND ("o"."user_id" = "auth"."uid"()))))));



CREATE POLICY "Orders access policy" ON "public"."orders" FOR SELECT USING ((("auth"."uid"() = "user_id") OR ("auth"."uid"() = "vendor_id") OR ("public"."get_user_role"("auth"."uid"()) = 'admin'::"text") OR (EXISTS ( SELECT 1
   FROM "public"."order_items" "oi"
  WHERE (("oi"."order_id" = "orders"."id") AND ("oi"."vendor_id" = "auth"."uid"()))))));



CREATE POLICY "Public: View reviews" ON "public"."reviews" FOR SELECT USING (true);



CREATE POLICY "Reviews public" ON "public"."reviews" FOR SELECT USING (true);



CREATE POLICY "Reviews viewable by everyone" ON "public"."reviews" FOR SELECT USING (true);



CREATE POLICY "Send messages" ON "public"."messages" FOR INSERT WITH CHECK (("sender_id" = "auth"."uid"()));



CREATE POLICY "Update own notifications" ON "public"."notifications" FOR UPDATE USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can create their own return requests" ON "public"."return_requests" FOR INSERT WITH CHECK (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can delete from their own cart" ON "public"."cart_items" FOR DELETE USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can delete own sessions" ON "public"."user_sessions" FOR DELETE USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can insert into their own cart" ON "public"."cart_items" FOR INSERT WITH CHECK (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can insert own logs" ON "public"."system_logs" FOR INSERT WITH CHECK (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can insert own messages" ON "public"."messages" FOR INSERT WITH CHECK (("auth"."uid"() = "sender_id"));



CREATE POLICY "Users can insert their own logs" ON "public"."system_logs" FOR INSERT WITH CHECK ((("auth"."uid"() = "user_id") OR ("auth"."uid"() IS NULL)));



CREATE POLICY "Users can insert their own messages" ON "public"."messages" FOR INSERT WITH CHECK (("auth"."uid"() = "sender_id"));



CREATE POLICY "Users can insert their own order items" ON "public"."order_items" FOR INSERT WITH CHECK ((EXISTS ( SELECT 1
   FROM "public"."orders"
  WHERE (("orders"."id" = "order_items"."order_id") AND ("orders"."user_id" = "auth"."uid"())))));



CREATE POLICY "Users can manage own addresses" ON "public"."addresses" USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can manage own reviews" ON "public"."reviews" USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can manage own subscriptions" ON "public"."subscriptions" USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can manage own waitlist" ON "public"."product_waitlist" USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can manage their own notifications" ON "public"."notifications" USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can see messages they sent or received" ON "public"."messages" FOR SELECT USING ((("auth"."uid"() = "sender_id") OR ("auth"."uid"() = "receiver_id")));



CREATE POLICY "Users can update own notifications (mark read/archive)" ON "public"."notifications" FOR UPDATE USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can update own profile" ON "public"."profiles" FOR UPDATE USING (("auth"."uid"() = "id")) WITH CHECK (("auth"."uid"() = "id"));



CREATE POLICY "Users can update read status of received messages" ON "public"."messages" FOR UPDATE USING (("auth"."uid"() = "receiver_id")) WITH CHECK (("auth"."uid"() = "receiver_id"));



CREATE POLICY "Users can update their own cart items" ON "public"."cart_items" FOR UPDATE USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can update their own notifications" ON "public"."notifications" FOR UPDATE USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can view own logs" ON "public"."system_logs" FOR SELECT USING ((("auth"."uid"() = "user_id") OR ("public"."get_user_role"("auth"."uid"()) = 'admin'::"text")));



CREATE POLICY "Users can view own messages" ON "public"."messages" FOR SELECT USING ((("auth"."uid"() = "sender_id") OR ("auth"."uid"() = "receiver_id")));



CREATE POLICY "Users can view own notifications" ON "public"."notifications" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can view own sessions" ON "public"."user_sessions" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can view own transactions" ON "public"."wallet_transactions" FOR SELECT USING ((EXISTS ( SELECT 1
   FROM "public"."wallets"
  WHERE (("wallets"."id" = "wallet_transactions"."wallet_id") AND ("wallets"."user_id" = "auth"."uid"())))));



CREATE POLICY "Users can view own wallet" ON "public"."wallets" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can view their own cart" ON "public"."cart_items" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can view their own fitting appointments" ON "public"."fitting_appointments" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can view their own messages" ON "public"."messages" FOR SELECT USING ((("auth"."uid"() = "sender_id") OR ("auth"."uid"() = "receiver_id")));



CREATE POLICY "Users can view their own notifications" ON "public"."notifications" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users can view their own return requests" ON "public"."return_requests" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users insert own messages" ON "public"."messages" FOR INSERT WITH CHECK (("auth"."uid"() = "sender_id"));



CREATE POLICY "Users manage own addresses" ON "public"."addresses" USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users manage own favorites" ON "public"."favorites" USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users manage own wishlist" ON "public"."wishlist" USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users see own messages" ON "public"."messages" FOR SELECT USING ((("auth"."uid"() = "sender_id") OR ("auth"."uid"() = "receiver_id")));



CREATE POLICY "Users see own notifications" ON "public"."notifications" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users update own notifications" ON "public"."notifications" FOR UPDATE USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users view own notifications" ON "public"."notifications" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Users view own wallet" ON "public"."wallets" FOR SELECT USING (("auth"."uid"() = "user_id"));



CREATE POLICY "Vendors can delete own pending banners" ON "public"."banners" FOR DELETE USING ((("auth"."uid"() = "vendor_id") AND ("status" = 'pending'::"text")));



CREATE POLICY "Vendors can manage their own banners" ON "public"."banners" USING (("vendor_id" = "auth"."uid"())) WITH CHECK (("vendor_id" = "auth"."uid"()));



CREATE POLICY "Vendors can request banners" ON "public"."banners" FOR INSERT WITH CHECK (("public"."get_user_role"("auth"."uid"()) = 'vendor'::"text"));



CREATE POLICY "Vendors can view their fitting appointments" ON "public"."fitting_appointments" FOR SELECT USING (("auth"."uid"() = "vendor_id"));



CREATE POLICY "Vendors view own payouts" ON "public"."payouts" FOR SELECT USING (("vendor_id" = "auth"."uid"()));



CREATE POLICY "View own messages" ON "public"."messages" FOR SELECT USING ((("sender_id" = "auth"."uid"()) OR ("receiver_id" = "auth"."uid"())));



ALTER TABLE "public"."addresses" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."audit_logs" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."banners" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."bundle_items" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."bundles" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."cart_items" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."categories" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."coupons" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."favorites" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."fitting_appointments" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."flash_sale_items" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."flash_sales" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."generated_reports" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."logistics_webhook_logs" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."loyalty_points" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."loyalty_tiers" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."notifications" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."order_items" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "order_items_insert_policy" ON "public"."order_items" FOR INSERT WITH CHECK ((EXISTS ( SELECT 1
   FROM "public"."orders"
  WHERE (("orders"."id" = "order_items"."order_id") AND ("orders"."user_id" = "auth"."uid"())))));



CREATE POLICY "order_items_select_policy" ON "public"."order_items" FOR SELECT USING ((("public"."get_user_role"("auth"."uid"()) = 'admin'::"text") OR ("vendor_id" = "auth"."uid"()) OR (EXISTS ( SELECT 1
   FROM "public"."orders"
  WHERE (("orders"."id" = "order_items"."order_id") AND ("orders"."user_id" = "auth"."uid"()))))));



CREATE POLICY "order_items_select_v10" ON "public"."order_items" FOR SELECT USING ("public"."can_access_order"("order_id"));



CREATE POLICY "order_items_update_policy" ON "public"."order_items" FOR UPDATE USING ((("public"."get_user_role"("auth"."uid"()) = 'admin'::"text") OR ("vendor_id" = "auth"."uid"()))) WITH CHECK ((("public"."get_user_role"("auth"."uid"()) = 'admin'::"text") OR ("vendor_id" = "auth"."uid"())));



ALTER TABLE "public"."order_status_history" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."orders" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "orders_insert_policy" ON "public"."orders" FOR INSERT WITH CHECK (("auth"."uid"() = "user_id"));



CREATE POLICY "orders_select_policy" ON "public"."orders" FOR SELECT USING ((("public"."get_user_role"("auth"."uid"()) = 'admin'::"text") OR ("user_id" = "auth"."uid"()) OR (EXISTS ( SELECT 1
   FROM "public"."order_items"
  WHERE (("order_items"."order_id" = "orders"."id") AND ("order_items"."vendor_id" = "auth"."uid"()))))));



CREATE POLICY "orders_update_policy" ON "public"."orders" FOR UPDATE USING (("public"."get_user_role"("auth"."uid"()) = 'admin'::"text"));



ALTER TABLE "public"."password_resets" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."payments" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "payments_insert_v9" ON "public"."payments" FOR INSERT WITH CHECK (true);



CREATE POLICY "payments_select_policy" ON "public"."payments" FOR SELECT USING ((EXISTS ( SELECT 1
   FROM "public"."orders"
  WHERE (("orders"."id" = "payments"."order_id") AND (("orders"."user_id" = "auth"."uid"()) OR "public"."is_admin"())))));



CREATE POLICY "payments_select_v9" ON "public"."payments" FOR SELECT USING (true);



ALTER TABLE "public"."payouts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."product_waitlist" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."products" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "products_delete_policy" ON "public"."products" FOR DELETE USING ((("auth"."uid"() = "vendor_id") OR "public"."is_admin"()));



CREATE POLICY "products_insert_policy" ON "public"."products" FOR INSERT WITH CHECK ((("auth"."uid"() = "vendor_id") AND ("public"."get_user_role"("auth"."uid"()) = 'vendor'::"text") AND (EXISTS ( SELECT 1
   FROM "public"."profiles"
  WHERE (("profiles"."id" = "auth"."uid"()) AND ("profiles"."status" = 'active'::"text"))))));



CREATE POLICY "products_select_policy" ON "public"."products" FOR SELECT USING ((("is_active" = true) OR ("auth"."uid"() = "vendor_id") OR "public"."is_admin"()));



CREATE POLICY "products_update_policy" ON "public"."products" FOR UPDATE USING ((("auth"."uid"() = "vendor_id") OR "public"."is_admin"()));



ALTER TABLE "public"."profiles" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "profiles_access_v9" ON "public"."profiles" USING ((("id" = "auth"."uid"()) OR ((("auth"."jwt"() -> 'user_metadata'::"text") ->> 'role'::"text") = 'admin'::"text")));



ALTER TABLE "public"."return_requests" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."reviews" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."search_index_queue" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."subscriptions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."system_logs" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "system_logs_select_v10" ON "public"."system_logs" FOR SELECT USING ((("auth"."uid"() = "user_id") OR ("public"."get_user_role"("auth"."uid"()) = 'admin'::"text")));



ALTER TABLE "public"."system_settings" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."user_sessions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."vendor_payouts" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."wallet_transactions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."wallets" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."wishlist" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";


ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."banners";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."categories";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."coupons";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."generated_reports";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."messages";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."notifications";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."order_items";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."orders";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."payouts";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."products";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."profiles";



ALTER PUBLICATION "supabase_realtime" ADD TABLE ONLY "public"."system_logs";



GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";

























































































































































GRANT ALL ON FUNCTION "public"."admin_delete_user"("target_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."admin_delete_user"("target_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."admin_delete_user"("target_user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."admin_force_update_order"("p_order_id" "uuid", "p_status" "text", "p_payment_status" "text", "p_admin_notes" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."admin_force_update_order"("p_order_id" "uuid", "p_status" "text", "p_payment_status" "text", "p_admin_notes" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."admin_force_update_order"("p_order_id" "uuid", "p_status" "text", "p_payment_status" "text", "p_admin_notes" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."audit_log_trigger"() TO "anon";
GRANT ALL ON FUNCTION "public"."audit_log_trigger"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."audit_log_trigger"() TO "service_role";



GRANT ALL ON FUNCTION "public"."award_loyalty_points"() TO "anon";
GRANT ALL ON FUNCTION "public"."award_loyalty_points"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."award_loyalty_points"() TO "service_role";



GRANT ALL ON FUNCTION "public"."broadcast_notification"() TO "anon";
GRANT ALL ON FUNCTION "public"."broadcast_notification"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."broadcast_notification"() TO "service_role";



GRANT ALL ON FUNCTION "public"."bulk_update_notifications"("notification_ids" "uuid"[], "new_is_read" boolean, "new_is_archived" boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."bulk_update_notifications"("notification_ids" "uuid"[], "new_is_read" boolean, "new_is_archived" boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."bulk_update_notifications"("notification_ids" "uuid"[], "new_is_read" boolean, "new_is_archived" boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."calculate_order_total"("p_order_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."calculate_order_total"("p_order_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."calculate_order_total"("p_order_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."can_access_order"("p_order_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."can_access_order"("p_order_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."can_access_order"("p_order_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."check_marketing_expirations"() TO "anon";
GRANT ALL ON FUNCTION "public"."check_marketing_expirations"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."check_marketing_expirations"() TO "service_role";



GRANT ALL ON FUNCTION "public"."check_return_eligibility"() TO "anon";
GRANT ALL ON FUNCTION "public"."check_return_eligibility"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."check_return_eligibility"() TO "service_role";



GRANT ALL ON FUNCTION "public"."check_review_eligibility"() TO "anon";
GRANT ALL ON FUNCTION "public"."check_review_eligibility"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."check_review_eligibility"() TO "service_role";



GRANT ALL ON FUNCTION "public"."check_stock_levels"() TO "anon";
GRANT ALL ON FUNCTION "public"."check_stock_levels"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."check_stock_levels"() TO "service_role";



GRANT ALL ON FUNCTION "public"."create_subscription"("p_product_id" "uuid", "p_quantity" integer, "p_size" "text", "p_color" "text", "p_frequency_days" integer, "p_address_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."create_subscription"("p_product_id" "uuid", "p_quantity" integer, "p_size" "text", "p_color" "text", "p_frequency_days" integer, "p_address_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."create_subscription"("p_product_id" "uuid", "p_quantity" integer, "p_size" "text", "p_color" "text", "p_frequency_days" integer, "p_address_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."finalize_successful_payment"("p_order_id" "uuid", "p_transaction_id" "text", "p_amount" numeric, "p_method" "text", "p_response" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."finalize_successful_payment"("p_order_id" "uuid", "p_transaction_id" "text", "p_amount" numeric, "p_method" "text", "p_response" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."finalize_successful_payment"("p_order_id" "uuid", "p_transaction_id" "text", "p_amount" numeric, "p_method" "text", "p_response" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_audit_trigger"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_audit_trigger"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_audit_trigger"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_award_loyalty_points"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_award_loyalty_points"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_award_loyalty_points"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_award_referral_bonus"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_award_referral_bonus"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_award_referral_bonus"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_calculate_item_delivery_fee"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_calculate_item_delivery_fee"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_calculate_item_delivery_fee"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_calculate_vendor_earnings"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_calculate_vendor_earnings"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_calculate_vendor_earnings"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_check_wallet_balance"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_check_wallet_balance"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_check_wallet_balance"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_ensure_default_after_delete"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_ensure_default_after_delete"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_ensure_default_after_delete"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_generate_referral_code"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_generate_referral_code"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_generate_referral_code"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_handle_item_stock_on_cancel"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_handle_item_stock_on_cancel"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_handle_item_stock_on_cancel"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_manage_default_address"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_manage_default_address"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_manage_default_address"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_notify_restock"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_notify_restock"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_notify_restock"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_notify_waitlist"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_notify_waitlist"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_notify_waitlist"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_process_loyalty_award"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_process_loyalty_award"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_process_loyalty_award"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_process_referral"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_process_referral"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_process_referral"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_process_vendor_payout_on_delivery"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_process_vendor_payout_on_delivery"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_process_vendor_payout_on_delivery"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_queue_search_sync"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_queue_search_sync"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_queue_search_sync"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_restrict_order_item_edits"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_restrict_order_item_edits"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_restrict_order_item_edits"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_sync_global_order_status"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_sync_global_order_status"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_sync_global_order_status"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_sync_order_item_vendor"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_sync_order_item_vendor"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_sync_order_item_vendor"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_sync_order_shipping_total"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_sync_order_shipping_total"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_sync_order_shipping_total"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_sync_order_status_from_items"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_sync_order_status_from_items"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_sync_order_status_from_items"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fn_update_user_tier"() TO "anon";
GRANT ALL ON FUNCTION "public"."fn_update_user_tier"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fn_update_user_tier"() TO "service_role";



GRANT ALL ON FUNCTION "public"."generate_subscription_orders"() TO "anon";
GRANT ALL ON FUNCTION "public"."generate_subscription_orders"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."generate_subscription_orders"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_active_flash_sales"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_active_flash_sales"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_active_flash_sales"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_admin_dashboard_stats"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_admin_dashboard_stats"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_admin_dashboard_stats"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_admin_detailed_sales_report"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_admin_detailed_sales_report"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_admin_detailed_sales_report"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_coupon_performance"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_coupon_performance"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_coupon_performance"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_demand_forecasting"("p_vendor_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_demand_forecasting"("p_vendor_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_demand_forecasting"("p_vendor_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_financial_summary"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone) TO "anon";
GRANT ALL ON FUNCTION "public"."get_financial_summary"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone) TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_financial_summary"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone) TO "service_role";



GRANT ALL ON FUNCTION "public"."get_financial_summary_v2"("p_start_date" "text", "p_end_date" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_financial_summary_v2"("p_start_date" "text", "p_end_date" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_financial_summary_v2"("p_start_date" "text", "p_end_date" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_inventory_health"("p_vendor_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_inventory_health"("p_vendor_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_inventory_health"("p_vendor_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_live_users"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_live_users"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_live_users"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_my_role"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_my_role"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_my_role"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_sales_trends"("p_start_date" "text", "p_end_date" "text", "p_interval" "text", "p_vendor_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_sales_trends"("p_start_date" "text", "p_end_date" "text", "p_interval" "text", "p_vendor_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_sales_trends"("p_start_date" "text", "p_end_date" "text", "p_interval" "text", "p_vendor_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_sales_trends"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone, "p_interval" "text", "p_vendor_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_sales_trends"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone, "p_interval" "text", "p_vendor_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_sales_trends"("p_start_date" timestamp with time zone, "p_end_date" timestamp with time zone, "p_interval" "text", "p_vendor_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_sales_trends_v2"("p_interval" "text", "p_vendor_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_sales_trends_v2"("p_interval" "text", "p_vendor_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_sales_trends_v2"("p_interval" "text", "p_vendor_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_user_role"("user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_user_role"("user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_user_role"("user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_vendor_analytics"("vendor_uuid" "uuid", "start_date" timestamp with time zone) TO "anon";
GRANT ALL ON FUNCTION "public"."get_vendor_analytics"("vendor_uuid" "uuid", "start_date" timestamp with time zone) TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_vendor_analytics"("vendor_uuid" "uuid", "start_date" timestamp with time zone) TO "service_role";



GRANT ALL ON FUNCTION "public"."get_vendor_dashboard_stats"("p_vendor_id" "uuid", "p_days" integer) TO "anon";
GRANT ALL ON FUNCTION "public"."get_vendor_dashboard_stats"("p_vendor_id" "uuid", "p_days" integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_vendor_dashboard_stats"("p_vendor_id" "uuid", "p_days" integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."get_vendor_inventory_stats"("p_vendor_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."get_vendor_inventory_stats"("p_vendor_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_vendor_inventory_stats"("p_vendor_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_banner_resubmission"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_banner_resubmission"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_banner_resubmission"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_coupon_usage"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_coupon_usage"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_coupon_usage"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_mpesa_callback_update"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_mpesa_callback_update"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_mpesa_callback_update"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_new_password_reset"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_new_password_reset"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_new_password_reset"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_new_user"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_payment_completion"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_payment_completion"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_payment_completion"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_product_update_time"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_product_update_time"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_product_update_time"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_profile_updated"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_profile_updated"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_profile_updated"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_referral_reward"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_referral_reward"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_referral_reward"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_stock_on_order_cancel"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_stock_on_order_cancel"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_stock_on_order_cancel"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_stock_on_order_item"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_stock_on_order_item"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_stock_on_order_item"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_successful_payment"() TO "anon";
GRANT ALL ON FUNCTION "public"."handle_successful_payment"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_successful_payment"() TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_user_login"("target_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."handle_user_login"("target_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_user_login"("target_user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."handle_user_logout"("target_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."handle_user_logout"("target_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."handle_user_logout"("target_user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."is_admin"() TO "anon";
GRANT ALL ON FUNCTION "public"."is_admin"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."is_admin"() TO "service_role";



GRANT ALL ON FUNCTION "public"."join_product_waitlist"("p_product_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."join_product_waitlist"("p_product_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."join_product_waitlist"("p_product_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."jsonb_diff"("l" "jsonb", "r" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."jsonb_diff"("l" "jsonb", "r" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."jsonb_diff"("l" "jsonb", "r" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."log_address_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_address_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_address_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_coupon_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_coupon_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_coupon_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_favorite_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_favorite_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_favorite_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_order_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_order_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_order_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_order_status_change"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_order_status_change"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_order_status_change"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_payment_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_payment_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_payment_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_payout_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_payout_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_payout_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_product_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_product_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_product_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_profile_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_profile_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_profile_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_profile_changes"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_profile_changes"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_profile_changes"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_profile_status_change"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_profile_status_change"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_profile_status_change"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_report_generation"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_report_generation"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_report_generation"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_review_activity"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_review_activity"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_review_activity"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_storage_upload"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_storage_upload"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_storage_upload"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_user_login"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_user_login"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_user_login"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_user_status_change"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_user_status_change"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_user_status_change"() TO "service_role";



GRANT ALL ON FUNCTION "public"."mark_all_active_notifications_read"() TO "anon";
GRANT ALL ON FUNCTION "public"."mark_all_active_notifications_read"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."mark_all_active_notifications_read"() TO "service_role";



GRANT ALL ON FUNCTION "public"."mark_messages_delivered"("receiver_uuid" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."mark_messages_delivered"("receiver_uuid" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."mark_messages_delivered"("receiver_uuid" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."mark_messages_read"("sender_uuid" "uuid", "receiver_uuid" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."mark_messages_read"("sender_uuid" "uuid", "receiver_uuid" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."mark_messages_read"("sender_uuid" "uuid", "receiver_uuid" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."notify_fcm_bridge"() TO "anon";
GRANT ALL ON FUNCTION "public"."notify_fcm_bridge"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."notify_fcm_bridge"() TO "service_role";



GRANT ALL ON FUNCTION "public"."notify_vendor_status_change"() TO "anon";
GRANT ALL ON FUNCTION "public"."notify_vendor_status_change"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."notify_vendor_status_change"() TO "service_role";



GRANT ALL ON FUNCTION "public"."on_new_order_notify_vendor"() TO "anon";
GRANT ALL ON FUNCTION "public"."on_new_order_notify_vendor"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."on_new_order_notify_vendor"() TO "service_role";



GRANT ALL ON FUNCTION "public"."on_order_cancelled_refund"() TO "anon";
GRANT ALL ON FUNCTION "public"."on_order_cancelled_refund"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."on_order_cancelled_refund"() TO "service_role";



GRANT ALL ON FUNCTION "public"."on_order_status_change_notify"() TO "anon";
GRANT ALL ON FUNCTION "public"."on_order_status_change_notify"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."on_order_status_change_notify"() TO "service_role";



GRANT ALL ON FUNCTION "public"."on_urgent_message_notify"() TO "anon";
GRANT ALL ON FUNCTION "public"."on_urgent_message_notify"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."on_urgent_message_notify"() TO "service_role";



GRANT ALL ON FUNCTION "public"."process_refund"("p_return_id" "uuid", "p_admin_notes" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."process_refund"("p_return_id" "uuid", "p_admin_notes" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."process_refund"("p_return_id" "uuid", "p_admin_notes" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."process_wallet_payment"("p_order_id" "uuid", "p_user_id" "uuid", "p_amount" numeric) TO "anon";
GRANT ALL ON FUNCTION "public"."process_wallet_payment"("p_order_id" "uuid", "p_user_id" "uuid", "p_amount" numeric) TO "authenticated";
GRANT ALL ON FUNCTION "public"."process_wallet_payment"("p_order_id" "uuid", "p_user_id" "uuid", "p_amount" numeric) TO "service_role";



GRANT ALL ON FUNCTION "public"."protect_profile_fields"() TO "anon";
GRANT ALL ON FUNCTION "public"."protect_profile_fields"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."protect_profile_fields"() TO "service_role";



GRANT ALL ON FUNCTION "public"."request_fitting_service"("p_order_id" "uuid", "p_preferred_date" "text", "p_preferred_slot" "text", "p_address" "text", "p_user_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."request_fitting_service"("p_order_id" "uuid", "p_preferred_date" "text", "p_preferred_slot" "text", "p_address" "text", "p_user_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."request_fitting_service"("p_order_id" "uuid", "p_preferred_date" "text", "p_preferred_slot" "text", "p_address" "text", "p_user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."reset_password_with_otp"("target_email" "text", "otp_code" "text", "new_password" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."reset_password_with_otp"("target_email" "text", "otp_code" "text", "new_password" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."reset_password_with_otp"("target_email" "text", "otp_code" "text", "new_password" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."restore_all_archived_notifications"() TO "anon";
GRANT ALL ON FUNCTION "public"."restore_all_archived_notifications"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."restore_all_archived_notifications"() TO "service_role";



GRANT ALL ON FUNCTION "public"."restore_stock_on_order_cancel"() TO "anon";
GRANT ALL ON FUNCTION "public"."restore_stock_on_order_cancel"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."restore_stock_on_order_cancel"() TO "service_role";



GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "anon";
GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "service_role";



GRANT ALL ON FUNCTION "public"."set_product_updated_at"() TO "anon";
GRANT ALL ON FUNCTION "public"."set_product_updated_at"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."set_product_updated_at"() TO "service_role";



GRANT ALL ON FUNCTION "public"."sync_cart_items"("p_items" "jsonb") TO "anon";
GRANT ALL ON FUNCTION "public"."sync_cart_items"("p_items" "jsonb") TO "authenticated";
GRANT ALL ON FUNCTION "public"."sync_cart_items"("p_items" "jsonb") TO "service_role";



GRANT ALL ON FUNCTION "public"."sync_order_final_amount"() TO "anon";
GRANT ALL ON FUNCTION "public"."sync_order_final_amount"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."sync_order_final_amount"() TO "service_role";



GRANT ALL ON FUNCTION "public"."sync_order_return_status"() TO "anon";
GRANT ALL ON FUNCTION "public"."sync_order_return_status"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."sync_order_return_status"() TO "service_role";



GRANT ALL ON FUNCTION "public"."sync_product_stock_status"() TO "anon";
GRANT ALL ON FUNCTION "public"."sync_product_stock_status"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."sync_product_stock_status"() TO "service_role";



GRANT ALL ON FUNCTION "public"."sync_profile_to_auth"() TO "anon";
GRANT ALL ON FUNCTION "public"."sync_profile_to_auth"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."sync_profile_to_auth"() TO "service_role";



GRANT ALL ON FUNCTION "public"."sync_ratings_on_review"() TO "anon";
GRANT ALL ON FUNCTION "public"."sync_ratings_on_review"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."sync_ratings_on_review"() TO "service_role";



GRANT ALL ON FUNCTION "public"."track_stock_update"() TO "anon";
GRANT ALL ON FUNCTION "public"."track_stock_update"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."track_stock_update"() TO "service_role";



GRANT ALL ON FUNCTION "public"."update_loyalty_tier"() TO "anon";
GRANT ALL ON FUNCTION "public"."update_loyalty_tier"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_loyalty_tier"() TO "service_role";



GRANT ALL ON FUNCTION "public"."update_order_tracking"("p_order_id" "uuid", "p_tracking_number" "text", "p_courier_name" "text", "p_status" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."update_order_tracking"("p_order_id" "uuid", "p_tracking_number" "text", "p_courier_name" "text", "p_status" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_order_tracking"("p_order_id" "uuid", "p_tracking_number" "text", "p_courier_name" "text", "p_status" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."update_product_inventory"("p_product_id" "uuid", "p_stock_count" integer, "p_in_stock" boolean, "p_is_active" boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."update_product_inventory"("p_product_id" "uuid", "p_stock_count" integer, "p_in_stock" boolean, "p_is_active" boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_product_inventory"("p_product_id" "uuid", "p_stock_count" integer, "p_in_stock" boolean, "p_is_active" boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_is_active" boolean, "p_stock_count" integer) TO "anon";
GRANT ALL ON FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_is_active" boolean, "p_stock_count" integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_is_active" boolean, "p_stock_count" integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_stock_count" integer, "p_is_active" boolean) TO "anon";
GRANT ALL ON FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_stock_count" integer, "p_is_active" boolean) TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_product_inventory_v2"("p_product_id" "uuid", "p_stock_count" integer, "p_is_active" boolean) TO "service_role";



GRANT ALL ON FUNCTION "public"."update_product_rating"() TO "anon";
GRANT ALL ON FUNCTION "public"."update_product_rating"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_product_rating"() TO "service_role";



GRANT ALL ON FUNCTION "public"."update_product_stock_status"() TO "anon";
GRANT ALL ON FUNCTION "public"."update_product_stock_status"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_product_stock_status"() TO "service_role";



GRANT ALL ON FUNCTION "public"."update_updated_at_column"() TO "anon";
GRANT ALL ON FUNCTION "public"."update_updated_at_column"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_updated_at_column"() TO "service_role";



GRANT ALL ON FUNCTION "public"."validate_inventory"("p_order_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."validate_inventory"("p_order_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."validate_inventory"("p_order_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."validate_inventory_v2"("p_order_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."validate_inventory_v2"("p_order_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."validate_inventory_v2"("p_order_id" "uuid") TO "service_role";



GRANT ALL ON TABLE "public"."products" TO "anon";
GRANT ALL ON TABLE "public"."products" TO "authenticated";
GRANT ALL ON TABLE "public"."products" TO "service_role";



GRANT ALL ON FUNCTION "public"."visual_search_products"("p_visual_tags" "text"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."visual_search_products"("p_visual_tags" "text"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."visual_search_products"("p_visual_tags" "text"[]) TO "service_role";


















GRANT ALL ON TABLE "public"."addresses" TO "anon";
GRANT ALL ON TABLE "public"."addresses" TO "authenticated";
GRANT ALL ON TABLE "public"."addresses" TO "service_role";



GRANT ALL ON TABLE "public"."order_items" TO "anon";
GRANT ALL ON TABLE "public"."order_items" TO "authenticated";
GRANT ALL ON TABLE "public"."order_items" TO "service_role";



GRANT ALL ON TABLE "public"."orders" TO "anon";
GRANT ALL ON TABLE "public"."orders" TO "authenticated";
GRANT ALL ON TABLE "public"."orders" TO "service_role";



GRANT ALL ON TABLE "public"."payments" TO "anon";
GRANT ALL ON TABLE "public"."payments" TO "authenticated";
GRANT ALL ON TABLE "public"."payments" TO "service_role";



GRANT ALL ON TABLE "public"."profiles" TO "anon";
GRANT ALL ON TABLE "public"."profiles" TO "authenticated";
GRANT ALL ON TABLE "public"."profiles" TO "service_role";



GRANT ALL ON TABLE "public"."admin_detailed_sales_report" TO "anon";
GRANT ALL ON TABLE "public"."admin_detailed_sales_report" TO "authenticated";
GRANT ALL ON TABLE "public"."admin_detailed_sales_report" TO "service_role";



GRANT ALL ON TABLE "public"."admin_financial_summary" TO "anon";
GRANT ALL ON TABLE "public"."admin_financial_summary" TO "authenticated";
GRANT ALL ON TABLE "public"."admin_financial_summary" TO "service_role";



GRANT ALL ON TABLE "public"."admin_master_transaction_log" TO "anon";
GRANT ALL ON TABLE "public"."admin_master_transaction_log" TO "authenticated";
GRANT ALL ON TABLE "public"."admin_master_transaction_log" TO "service_role";



GRANT ALL ON TABLE "public"."audit_logs" TO "anon";
GRANT ALL ON TABLE "public"."audit_logs" TO "authenticated";
GRANT ALL ON TABLE "public"."audit_logs" TO "service_role";



GRANT ALL ON TABLE "public"."banners" TO "anon";
GRANT ALL ON TABLE "public"."banners" TO "authenticated";
GRANT ALL ON TABLE "public"."banners" TO "service_role";



GRANT ALL ON TABLE "public"."bundle_items" TO "anon";
GRANT ALL ON TABLE "public"."bundle_items" TO "authenticated";
GRANT ALL ON TABLE "public"."bundle_items" TO "service_role";



GRANT ALL ON TABLE "public"."bundles" TO "anon";
GRANT ALL ON TABLE "public"."bundles" TO "authenticated";
GRANT ALL ON TABLE "public"."bundles" TO "service_role";



GRANT ALL ON TABLE "public"."cart_items" TO "anon";
GRANT ALL ON TABLE "public"."cart_items" TO "authenticated";
GRANT ALL ON TABLE "public"."cart_items" TO "service_role";



GRANT ALL ON TABLE "public"."categories" TO "anon";
GRANT ALL ON TABLE "public"."categories" TO "authenticated";
GRANT ALL ON TABLE "public"."categories" TO "service_role";



GRANT ALL ON TABLE "public"."catalog_products" TO "anon";
GRANT ALL ON TABLE "public"."catalog_products" TO "authenticated";
GRANT ALL ON TABLE "public"."catalog_products" TO "service_role";



GRANT ALL ON TABLE "public"."coupons" TO "anon";
GRANT ALL ON TABLE "public"."coupons" TO "authenticated";
GRANT ALL ON TABLE "public"."coupons" TO "service_role";



GRANT ALL ON TABLE "public"."coupon_performance" TO "anon";
GRANT ALL ON TABLE "public"."coupon_performance" TO "authenticated";
GRANT ALL ON TABLE "public"."coupon_performance" TO "service_role";



GRANT ALL ON TABLE "public"."coupon_usage_stats" TO "anon";
GRANT ALL ON TABLE "public"."coupon_usage_stats" TO "authenticated";
GRANT ALL ON TABLE "public"."coupon_usage_stats" TO "service_role";



GRANT ALL ON TABLE "public"."favorites" TO "anon";
GRANT ALL ON TABLE "public"."favorites" TO "authenticated";
GRANT ALL ON TABLE "public"."favorites" TO "service_role";



GRANT ALL ON TABLE "public"."fitting_appointments" TO "anon";
GRANT ALL ON TABLE "public"."fitting_appointments" TO "authenticated";
GRANT ALL ON TABLE "public"."fitting_appointments" TO "service_role";



GRANT ALL ON TABLE "public"."flash_sale_items" TO "anon";
GRANT ALL ON TABLE "public"."flash_sale_items" TO "authenticated";
GRANT ALL ON TABLE "public"."flash_sale_items" TO "service_role";



GRANT ALL ON TABLE "public"."flash_sales" TO "anon";
GRANT ALL ON TABLE "public"."flash_sales" TO "authenticated";
GRANT ALL ON TABLE "public"."flash_sales" TO "service_role";



GRANT ALL ON TABLE "public"."generated_reports" TO "anon";
GRANT ALL ON TABLE "public"."generated_reports" TO "authenticated";
GRANT ALL ON TABLE "public"."generated_reports" TO "service_role";



GRANT ALL ON TABLE "public"."logistics_webhook_logs" TO "anon";
GRANT ALL ON TABLE "public"."logistics_webhook_logs" TO "authenticated";
GRANT ALL ON TABLE "public"."logistics_webhook_logs" TO "service_role";



GRANT ALL ON TABLE "public"."loyalty_points" TO "anon";
GRANT ALL ON TABLE "public"."loyalty_points" TO "authenticated";
GRANT ALL ON TABLE "public"."loyalty_points" TO "service_role";



GRANT ALL ON TABLE "public"."loyalty_tiers" TO "anon";
GRANT ALL ON TABLE "public"."loyalty_tiers" TO "authenticated";
GRANT ALL ON TABLE "public"."loyalty_tiers" TO "service_role";



GRANT ALL ON TABLE "public"."messageable_contacts" TO "anon";
GRANT ALL ON TABLE "public"."messageable_contacts" TO "authenticated";
GRANT ALL ON TABLE "public"."messageable_contacts" TO "service_role";



GRANT ALL ON TABLE "public"."messages" TO "anon";
GRANT ALL ON TABLE "public"."messages" TO "authenticated";
GRANT ALL ON TABLE "public"."messages" TO "service_role";



GRANT ALL ON TABLE "public"."notifications" TO "anon";
GRANT ALL ON TABLE "public"."notifications" TO "authenticated";
GRANT ALL ON TABLE "public"."notifications" TO "service_role";



GRANT ALL ON TABLE "public"."order_receipt_view" TO "anon";
GRANT ALL ON TABLE "public"."order_receipt_view" TO "authenticated";
GRANT ALL ON TABLE "public"."order_receipt_view" TO "service_role";



GRANT ALL ON TABLE "public"."order_status_history" TO "anon";
GRANT ALL ON TABLE "public"."order_status_history" TO "authenticated";
GRANT ALL ON TABLE "public"."order_status_history" TO "service_role";



GRANT ALL ON TABLE "public"."password_resets" TO "anon";
GRANT ALL ON TABLE "public"."password_resets" TO "authenticated";
GRANT ALL ON TABLE "public"."password_resets" TO "service_role";



GRANT ALL ON TABLE "public"."payouts" TO "anon";
GRANT ALL ON TABLE "public"."payouts" TO "authenticated";
GRANT ALL ON TABLE "public"."payouts" TO "service_role";



GRANT ALL ON TABLE "public"."pending_vendors" TO "anon";
GRANT ALL ON TABLE "public"."pending_vendors" TO "authenticated";
GRANT ALL ON TABLE "public"."pending_vendors" TO "service_role";



GRANT ALL ON TABLE "public"."product_waitlist" TO "anon";
GRANT ALL ON TABLE "public"."product_waitlist" TO "authenticated";
GRANT ALL ON TABLE "public"."product_waitlist" TO "service_role";



GRANT ALL ON TABLE "public"."return_requests" TO "anon";
GRANT ALL ON TABLE "public"."return_requests" TO "authenticated";
GRANT ALL ON TABLE "public"."return_requests" TO "service_role";



GRANT ALL ON TABLE "public"."reviews" TO "anon";
GRANT ALL ON TABLE "public"."reviews" TO "authenticated";
GRANT ALL ON TABLE "public"."reviews" TO "service_role";



GRANT ALL ON TABLE "public"."search_index_queue" TO "anon";
GRANT ALL ON TABLE "public"."search_index_queue" TO "authenticated";
GRANT ALL ON TABLE "public"."search_index_queue" TO "service_role";



GRANT ALL ON TABLE "public"."stock_alerts" TO "anon";
GRANT ALL ON TABLE "public"."stock_alerts" TO "authenticated";
GRANT ALL ON TABLE "public"."stock_alerts" TO "service_role";



GRANT ALL ON TABLE "public"."student_catalog_view" TO "anon";
GRANT ALL ON TABLE "public"."student_catalog_view" TO "authenticated";
GRANT ALL ON TABLE "public"."student_catalog_view" TO "service_role";



GRANT ALL ON TABLE "public"."subscriptions" TO "anon";
GRANT ALL ON TABLE "public"."subscriptions" TO "authenticated";
GRANT ALL ON TABLE "public"."subscriptions" TO "service_role";



GRANT ALL ON TABLE "public"."system_logs" TO "anon";
GRANT ALL ON TABLE "public"."system_logs" TO "authenticated";
GRANT ALL ON TABLE "public"."system_logs" TO "service_role";



GRANT ALL ON TABLE "public"."system_settings" TO "anon";
GRANT ALL ON TABLE "public"."system_settings" TO "authenticated";
GRANT ALL ON TABLE "public"."system_settings" TO "service_role";



GRANT ALL ON TABLE "public"."user_conversations" TO "anon";
GRANT ALL ON TABLE "public"."user_conversations" TO "authenticated";
GRANT ALL ON TABLE "public"."user_conversations" TO "service_role";



GRANT ALL ON TABLE "public"."user_sessions" TO "anon";
GRANT ALL ON TABLE "public"."user_sessions" TO "authenticated";
GRANT ALL ON TABLE "public"."user_sessions" TO "service_role";



GRANT ALL ON TABLE "public"."v_loyalty_history" TO "anon";
GRANT ALL ON TABLE "public"."v_loyalty_history" TO "authenticated";
GRANT ALL ON TABLE "public"."v_loyalty_history" TO "service_role";



GRANT ALL ON TABLE "public"."v_order_tracking_timeline" TO "anon";
GRANT ALL ON TABLE "public"."v_order_tracking_timeline" TO "authenticated";
GRANT ALL ON TABLE "public"."v_order_tracking_timeline" TO "service_role";



GRANT ALL ON TABLE "public"."v_vendor_performance" TO "anon";
GRANT ALL ON TABLE "public"."v_vendor_performance" TO "authenticated";
GRANT ALL ON TABLE "public"."v_vendor_performance" TO "service_role";



GRANT ALL ON TABLE "public"."vendor_order_performance" TO "anon";
GRANT ALL ON TABLE "public"."vendor_order_performance" TO "authenticated";
GRANT ALL ON TABLE "public"."vendor_order_performance" TO "service_role";



GRANT ALL ON TABLE "public"."vendor_payouts" TO "anon";
GRANT ALL ON TABLE "public"."vendor_payouts" TO "authenticated";
GRANT ALL ON TABLE "public"."vendor_payouts" TO "service_role";



GRANT ALL ON TABLE "public"."vendor_rankings" TO "anon";
GRANT ALL ON TABLE "public"."vendor_rankings" TO "authenticated";
GRANT ALL ON TABLE "public"."vendor_rankings" TO "service_role";



GRANT ALL ON TABLE "public"."vendor_ratings" TO "anon";
GRANT ALL ON TABLE "public"."vendor_ratings" TO "authenticated";
GRANT ALL ON TABLE "public"."vendor_ratings" TO "service_role";



GRANT ALL ON TABLE "public"."vendor_stats" TO "anon";
GRANT ALL ON TABLE "public"."vendor_stats" TO "authenticated";
GRANT ALL ON TABLE "public"."vendor_stats" TO "service_role";



GRANT ALL ON TABLE "public"."wallet_transactions" TO "anon";
GRANT ALL ON TABLE "public"."wallet_transactions" TO "authenticated";
GRANT ALL ON TABLE "public"."wallet_transactions" TO "service_role";



GRANT ALL ON TABLE "public"."wallets" TO "anon";
GRANT ALL ON TABLE "public"."wallets" TO "authenticated";
GRANT ALL ON TABLE "public"."wallets" TO "service_role";



GRANT ALL ON TABLE "public"."wishlist" TO "anon";
GRANT ALL ON TABLE "public"."wishlist" TO "authenticated";
GRANT ALL ON TABLE "public"."wishlist" TO "service_role";









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";



































