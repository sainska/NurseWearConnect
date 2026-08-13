-- Robust Inventory Lifecycle Management

-- 1. Function to handle stock updates based on order item lifecycle
CREATE OR REPLACE FUNCTION public.manage_product_stock_lifecycle()
RETURNS TRIGGER AS $$
BEGIN
    -- CASE 1: New order item created (Initial Purchase)
    IF (TG_OP = 'INSERT') THEN
        -- Subtract stock
        UPDATE public.products
        SET stock_count = GREATEST(0, stock_count - NEW.quantity)
        WHERE id = NEW.product_id;

        RETURN NEW;
    END IF;

    -- CASE 2: Order item status updated
    IF (TG_OP = 'UPDATE') THEN
        -- If status changed TO 'cancelled' (and it wasn't already cancelled)
        IF (NEW.status = 'cancelled' AND OLD.status <> 'cancelled') THEN
            UPDATE public.products
            SET stock_count = stock_count + NEW.quantity
            WHERE id = NEW.product_id;

            -- Log the stock restoration
            INSERT INTO public.system_logs (action, details, severity)
            VALUES ('STOCK_RESTORED', jsonb_build_object('product_id', NEW.product_id, 'quantity', NEW.quantity, 'reason', 'Order item cancelled'), 'info');

        -- If status changed FROM 'cancelled' back to something active (Rare edge case)
        ELSIF (OLD.status = 'cancelled' AND NEW.status <> 'cancelled') THEN
            UPDATE public.products
            SET stock_count = GREATEST(0, stock_count - NEW.quantity)
            WHERE id = NEW.product_id;
        END IF;

        RETURN NEW;
    END IF;

    -- CASE 3: Order item deleted (Optional, usually we just cancel)
    IF (TG_OP = 'DELETE') THEN
        -- Restore stock if the item wasn't already cancelled
        IF (OLD.status <> 'cancelled') THEN
            UPDATE public.products
            SET stock_count = stock_count + OLD.quantity
            WHERE id = OLD.product_id;
        END IF;

        RETURN OLD;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Apply lifecycle trigger to order_items
DROP TRIGGER IF EXISTS tr_manage_order_item_stock ON public.order_items;
CREATE TRIGGER tr_manage_order_item_stock
    AFTER INSERT OR UPDATE OR DELETE ON public.order_items
    FOR EACH ROW EXECUTE FUNCTION public.manage_product_stock_lifecycle();

-- 2. Ensure Order cancellation cascades to all items
CREATE OR REPLACE FUNCTION public.cascade_order_cancellation()
RETURNS TRIGGER AS $$
BEGIN
    -- If order is marked as 'cancelled'
    IF (NEW.status = 'cancelled' AND OLD.status <> 'cancelled') THEN
        -- Mark all non-cancelled items as cancelled
        -- This will trigger tr_manage_order_item_stock for each item
        UPDATE public.order_items
        SET status = 'cancelled'
        WHERE order_id = NEW.id AND status <> 'cancelled';

        -- Log the bulk restoration
        INSERT INTO public.system_logs (action, details, severity)
        VALUES ('ORDER_CANCELLED_STOCK_SYNC', jsonb_build_object('order_id', NEW.id, 'notes', 'Order status set to cancelled, restoring stock for all items'), 'info');
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply cascade trigger to orders
DROP TRIGGER IF EXISTS tr_cascade_order_cancellation ON public.orders;
CREATE TRIGGER tr_cascade_order_cancellation
    AFTER UPDATE OF status ON public.orders
    FOR EACH ROW EXECUTE FUNCTION public.cascade_order_cancellation();

-- 3. Automatic 'in_stock' status sync based on stock_count
CREATE OR REPLACE FUNCTION public.sync_product_in_stock_status()
RETURNS TRIGGER AS $$
BEGIN
    NEW.in_stock := (NEW.stock_count > 0);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_product_in_stock ON public.products;
CREATE TRIGGER tr_sync_product_in_stock
    BEFORE INSERT OR UPDATE OF stock_count ON public.products
    FOR EACH ROW EXECUTE FUNCTION public.sync_product_in_stock_status();

-- 4. Initial sync for existing data (Safety)
UPDATE public.products
SET in_stock = (stock_count > 0);
