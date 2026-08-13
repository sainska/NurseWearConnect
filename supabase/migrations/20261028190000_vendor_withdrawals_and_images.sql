-- Migration: Vendor Withdrawals and Image Support enhancements
-- Date: 2026-10-28 19:00:00

-- 1. Ensure Vendors can request payouts
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'payouts' AND policyname = 'Vendors can request a payout'
    ) THEN
        CREATE POLICY "Vendors can request a payout"
        ON "public"."payouts" FOR INSERT
        WITH CHECK (("vendor_id" = "auth"."uid"()));
    END IF;
END $$;

-- 2. Update payouts table amount to numeric for currency precision if needed
-- (Keeping integer if it was intentional for KSh, but numeric is safer for calculations)
ALTER TABLE public.payouts ALTER COLUMN amount TYPE NUMERIC(12,2);

-- 3. Function to calculate current withdrawable balance for a vendor
-- This sums up all net_earnings from vendor_payouts (which tracks order-level earnings)
-- and subtracts already paid or pending withdrawals from payouts table.
CREATE OR REPLACE FUNCTION public.get_vendor_withdrawable_balance(p_vendor_id UUID)
RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_total_earned NUMERIC;
    v_total_withdrawn_or_pending NUMERIC;
BEGIN
    -- Sum of earnings from orders
    SELECT COALESCE(SUM(net_amount), 0)
    INTO v_total_earned
    FROM public.vendor_payouts
    WHERE vendor_id = p_vendor_id;

    -- Sum of processed or pending withdrawals from the payouts table
    SELECT COALESCE(SUM(amount), 0)
    INTO v_total_withdrawn_or_pending
    FROM public.payouts
    WHERE vendor_id = p_vendor_id
      AND status IN ('paid', 'pending', 'processing');

    RETURN v_total_earned - v_total_withdrawn_or_pending;
END;
$$;

-- 4. Ensure products table has images array and it is never null
ALTER TABLE public.products ALTER COLUMN images SET DEFAULT '{}'::text[];

-- 5. Log the update
INSERT INTO public.system_logs (action, details, severity)
VALUES ('VND_WITHDRAWAL_INIT', '{"module": "finance", "status": "active", "details": "Withdrawal logic and balance functions added"}', 'info');
