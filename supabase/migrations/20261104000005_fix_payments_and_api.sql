-- Fix payments table and finalizer logic for better API compatibility
-- Run: supabase db push

-- 1. Enhance payments table
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL;
ALTER TABLE public.payments ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}'::jsonb;

-- 2. Update finalize_successful_payment to capture user_id
CREATE OR REPLACE FUNCTION public.finalize_successful_payment(
    p_order_id UUID,
    p_transaction_id TEXT,
    p_amount NUMERIC,
    p_method TEXT,
    p_response JSONB,
    p_user_id UUID DEFAULT NULL
) RETURNS VOID AS $$
DECLARE
    v_actual_user_id UUID;
BEGIN
    -- Determine User ID
    IF p_user_id IS NOT NULL THEN
        v_actual_user_id := p_user_id;
    ELSIF p_order_id IS NOT NULL THEN
        SELECT user_id INTO v_actual_user_id FROM public.orders WHERE id = p_order_id;
    END IF;

    -- Handle Order Payment
    IF p_order_id IS NOT NULL THEN
        INSERT INTO public.payments (order_id, user_id, transaction_id, amount, payment_method, status, provider_response)
        VALUES (p_order_id, v_actual_user_id, p_transaction_id, p_amount, p_method, 'completed', p_response)
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

        -- Log Success
        INSERT INTO public.system_logs (user_id, action, details)
        VALUES (v_actual_user_id, 'ORDER_PAYMENT_SUCCESS', jsonb_build_object('order_id', p_order_id, 'amount', p_amount));
    END IF;

    -- Handle Wallet Top-up (if p_order_id is null)
    IF p_order_id IS NULL AND v_actual_user_id IS NOT NULL THEN
        UPDATE public.wallets
        SET balance = balance + p_amount, updated_at = now()
        WHERE user_id = v_actual_user_id;

        INSERT INTO public.wallet_transactions (wallet_id, amount, type, reference_type, reference_id, description)
        SELECT id, p_amount, 'credit', 'topup', NULL, 'Wallet Top-up via ' || p_method
        FROM public.wallets WHERE user_id = v_actual_user_id;

        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (v_actual_user_id, 'WALLET_TOPUP_SUCCESS', jsonb_build_object('transaction_id', p_transaction_id, 'amount', p_amount), 'info');

        -- Record in payments table as well
        INSERT INTO public.payments (user_id, transaction_id, amount, payment_method, status, provider_response)
        VALUES (v_actual_user_id, p_transaction_id, p_amount, p_method, 'completed', p_response)
        ON CONFLICT (transaction_id) DO UPDATE SET status = 'completed';
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Ensure extensions are active
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_net;

-- 4. Fix potential permission issues for Edge Functions
GRANT ALL ON TABLE public.payments TO service_role;
GRANT ALL ON TABLE public.password_resets TO service_role;
