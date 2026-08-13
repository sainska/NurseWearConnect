-- Immediate Logic Updates & Inventory Lifecycle
-- Consolidated: 2026-10-30
-- NO DROP TABLE STATEMENTS USED

-- 1. Enhanced Payment Finalizer (Handles Orders & Wallet Top-ups)
CREATE OR REPLACE FUNCTION public.finalize_successful_payment(
    p_order_id UUID,
    p_transaction_id TEXT,
    p_amount NUMERIC,
    p_method TEXT,
    p_response JSONB,
    p_user_id UUID DEFAULT NULL
) RETURNS VOID AS $$
BEGIN
    -- Handle Order Payment
    IF p_order_id IS NOT NULL THEN
        INSERT INTO public.payments (order_id, transaction_id, amount, payment_method, status, provider_response)
        VALUES (p_order_id, p_transaction_id, p_amount, p_method, 'completed', p_response)
        ON CONFLICT (transaction_id) DO UPDATE SET
            status = 'completed',
            provider_response = p_response,
            updated_at = now();

        UPDATE public.orders
        SET
            payment_status = 'paid',
            status = 'processing',
            payment_id = p_transaction_id,
            payment_method = p_method,
            updated_at = now()
        WHERE id = p_order_id;
    END IF;

    -- Handle Wallet Top-up (if p_user_id provided and p_order_id is null)
    IF p_user_id IS NOT NULL AND p_order_id IS NULL THEN
        UPDATE public.wallets
        SET balance = balance + p_amount, updated_at = now()
        WHERE user_id = p_user_id;

        INSERT INTO public.wallet_transactions (wallet_id, amount, type, reference_type, reference_id, description)
        SELECT id, p_amount, 'credit', 'topup', NULL, 'Wallet Top-up via ' || p_method
        FROM public.wallets WHERE user_id = p_user_id;

        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (p_user_id, 'WALLET_TOPUP_SUCCESS', jsonb_build_object('transaction_id', p_transaction_id, 'amount', p_amount), 'info');
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Inventory Return on Cancellation Trigger
CREATE OR REPLACE FUNCTION public.fn_handle_order_item_cancellation()
RETURNS TRIGGER AS $$
BEGIN
    -- Return stock if item status changed to 'cancelled' or 'returned'
    IF (NEW.status IN ('cancelled', 'returned') AND OLD.status NOT IN ('cancelled', 'returned')) THEN
        UPDATE public.products
        SET stock_count = stock_count + NEW.quantity
        WHERE id = NEW.product_id;

        -- Log the stock return
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (
            (SELECT user_id FROM public.orders WHERE id = NEW.order_id),
            'STOCK_RETURNED',
            jsonb_build_object('product_id', NEW.product_id, 'quantity', NEW.quantity, 'reason', NEW.status, 'order_id', NEW.order_id),
            'info'
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_handle_order_item_cancellation ON public.order_items;
CREATE TRIGGER tr_handle_order_item_cancellation
AFTER UPDATE OF status ON public.order_items
FOR EACH ROW EXECUTE FUNCTION public.fn_handle_order_item_cancellation();

-- 3. Profile Status Change Logger (Immediate Insight)
CREATE OR REPLACE FUNCTION public.fn_log_profile_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.status IS DISTINCT FROM NEW.status) THEN
        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (NEW.id, 'PROFILE_STATUS_CHANGE', jsonb_build_object('old_status', OLD.status, 'new_status', NEW.status), 'info');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_log_profile_status_change ON public.profiles;
CREATE TRIGGER tr_log_profile_status_change
AFTER UPDATE OF status ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.fn_log_profile_status_change();

-- 4. Automatically Sync Order Status based on Items (Immediate Consistency)
-- (Already exists in master schema, but ensured here for completeness of 'Immediate Logic')
CREATE OR REPLACE FUNCTION public.fn_sync_order_status_from_items_v2() RETURNS TRIGGER AS $$
DECLARE
    v_total_items integer;
    v_delivered_items integer;
    v_cancelled_items integer;
    v_processing_items integer;
    v_new_status text;
BEGIN
    SELECT COUNT(*), COUNT(*) FILTER (WHERE status = 'delivered'), COUNT(*) FILTER (WHERE status = 'cancelled'), COUNT(*) FILTER (WHERE status = 'processing' OR status = 'shipped')
    INTO v_total_items, v_delivered_items, v_cancelled_items, v_processing_items
    FROM public.order_items WHERE order_id = NEW.order_id;

    IF v_total_items = v_delivered_items THEN
        v_new_status := 'delivered';
    ELSIF v_total_items = v_cancelled_items THEN
        v_new_status := 'cancelled';
    ELSIF v_processing_items > 0 THEN
        v_new_status := 'processing';
    ELSE
        v_new_status := 'pending';
    END IF;

    UPDATE public.orders SET status = v_new_status, updated_at = now() WHERE id = NEW.order_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_sync_order_status_from_items ON public.order_items;
CREATE TRIGGER tr_sync_order_status_from_items
AFTER UPDATE OF status ON public.order_items
FOR EACH ROW EXECUTE FUNCTION public.fn_sync_order_status_from_items_v2();

-- Log the implementation of Immediate Logic
INSERT INTO public.system_logs (action, details, severity)
VALUES ('SYSTEM_LOGIC_UPDATE', '{"features": ["inventory_return", "wallet_topup_finalization", "status_propagation"]}', 'info');
