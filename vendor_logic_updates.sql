-- View for vendor top selling products
CREATE OR REPLACE VIEW public.v_vendor_top_selling AS
SELECT
    p.vendor_id,
    p.id AS product_id,
    p.name AS product_name,
    p.images[1] AS product_image,
    COUNT(oi.id) AS sales_count,
    SUM(oi.unit_price * oi.quantity) AS total_revenue
FROM public.products p
JOIN public.order_items oi ON p.id = oi.product_id
JOIN public.orders o ON oi.order_id = o.id
WHERE o.status = 'delivered'
GROUP BY p.vendor_id, p.id, p.name, p.images[1];

-- Table for vendor payouts tracking
CREATE TABLE IF NOT EXISTS public.vendor_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id UUID REFERENCES auth.users(id),
    amount DECIMAL(12,2) NOT NULL,
    status TEXT DEFAULT 'pending', -- pending, processing, paid, failed
    scheduled_date TIMESTAMP WITH TIME ZONE DEFAULT (now() + interval '7 days'),
    paid_at TIMESTAMP WITH TIME ZONE,
    transaction_reference TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Function to calculate vendor balance
CREATE OR REPLACE FUNCTION public.get_vendor_balance(p_vendor_id UUID)
RETURNS DECIMAL AS $$
DECLARE
    total_earned DECIMAL;
    total_paid DECIMAL;
BEGIN
    -- Sum of delivered order items (minus 10% commission)
    SELECT SUM(unit_price * quantity * 0.9) INTO total_earned
    FROM order_items oi
    JOIN products p ON oi.product_id = p.id
    JOIN orders o ON oi.order_id = o.id
    WHERE p.vendor_id = p_vendor_id AND o.status = 'delivered';

    -- Sum of paid payouts
    SELECT SUM(amount) INTO total_paid
    FROM vendor_payouts
    WHERE vendor_id = p_vendor_id AND status = 'paid';

    RETURN COALESCE(total_earned, 0) - COALESCE(total_paid, 0);
END;
$$ LANGUAGE plpgsql;

-- Trigger to update product reviews count and average rating
CREATE OR REPLACE FUNCTION public.update_product_rating_on_review()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE public.products
    SET
        rating = (SELECT AVG(rating) FROM public.reviews WHERE product_id = NEW.product_id),
        reviews_count = (SELECT COUNT(*) FROM public.reviews WHERE product_id = NEW.product_id)
    WHERE id = NEW.product_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_update_product_rating
AFTER INSERT OR UPDATE ON public.reviews
FOR EACH ROW EXECUTE FUNCTION public.update_product_rating_on_review();
