# NurseWearConnect Enterprise Phase 2: Logic & Orders Transformation

## 20 Logic Enhancement Todos (Immediate Implementation)
1. [x] **Student Order View**: Implement `OrdersScreen.kt` for students with tabbed views (Active, Completed, Cancelled).
2. [x] **Real-time Order Tracking**: Integrate timeline-based tracking UI using the `order_status_history` table.
3. [x] **Vendor Product Filtering**: Update vendor orders logic to ONLY show orders containing products owned by the vendor.
4. [x] **Multi-Vendor Order Splitting**: Logic to handle single consumer orders containing items from multiple vendors.
5. [x] **Optimistic Tracking Updates**: Allow UI to reflect status changes immediately while sync occurs in background.
6. [x] **Student Home Cleanup**: Remove "Recent Updates" from `HomeScreen.kt` for students to focus on shopping/wallet.
7. [x] **Admin Global Order Dashboard**: Implement searchable, filterable master order list for system governance.
8. [x] **Automated PDF Invoicing**: Trigger server-side PDF generation on order completion (Invoices table and triggers).
9. [x] **Inventory Auto-Correction**: Deduct stock on payment success; restore on cancellation (SQL Triggers).
10. [x] **Push Notification Bridge**: Connect `notifications` table to `fcm-pusher` Edge Function for real-time alerts.
11. [x] **Wallet-Only Checkout**: Logic to prioritize wallet balance over external payment methods if funds are sufficient.
12. [x] **Loyalty Point Calculation**: Logic to award 1% of order value as points upon "Delivered" status (SQL Trigger).
13. [x] **Address Book Management**: CRUD operations for student delivery addresses within the checkout flow.
14. [x] **Return/Refund Workflow**: UI and logic for initiating returns within 7 days of delivery.
15. [x] **Vendor Payout Logic**: Automated calculation of vendor earnings minus commission after order completion.
16. [x] **Smart Search for Orders**: Filter orders by ID, Product Name, or Date Range in Admin Dashboard.
17. [x] **Bulk Order Actions for Admin**: Allow admin to batch-update order statuses (e.g., mark multiple as "In Transit").
18. [x] **Delivery Partner Integration**: Placeholder for tracking IDs from third-party logistics providers.
19. [x] **Session-Based Cart Persistence**: Ensure cart survives app restarts using Room/Supabase sync.
20. [x] **Home Fitting Service Integration**: End-to-end logic for "Try Before You Buy" with appointment scheduling and conflict-aware cart sync.

---

## 50 Enterprise System Ideas (The Jumia/E-commerce North Star)

### Student (Consumer) Role
1. [x] **Visual Search**: Upload a photo of scrubs to find similar items.
2. [x] **Flash Sale Countdown**: Real-time tickers for limited-time offers.
3. [x] **Waitlist/Restock Alerts**: Notify users when an out-of-stock item is back.
4. [x] **Bundle Discounts**: "Buy the set" (top + bottom) for 10% less.
5. [x] **Referral Program**: "Invite a nurse" and get KSh 500 wallet credit.
6. [x] **Subscription Scrubs**: Monthly delivery for high-wear environments.
7. [x] **Try Before You Buy**: (Enterprise Logistics) Home fitting service.
8. [x] **Community Reviews with Photos**: Incentive points for photo reviews.
9. [x] **Interactive Size Guide**: Comparison with common brands (e.g., Figs vs NurseWear).
10. [x] **Order Milestone Gamification**: "Path to Gold Tier" progress bar.

### Vendor (Merchant) Role
11. [x] **Performance Analytics**: Heatmaps showing which colors/sizes sell best.
12. [x] **Promotion Manager**: Vendors can create their own "Buy 3 Get 1" deals.
13. [x] **Auto-Replenishment**: System suggests restock quantities based on velocity.
14. [x] **Vendor-Customer Chat**: Direct messaging for product inquiries.
15. [x] **Payout Dashboard**: Visual breakdown of pending vs settled funds.
16. [x] **Storefront Personalization**: Custom banners for vendor profiles.
17. [x] **Inventory Import**: Bulk upload products via CSV/Excel.
18. [x] **Quality Scorecard**: Ratings based on shipping speed and return rates.
19. [x] **Lead Generation**: Alerts for users searching for items the vendor doesn't have.
20. [x] **Eco-Friendly Badge**: Special tag for vendors using sustainable materials.

### Admin (Governance) Role
21. [x] **Fraud Detection Engine**: Flag suspicious wallet transactions or bulk orders.
22. [x] **Dynamic Commission Tiers**: Adjust commission based on vendor volume.
23. [x] **Banner Management Suite**: Control all app marketing from one screen.
24. [x] **System Health Monitor**: Real-time status of Edge Functions and API latency.
25. [x] **Automated Tax Reporting**: Generate KRA-ready reports for VAT.
26. [x] **Dispute Resolution Center**: Interface to moderate vendor/student conflicts.
27. [x] **Role Inheritance Manager**: Granular permission control for "Staff" users.
28. [x] **Mass Notification Broadcaster**: Send "System Maintenance" or "Holiday" alerts.
29. [x] **Audit Log Explorer**: Searchable history of every data change in the system.
30. [x] **Catalog Integrity Tool**: Find and fix duplicate or missing-data products.

### Automation & Logic
31. [x] **Geo-Fencing Delivery**: Auto-assign orders to the nearest delivery hub.
32. [x] **Dynamic Delivery Pricing**: Calculate fees based on distance + parcel weight.
33. [x] **Auto-Cancellation**: Cancel unpaid orders after 2 hours to free inventory.
34. [x] **Refund to Wallet**: Instant refunds instead of bank delays.
35. [x] **Birthday Bonuses**: Automated KSh 200 gift to wallet on user birthday.
36. [x] **Abandoned Cart Recovery**: Push notifications sent 24h after cart activity.
37. [x] **SLA Breach Alerts**: Notify admins if an order isn't shipped within 48h.
38. [x] **Multi-Currency Support**: Instant conversion (USD/EUR/KES/ugsh tzsh) other  ,  Drop down and use the online automatic and upto date currency rate conveter Default (KES) for international scaling.
39. [x] **Biometric Payment Approval**: Fingerprint/FaceID for wallet transactions or googlepay.
40. [x] **AI Demand Forecasting**: Predict stock needs for next month using ML.

### Infrastructure & Scale
41. [x] **Edge-Side Image Resizing**: Optimize images for mobile automatically.
42. [x] **Database Sharding Readiness**: Logic to separate archive data from active orders.
43. [x] **Offline Mode Resilience**: Queue actions when signal is lost; sync on reconnect.
44. [x] **Dark Mode / Accessibility**: Full WCAG compliance across all screens.
45. [x] **Modular UI System**: Components that adapt to Tablet/Foldable screens.
46. [x] **Global Search Index (Meilisearch)**: Sub-millisecond search across products/orders.
47. [x] **Webhooks for 3rd Party Logic**: Sync with DHL/G4S/Sendy  check kenyan deliveris services bolt or  uber.
48. [x] **OAuth 2.0 Integration**: Sign in with Google/Apple make the logic work and just to set ensure the new and login to database account emails
49. [x] **Digital Receipt Vault**: Permanent PDF storage for professional tax claims.
50. [x] **System-Wide "Undo" Action**: Transactional history for critical admin edits.
