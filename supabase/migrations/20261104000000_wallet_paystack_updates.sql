-- Wallet and Paystack Integration Updates
-- Run: supabase db push

-- 1. Enhance Payouts table for withdrawals
ALTER TABLE public.payouts ADD COLUMN IF NOT EXISTS account_number TEXT;
ALTER TABLE public.payouts ADD COLUMN IF NOT EXISTS withdrawal_method TEXT DEFAULT 'mpesa';

-- 2. Function to handle wallet top-ups (Internal/Edge Function use)
CREATE OR REPLACE FUNCTION public.top_up_wallet(
    p_user_id UUID,
    p_amount NUMERIC,
    p_reference TEXT,
    p_description TEXT DEFAULT 'Wallet Top-up'
)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_wallet_id UUID;
    v_new_balance NUMERIC;
BEGIN
    -- Ensure wallet exists
    INSERT INTO public.wallets (user_id, balance)
    VALUES (p_user_id, 0.0)
    ON CONFLICT (user_id) DO NOTHING;

    SELECT id INTO v_wallet_id FROM public.wallets WHERE user_id = p_user_id;

    -- Update Balance
    UPDATE public.wallets
    SET balance = balance + p_amount,
        updated_at = now()
    WHERE id = v_wallet_id
    RETURNING balance INTO v_new_balance;

    -- Record Transaction
    INSERT INTO public.wallet_transactions (
        wallet_id,
        amount,
        type,
        reference_type,
        reference_id,
        description
    ) VALUES (
        v_wallet_id,
        p_amount,
        'credit',
        'topup',
        NULL,
        p_description || ' (Ref: ' || p_reference || ')'
    );

    -- Log Action
    INSERT INTO public.system_logs (user_id, action, details)
    VALUES (p_user_id, 'WALLET_TOPUP_SUCCESS', jsonb_build_object('amount', p_amount, 'reference', p_reference));

    RETURN jsonb_build_object('success', true, 'new_balance', v_new_balance);
END;
$$;

-- 3. Trigger to deduct from wallet balance when a withdrawal (payout) is requested
CREATE OR REPLACE FUNCTION public.fn_process_withdrawal_request()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_wallet_balance NUMERIC;
BEGIN
    -- Lock wallet for update
    SELECT balance INTO v_wallet_balance FROM public.wallets WHERE user_id = NEW.vendor_id FOR UPDATE;

    IF v_wallet_balance IS NULL OR v_wallet_balance < NEW.amount THEN
        RAISE EXCEPTION 'Insufficient wallet balance for withdrawal. Required: %, Available: %', NEW.amount, COALESCE(v_wallet_balance, 0);
    END IF;

    -- Deduct from wallet balance immediately to prevent double spending
    UPDATE public.wallets
    SET balance = balance - NEW.amount,
        updated_at = now()
    WHERE user_id = NEW.vendor_id;

    -- Record the debit transaction
    INSERT INTO public.wallet_transactions (
        wallet_id,
        amount,
        type,
        reference_type,
        reference_id,
        description
    )
    SELECT id, NEW.amount, 'debit', 'payout', NEW.id, 'Withdrawal request initiated to ' || COALESCE(NEW.account_number, 'M-Pesa')
    FROM public.wallets
    WHERE user_id = NEW.vendor_id;

    RETURN NEW;
END;
$$;

-- 4. Apply trigger to payouts table
DROP TRIGGER IF EXISTS tr_process_withdrawal_request ON public.payouts;
CREATE TRIGGER tr_process_withdrawal_request
    BEFORE INSERT ON public.payouts
    FOR EACH ROW
    WHEN (NEW.status = 'pending')
    EXECUTE FUNCTION public.fn_process_withdrawal_request();

-- 5. Add RLS policy to allow users to view their own payouts
ALTER TABLE public.payouts ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own payouts" ON public.payouts
    FOR SELECT USING (auth.uid() = vendor_id);

-- 6. Grant execute permissions to service role (for Edge Functions)
GRANT EXECUTE ON FUNCTION public.top_up_wallet(UUID, NUMERIC, TEXT, TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.process_wallet_payment(UUID, UUID, NUMERIC) TO service_role;
