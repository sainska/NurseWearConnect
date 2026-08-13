-- Fix Profiles RLS to allow Catalog visibility

-- 1. Drop the overly restrictive profiles policy
DROP POLICY IF EXISTS "profiles_access_v9" ON public.profiles;
DROP POLICY IF EXISTS "profiles_universal_select" ON public.profiles;

-- 2. Create a new select policy for profiles
-- Everyone (including anon) can see active profiles
-- This is necessary for the catalog_products view to join correctly
CREATE POLICY "profiles_select_public" ON public.profiles
FOR SELECT USING (
    status = 'active'
    OR auth.uid() = id
    OR public.get_user_role(auth.uid()) = 'admin'
);

-- 3. Ensure everyone can see active products
-- (Redundant if already exists, but good for consistency)
DROP POLICY IF EXISTS "products_select_policy" ON public.products;
CREATE POLICY "products_select_policy" ON public.products
FOR SELECT USING (
    is_active = true
    OR auth.uid() = vendor_id
    OR public.get_user_role(auth.uid()) = 'admin'
);

-- 4. Re-grant select to authenticated and anon just in case
GRANT SELECT ON public.profiles TO anon, authenticated;
GRANT SELECT ON public.products TO anon, authenticated;
GRANT SELECT ON public.catalog_products TO anon, authenticated;
