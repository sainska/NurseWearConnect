-- Payout and Profile schema fixes for M-Pesa transfers
-- Run: supabase db push

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS paystack_mpesa_recipient_code TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS paystack_recipient_code TEXT;

-- Ensure payouts table has necessary columns for the Edge Function
ALTER TABLE public.payouts ADD COLUMN IF NOT EXISTS withdrawal_method TEXT DEFAULT 'mpesa';
ALTER TABLE public.payouts ADD COLUMN IF NOT EXISTS account_number TEXT;
