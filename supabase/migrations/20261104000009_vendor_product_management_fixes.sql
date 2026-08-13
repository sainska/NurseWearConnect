-- Enhancements for Vendor Product Management and Storage Access
-- Run: supabase db push

-- 1. Ensure Product RLS is robust for Vendors
DROP POLICY IF EXISTS "products_insert_policy" ON public.products;
CREATE POLICY "products_insert_policy" ON public.products
FOR INSERT WITH CHECK (
    auth.uid() = vendor_id
    AND (public.get_user_role(auth.uid()) = 'vendor' OR public.is_admin())
);

DROP POLICY IF EXISTS "products_update_policy" ON public.products;
CREATE POLICY "products_update_policy" ON public.products
FOR UPDATE USING (
    auth.uid() = vendor_id
    OR public.is_admin()
)
WITH CHECK (
    auth.uid() = vendor_id
    OR public.is_admin()
);

DROP POLICY IF EXISTS "products_delete_policy" ON public.products;
CREATE POLICY "products_delete_policy" ON public.products
FOR DELETE USING (
    auth.uid() = vendor_id
    OR public.is_admin()
);

-- 2. Storage Policies for 'product-images' bucket
-- Allow vendors to upload to their own folder in 'product-images'
-- The path in app is: {user_id}/{filename}

DROP POLICY IF EXISTS "Vendors can upload product images" ON storage.objects;
CREATE POLICY "Vendors can upload product images" ON storage.objects
FOR INSERT WITH CHECK (
    bucket_id = 'product-images'
    AND (auth.uid()::text = (storage.foldername(name))[1])
);

DROP POLICY IF EXISTS "Vendors can update own product images" ON storage.objects;
CREATE POLICY "Vendors can update own product images" ON storage.objects
FOR UPDATE USING (
    bucket_id = 'product-images'
    AND (auth.uid()::text = (storage.foldername(name))[1])
);

DROP POLICY IF EXISTS "Vendors can delete own product images" ON storage.objects;
CREATE POLICY "Vendors can delete own product images" ON storage.objects
FOR DELETE USING (
    bucket_id = 'product-images'
    AND (auth.uid()::text = (storage.foldername(name))[1])
);

DROP POLICY IF EXISTS "Public access to product images" ON storage.objects;
CREATE POLICY "Public access to product images" ON storage.objects
FOR SELECT USING (bucket_id = 'product-images');

-- 3. Ensure Banners are also manageable by vendors (if not already)
DROP POLICY IF EXISTS "banners_vendor_insert" ON public.banners;
CREATE POLICY "banners_vendor_insert" ON public.banners
FOR INSERT WITH CHECK (
    auth.uid() = vendor_id
    AND public.get_user_role(auth.uid()) = 'vendor'
);

-- 4. Audit Log
INSERT INTO public.system_logs (user_id, action, details, severity)
VALUES (auth.uid(), 'SECURITY_UPDATE', '{"module": "products", "description": "Fixed Vendor Product Management and Storage RLS."}', 'info');
