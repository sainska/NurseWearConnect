# Paystack & Order Management - 20 Logic Points

1.  [DONE] **Environment Validation**: Ensure `PAYSTACK_SECRET_KEY` is present in Supabase Secrets. (Verified via `paystack-test`)
2.  [DONE] **Connectivity Health Check**: Ping `api.paystack.co` to verify outbound networking. (Verified via `paystack-health`)
3.  [DONE] **Idempotent Verification**: Prevent duplicate payment processing using `transaction_id` unique constraints. (Implemented in `finalize_successful_payment` RPC)
4.  [DONE] **Metadata Reconciliation**: Pass `order_id` in Paystack metadata for backend mapping. (Implemented in `paystack-initialize`)
5.  [DONE] **Amount Unit Safety**: Convert subunit integers (cents) to standard decimal values in DB. (Implemented in Edge Functions)
6.  [DONE] **Atomic Order Updates**: Transition order status to 'paid' and 'processing' in one transaction. (Implemented via `finalize_successful_payment` RPC)
7.  [DONE] **Webhook Signature Security**: Verify `x-paystack-signature` using HMAC SHA-512. (Implemented in `paystack-webhook/index.ts`)
8.  [DONE] **Automated Audit Logging**: Use `SECURITY DEFINER` functions to log payment events bypassing RLS. (Implemented via `finalize_successful_payment` RPC)
9.  [DONE] **Fail-Safe Error Handling**: Return detailed error codes to the mobile app for UX. (Implemented in all Paystack Edge Functions)
10. [DONE] **Database Trigger Synchronization**: Automatic `orders` update on `payments` table changes. (Implemented via `tr_on_payment_completed`)
11. [DONE] **Vendor Partial Fulfillment**: Allow vendors to mark individual items as 'shipped' while the main order is 'processing'. (Implemented via `tr_sync_order_status_from_items` trigger)
12. [DONE] **Admin Global Override**: Admins can force-update any order status (e.g., to 'refunded' or 'cancelled') regardless of payment state. (Implemented via `admin_force_update_order` RPC)
13. [DONE] **Customer Refund Logic**: Automated Paystack refund initiation via Edge Function when an admin cancels a paid order. (Implemented in `paystack-refund` function and Admin UI)
14. [DONE] **Stock Reversion on Cancellation**: Automatically increment `stock_count` in `products` if an order is cancelled before shipping. (Implemented in SQL triggers)
15. [DONE] **Multi-Vendor Split**: Logic to identify which items belong to which vendor for dashboard filtering. (Implemented via `!inner` join filtering in `ApiService.kt`)
16. [DONE] **Real-Time Tracking Webhooks**: Integrate shipping provider webhooks (simulated) to update 'transit' status. (Implemented in `shipping-webhook` function)
17. [DONE] **Conditional RLS for Roles**: Users see only their orders; Vendors see orders containing their products; Admins see all. (Implemented in RLS policy updates)
18. [DONE] **Duplicate Order Prevention**: Frontend/Backend debounce logic to prevent double-click order creation. (Handled via `checkoutLoading` state)
19. [DONE] **Currency Consistency**: Enforce KES across all payment gateways and internal reporting. (Verified KES enforcement)
20. [DONE] **Fulfillment Notifications**: Trigger FCM notifications to users when Vendor changes status to 'shipped'. (Implemented in `VendorRepository.kt`)

---
**Verified Real Payment Readiness**:
- [x] Live Secret Key connected.
- [x] Deep link callback `nursewear://checkout` verified.
- [x] Webhook signature verification verified.
- [x] Atomic status transitions via RPC verified.
- [x] Success/Error state propagation to UI verified.
- [x] Full Refund & Partial Fulfillment logic implemented.
