-- Orders and Loyalty Automation

-- 1. Automate Order Status History
CREATE OR REPLACE FUNCTION public.handle_order_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.status IS NULL OR OLD.status <> NEW.status) THEN
        INSERT INTO public.order_status_history (order_id, status, notes)
        VALUES (NEW.id, NEW.status, 'Order status updated to ' || NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS on_order_status_change ON public.orders;
CREATE TRIGGER on_order_status_change
    AFTER UPDATE OF status ON public.orders
    FOR EACH ROW EXECUTE FUNCTION public.handle_order_status_change();

-- 2. Reward Loyalty Points on Order Delivery
CREATE OR REPLACE FUNCTION public.reward_points_on_delivery()
RETURNS TRIGGER AS $$
DECLARE
    points_to_add INTEGER;
BEGIN
    -- Only reward when status changes to 'delivered'
    IF NEW.status = 'delivered' AND OLD.status <> 'delivered' THEN
        -- Reward 1 point for every 100 KES spent (using final_amount)
        points_to_add := FLOOR(NEW.final_amount / 100);

        IF points_to_add > 0 THEN
            -- Update user points
            UPDATE public.profiles
            SET loyalty_points = loyalty_points + points_to_add
            WHERE id = NEW.user_id;

            -- Log to history
            INSERT INTO public.loyalty_history (user_id, points_change, reason)
            VALUES (NEW.user_id, points_to_add, 'Order #' || NEW.id || ' completed');

            -- Create notification
            INSERT INTO public.notifications (user_id, title, body, category)
            VALUES (NEW.user_id, 'Loyalty Points Earned!', 'You earned ' || points_to_add || ' points from your recent purchase.', 'reward');
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS on_order_delivered_reward ON public.orders;
CREATE TRIGGER on_order_delivered_reward
    AFTER UPDATE OF status ON public.orders
    FOR EACH ROW EXECUTE FUNCTION public.reward_points_on_delivery();

-- 3. Ensure all relevant tables are in Realtime publication
DO $$
DECLARE
    t TEXT;
BEGIN
    FOR t IN SELECT unnest(ARRAY['profiles', 'wallets', 'notifications', 'orders', 'order_items', 'order_status_history'])
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = t) THEN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
        END IF;
    END LOOP;
END $$;

-- 4. RPC for the Size Finder logic (Optional but good for shared logic)
CREATE OR REPLACE FUNCTION public.get_recommended_size(p_gender TEXT, p_bust FLOAT, p_waist FLOAT, p_hips FLOAT)
RETURNS TEXT AS $$
DECLARE
    v_size TEXT := 'M';
BEGIN
    -- Simple logic mirrored from app SizeFinder.kt
    IF p_gender ILIKE 'male' THEN
        IF p_bust < 36 THEN v_size := 'XS';
        ELSIF p_bust < 39 THEN v_size := 'S';
        ELSIF p_bust < 42 THEN v_size := 'M';
        ELSIF p_bust < 46 THEN v_size := 'L';
        ELSE v_size := 'XL';
        END IF;
    ELSE
        IF p_bust < 33 THEN v_size := 'XXS';
        ELSIF p_bust < 35 THEN v_size := 'XS';
        ELSIF p_bust < 37 THEN v_size := 'S';
        ELSIF p_bust < 40 THEN v_size := 'M';
        ELSIF p_bust < 44 THEN v_size := 'L';
        ELSE v_size := 'XL';
        END IF;
    END IF;
    RETURN v_size;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 5. Logging success
INSERT INTO public.system_logs (action, details, severity)
VALUES ('MIGRATION_SUCCESS', '{"migration": "20241124_orders_and_loyalty_automation", "status": "applied"}', 'info');
