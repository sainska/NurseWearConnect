-- Logic for Deferred Order Creation (Place order only after payment)
-- Run: supabase db push

-- 1. Updated RPC to handle order creation from payment metadata if order_id is missing
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
    v_new_order_id UUID;
    v_metadata JSONB;
    v_item JSONB;
    v_product_id UUID;
    v_vendor_id UUID;
    v_item_vendor_id UUID;
BEGIN
    -- 1. Determine User ID
    IF p_user_id IS NOT NULL THEN
        v_actual_user_id := p_user_id;
    ELSIF p_order_id IS NOT NULL THEN
        SELECT user_id INTO v_actual_user_id FROM public.orders WHERE id = p_order_id;
    ELSE
        -- Try to find in payment record metadata
        SELECT user_id, metadata INTO v_actual_user_id, v_metadata
        FROM public.payments WHERE transaction_id = p_transaction_id;
    END IF;

    -- 2. Update Payment Record status to completed
    UPDATE public.payments
    SET status = 'completed',
        provider_response = p_response,
        updated_at = now()
    WHERE transaction_id = p_transaction_id;

    -- 3. Case A: Order already exists (standard flow)
    IF p_order_id IS NOT NULL THEN
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

    -- 4. Case B: Deferred Order Creation (Place order NOW)
    ELSIF v_metadata IS NOT NULL AND (v_metadata->>'type' = 'order_payment' OR v_metadata->>'type' IS NULL) AND (v_metadata ? 'cart_items') THEN
        -- Extract order details from metadata
        -- Expected structure in metadata: { cart_items: [...], shipping_address: "...", discount_amount: 0, vendor_id: "..." }

        v_vendor_id := (v_metadata->>'vendor_id')::UUID;

        INSERT INTO public.orders (
            user_id, vendor_id, total_amount, discount_amount, final_amount,
            status, payment_status, payment_method, payment_id,
            shipping_address, shipping_method, currency, is_fitting_service, digital_receipt_enabled
        ) VALUES (
            v_actual_user_id,
            v_vendor_id,
            (v_metadata->>'total_amount')::NUMERIC,
            COALESCE((v_metadata->>'discount_amount')::NUMERIC, 0),
            p_amount,
            'processing',
            'paid',
            p_method,
            p_transaction_id,
            v_metadata->>'shipping_address',
            COALESCE(v_metadata->>'shipping_method', 'Standard'),
            'KES',
            COALESCE((v_metadata->>'is_fitting_service')::BOOLEAN, FALSE),
            COALESCE((v_metadata->>'digital_receipt_enabled')::BOOLEAN, TRUE)
        ) RETURNING id INTO v_new_order_id;

        -- Link the payment record to the new order
        UPDATE public.payments SET order_id = v_new_order_id WHERE transaction_id = p_transaction_id;

        -- Insert Order Items from metadata
        FOR v_item IN SELECT * FROM jsonb_array_elements(v_metadata->'cart_items')
        LOOP
            v_product_id := (v_item->>'product_id')::UUID;
            -- Fetch vendor_id for this product if not in item payload
            SELECT vendor_id INTO v_item_vendor_id FROM public.products WHERE id = v_product_id;

            INSERT INTO public.order_items (
                order_id, product_id, vendor_id, quantity, unit_price,
                size, color, status, embroidery_name, has_embroidery
            ) VALUES (
                v_new_order_id,
                v_product_id,
                COALESCE(v_item_vendor_id, v_vendor_id),
                (v_item->>'quantity')::INTEGER,
                (v_item->>'unit_price')::NUMERIC,
                v_item->>'size',
                v_item->>'color',
                'pending',
                v_item->>'embroidery_name',
                (v_item->>'embroidery_name' IS NOT NULL)
            );
        END LOOP;

        -- Log Success
        INSERT INTO public.system_logs (user_id, action, details)
        VALUES (v_actual_user_id, 'ORDER_CREATED_AFTER_PAYMENT', jsonb_build_object('order_id', v_new_order_id, 'amount', p_amount));

    -- 5. Case C: Wallet Top-up
    ELSIF v_actual_user_id IS NOT NULL AND (v_metadata->>'type' = 'wallet_topup') THEN
        UPDATE public.wallets
        SET balance = balance + p_amount, updated_at = now()
        WHERE user_id = v_actual_user_id;

        INSERT INTO public.wallet_transactions (wallet_id, amount, type, reference_type, reference_id, description)
        SELECT id, p_amount, 'credit', 'topup', NULL, 'Wallet Top-up via ' || p_method
        FROM public.wallets WHERE user_id = v_actual_user_id;

        INSERT INTO public.system_logs (user_id, action, details, severity)
        VALUES (v_actual_user_id, 'WALLET_TOPUP_SUCCESS', jsonb_build_object('transaction_id', p_transaction_id, 'amount', p_amount), 'info');
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
