# NurseWearConnect System Architecture & Modules

This document provides a comprehensive overview of the NurseWearConnect application structure, detailing the functionalities of each module and submodule.

## 1. Core Android Module (`:app`)
The primary native application developed in Kotlin using Jetpack Compose.

### A. Data Layer (`com.example.nursewearconnect.data`)
*   **`api` (ApiService.kt)**: Defines the REST interface for Supabase (PostgREST). Handles requests for products, orders, profile updates, and admin operations.
*   **`repository`**: Orchestrates data flow between the API and the UI.
    *   `AuthRepository`: Manages user authentication, registration, and role assignment.
    *   `ProductRepository`: Handles catalog fetching, search, and category management.
    *   `OrderRepository`: Manages cart persistence and order creation.
    *   `VendorRepository`: Manages vendor-specific product inventory and orders.
    *   `AdminRepository`: Provides high-level access for user management and system auditing.
*   **`local`**: Room DB implementation for local storage (Cart items, cached products).
*   **`security`**: Encrypted storage for session tokens and Biometric authentication logic.

### B. UI Layer (`com.example.nursewearconnect.ui`)
*   **`screens` (Role-Based Architecture)**:
    *   **Student/Professional**:
        *   `HomeScreen`: Personalized dashboard with featured products and quick links.
        *   `CatalogScreen`: Advanced product discovery with filtering and sorting.
        *   `CartScreen`: Multi-step checkout with M-Pesa integration.
        *   `OrdersScreen`: Real-time order tracking and status updates.
    *   **Vendor**:
        *   `VendorInventoryScreen`: CRUD operations for vendor products with image optimization.
        *   `VendorOrdersScreen`: Management of customer orders specifically for the vendor's items.
        *   `VendorAnalyticsScreen`: Financial dashboards showing revenue trends and sales stats.
    *   **Admin**:
        *   `AdminUserManagementScreen`: CRUD operations on user profiles and role overrides.
        *   `AdminVendorApprovalsScreen`: Workflow for vetting and approving new vendor applications.
        *   `UserLogsScreen`: Real-time system audit logs for monitoring platform health.
*   **`viewmodel` (HomeViewModel.kt)**: The central business logic engine. It manages the global `HomeUiState`, handles real-time Supabase subscriptions, and processes user interactions across all roles.

### C. Domain Models (`com.example.nursewearconnect.model`)
*   `Product`: Data class for items, including transient fields for vendor metadata.
*   `User`: Profile information including roles (admin, vendor, student).
*   `Order`: Represents purchases, linked to `OrderItems` and `Payments`.

## 2. Infrastructure & Backend (`/supabase`)
The backend is powered by Supabase, providing a serverless PostgreSQL environment.

*   **`database_schema.sql`**: The Single Source of Truth for the backend.
    *   **Tables**: Profiles, Products, Orders, Order Items, Notifications, Messages, Banners, Coupons, System Logs.
    *   **Security (RLS)**: Row Level Security policies ensure that students only see their orders, vendors only manage their products, and admins have global visibility.
    *   **Triggers**: Automatic notification creation on order status changes or vendor registration.
    *   **Functions**: Custom PL/pgSQL functions for review eligibility and role checking.
*   **Edge Functions**:
    *   `stk-push`: Initiates M-Pesa mobile money payments.
    *   `mpesa-callback`: Processes payment confirmations from Safaricom.

## 3. Supplementary Modules[TODO.md](TODO.md)
*   **`NurseWearConnectRN`**: A React Native submodule for cross-platform feature parity or specific web-based admin tools.
*   **`gradle`**: Contains build configuration scripts and dependency versioning (`libs.versions.toml`).

## 4. Key Cross-Cutting Functionalities
*   **Real-time Synchronization**: Uses Supabase Realtime (WebSockets) to update chat messages, order statuses, and notifications without requiring a page refresh.
*   **Image Optimization**: Client-side JPEG compression and resizing before uploading product images to Supabase Storage.
*   **Multi-Role Dashboarding**: A dynamic UI that adapts its navigation, available actions, and data views based on the authenticated user's `role` and `status`.
