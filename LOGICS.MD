# NurseWearConnect: Implemented Logic & Features

This document tracks all Phase 2 and Enterprise logic that has been successfully implemented in the NurseWearConnect platform.

## Logic Enhancement (Core Workflow)
- **Student Order View**: Implemented `OrdersScreen.kt` for students with tabbed views (Active, Completed, Cancelled).
- **Real-time Order Tracking**: Integrated timeline-based tracking UI using the `order_status_history` table.
- **Vendor Product Filtering**: Updated vendor orders logic to ONLY show orders containing products owned by the vendor.
- **Multi-Vendor Order Splitting**: Logic to handle single consumer orders containing items from multiple vendors.
- **Optimistic Tracking Updates**: UI reflects status changes immediately while sync occurs in the background.
- **Student Home Cleanup**: Removed "Recent Updates" from `HomeScreen.kt` for students to focus on shopping and wallet.
- **Admin Global Order Dashboard**: Implemented searchable, filterable master order list for system governance.
- **Automated PDF Invoicing**: Triggered server-side PDF generation on order completion.
- **Inventory Auto-Correction**: Stock deduction on payment success and restoration on cancellation via SQL Triggers.
- **Push Notification Bridge**: Connected `notifications` table to `fcm-pusher` Edge Function for real-time alerts.
- **Wallet-Only Checkout**: Logic to prioritize wallet balance over external payment methods.
- **Loyalty Point Calculation**: Logic to award 1% of order value as points upon "Delivered" status.
- **Address Book Management**: CRUD operations for student delivery addresses within the checkout flow.
- **Return/Refund Workflow**: UI and logic for initiating returns within 7 days of delivery.
- **Vendor Payout Logic**: Automated calculation of vendor earnings minus commission.
- **Smart Search for Orders**: Filter orders by ID, Product Name, or Date Range in Admin Dashboard.
- **Bulk Order Actions for Admin**: Allows admin to batch-update order statuses.
- **Delivery Partner Integration**: Placeholder for tracking IDs from third-party logistics providers.
- **Session-Based Cart Persistence**: Ensures cart survives app restarts using Room/Supabase sync.
- **Home Fitting Service Integration**: [DONE] End-to-end logic for "Try Before You Buy" with appointment scheduling.
- **AI Demand Forecasting**: [DONE] Predict stock needs using mock ML logic in `HomeViewModel` and display in `ReportsScreen.kt`.
- **Refund to Wallet**: [DONE] Instant refunds instead of bank delays (Triggered on return approval).

## Enterprise System Features

### Student (Consumer) Experience
- **Visual Search**: Upload a photo of scrubs to find similar items.
- **Flash Sale Countdown**: Real-time tickers for limited-time offers.
- **Waitlist/Restock Alerts**: Notify users when an out-of-stock item is back.
- **Bundle Discounts**: "Buy the set" (top + bottom) for 10% less.
- **Referral Program**: "Invite a nurse" and get KSh 500 wallet credit.
- **Subscription Scrubs**: Monthly delivery for high-wear environments.
- **Try Before You Buy**: Home fitting service logistics.
- **Community Reviews with Photos**: Incentive points for photo reviews.
- **Interactive Size Guide**: Comparison with common brands.
- **Order Milestone Gamification**: "Path to Gold Tier" progress bar.

### Vendor (Merchant) Experience
- **Performance Analytics**: Heatmaps showing which colors/sizes sell best.
- **Promotion Manager**: Vendors can create their own "Buy 3 Get 1" deals.
- **Auto-Replenishment**: System suggests restock quantities based on velocity.
- **Vendor-Customer Chat**: Direct messaging for product inquiries.
- **Payout Dashboard**: Visual breakdown of pending vs settled funds.
- **Storefront Personalization**: Custom banners for vendor profiles.
- **Inventory Import**: Bulk upload products via CSV/Excel.
- **Quality Scorecard**: Ratings based on shipping speed and return rates.
- **Lead Generation**: Alerts for users searching for items the vendor doesn't have.
- **Eco-Friendly Badge**: Special tag for vendors using sustainable materials.

### Admin (Governance) & Automation
- **Fraud Detection Engine**: Flag suspicious wallet transactions or bulk orders.
- **Dynamic Commission Tiers**: Adjust commission based on vendor volume.
- **Banner Management Suite**: Control all app marketing from one screen.
- **System Health Monitor**: Real-time status of Edge Functions and API latency.
- **Automated Tax Reporting**: Generate KRA-ready reports for VAT.
- **Dispute Resolution Center**: Interface to moderate vendor/student conflicts.
- **Role Inheritance Manager**: Granular permission control for "Staff" users.
- **Mass Notification Broadcaster**: Send "System Maintenance" or "Holiday" alerts.
- **Audit Log Explorer**: Searchable history of every data change in the system.
- **Catalog Integrity Tool**: Find and fix duplicate or missing-data products.
- **Geo-Fencing Delivery**: Auto-assign orders to the nearest delivery hub.
- **Dynamic Delivery Pricing**: Fees based on distance + parcel weight.
- **Auto-Cancellation**: Cancel unpaid orders after 2 hours to free inventory.
- **Refund to Wallet**: Instant refunds instead of bank delays.
- **Birthday Bonuses**: Automated KSh 200 gift to wallet on user birthday.
- **Abandoned Cart Recovery**: Push notifications sent 24h after cart activity.
- **SLA Breach Alerts**: Notify admins if an order isn't shipped within 48h.
- **Multi-Currency Support**: Instant conversion (USD/EUR/KES) with automatic rates.
- **Biometric Payment Approval**: Fingerprint/FaceID for wallet transactions.
- **AI Demand Forecasting**: Predict stock needs using ML.

### Infrastructure & Scale
- **Modular UI System**: Components that adapt to Tablet/Foldable screens using `NavigationSuiteScaffold` and `WindowSizeClass`.
- **OAuth 2.0 Integration**: Sign in with Google/Apple implemented.
- **Offline Mode Resilience**: Queue actions when signal is lost; sync on reconnect.
- **Dark Mode / Accessibility**: Full WCAG compliance across all screens (Material 3).
- **Database Sharding Readiness**: Logic to separate archive data from active orders.
- **Webhooks for 3rd Party Logic**: Sync with delivery services (DHL/G4S/Sendy/Bolt/Uber).
- **Digital Receipt Vault**: Permanent PDF storage for professional tax claims.
- **System-Wide "Undo" Action**: Transactional history for critical admin edits.
