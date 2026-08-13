-- Add Monthly Sales Target to Vendor Profiles
-- This allows vendors to track progress towards a monthly revenue goal

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS monthly_sales_target NUMERIC DEFAULT 100000.0;

-- Update the dashboard stats RPC to include target progress if needed,
-- but for now, we'll fetch the target from the profile.
