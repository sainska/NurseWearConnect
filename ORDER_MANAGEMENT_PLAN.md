# NurseWear Connect: Order Management Differentiation Plan

## 1. Core Philosophy
*   **Admins**: Manage the **Financial & System Lifecycle**. They oversee total amounts, payment status, refunds, and global order health.
*   **Vendors**: Manage the **Fulfillment Lifecycle**. They focus on preparing, shipping, and delivering specific items they own within an order.

## 2. Technical Architecture: Item-Level Fulfillment
In a multi-vendor marketplace, an order is a container for items from multiple sources.
*   `orders` Table: Tracks `payment_status`, `total_amount`, and `global_status`.
*   `order_items` Table: Tracks `vendor_id`, `item_status`, and `fulfillment_data`.

### Status Aggregation Logic (Trigger-based)
*   If **any** item is `processing` -> Order becomes `processing`.
*   If **all** items are `shipped` -> Order becomes `shipped`.
*   If **all** items are `delivered` -> Order becomes `delivered`.
*   If **any** item is `cancelled` -> Admin is notified; Order remains `processing` unless all are cancelled.

## 3. Screen Enhancements

### Vendor Screen (`VendorOrdersScreen.kt`)
- [x] **Data Isolation**: Vendors only see items assigned to them.
- [x] **Sub-totaling**: Dashboard shows the vendor's share (items * price) rather than the order total.
- [x] **Item Actions**: Buttons for "Mark Shipped" and "Mark Delivered" apply specifically to the items they own.
- [x] **Order Status**: Derived from their items (e.g., if their items are shipped, the order looks 'Shipped' to them even if other vendors are pending).

### Admin Screen (`AdminOrderManagementScreen.kt`)
- [ ] **Vendor Grouping**: In order details, items are grouped by vendor name.
- [ ] **Financial Governance**: Actions to release funds to specific vendors after items are marked 'delivered'.
- [ ] **Global Override**: Admin can manually set the status of any item or the whole order in case of disputes.

## 4. SQL Logic Updates
- **RLS**: Restrict `order_items` UPDATE to `vendor_id = auth.uid()`.
- **Triggers**: `sync_order_status_from_items` handles the global status flow.
- **Views**: `vendor_revenue_report` to calculate earnings minus commission.

## 5. Implementation Steps
1.  **UI Sync**: Update `AdminOrderManagementScreen` to show item fulfillment progress across all vendors.
2.  **SQL Migration**: Deploy the `differentiate_order_management_logic` script (Refined version).
3.  **Notification Logic**: Automate "Item Shipped" alerts to customers.
