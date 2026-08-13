# NURSEWEAR CONNECT SYSTEM MANUAL
## A Comprehensive Enterprise Healthcare E-Commerce Solution

**Author:** Lynne Harriet Vion
**Project:** NurseWearConnect
**Date:** November 2024
**Status:** System Documentation (Phase 2 & Enterprise Logic)

---

### TABLE OF CONTENTS
1.  **CHAPTER 1: INTRODUCTION** ................................................................ 3
    *   1.1 System Overview
    *   1.2 Problem Statement & Objectives
    *   1.3 Target Audience & Stakeholders
2.  **CHAPTER 2: SYSTEM ARCHITECTURE & DESIGN** .................................... 5
    *   2.1 Technological Stack (Frontend, Backend, Database)
    *   2.2 Architectural Pattern (MVVM)
    *   2.3 Multi-Role Navigation Logic
3.  **CHAPTER 3: FRONTEND ENGINEERING (THE USER INTERFACE)** .................. 7
    *   3.1 Jetpack Compose Implementation
    *   3.2 Adaptive Navigation & Material 3 Design
    *   3.3 Key UI Components (Marketing, Catalog, Orders)
4.  **CHAPTER 4: BACKEND & DATABASE ENGINEERING** ................................ 10
    *   4.1 Supabase Integration & Edge Functions
    *   4.2 Database Schema & ER Diagram Overview
    *   4.3 Row-Level Security (RLS) & Data Protection
5.  **CHAPTER 5: ADVANCED BUSINESS LOGIC & AUTOMATION** ....................... 13
    *   5.1 Payment Ecosystem (Paystack, M-Pesa, Wallet)
    *   5.2 AI Demand Forecasting & Business Intelligence
    *   5.3 Loyalty Points & Tiered Rewards System
6.  **CHAPTER 6: USER GUIDES (OPERATIONAL PROCEDURES)** .......................... 16
    *   6.1 Student/Professional User Guide
    *   6.2 Vendor Management Guide
    *   6.3 Admin Governance & Audit Procedures
7.  **CHAPTER 7: DEPLOYMENT & MAINTENANCE** ......................................... 19
    *   7.1 CI/CD Pipeline (Docker & GitHub Actions)
    *   7.2 Security Protocols (Biometrics & Encryption)
8.  **REFERENCES & APPENDICES** ............................................................... 20

---

## CHAPTER 1: INTRODUCTION

### 1.1 System Overview
NurseWearConnect is a specialized, enterprise-grade e-commerce platform designed specifically for the healthcare sector in Kenya. Unlike generic e-commerce applications, NurseWearConnect addresses the unique needs of medical professionals and students by providing high-quality scrubs, footwear, and accessories through a multi-vendor marketplace model. The system integrates advanced payment gateways (M-Pesa, Paystack), real-time inventory management, and a robust administrative governance suite.

### 1.2 Problem Statement & Objectives
Healthcare professionals often struggle to find durable, stylish, and compliant apparel in local markets. NurseWearConnect aims to:
- Provide a centralized marketplace for verified medical apparel.
- Enable local vendors to scale their businesses through a structured merchant ecosystem.
- Implement specialized services such as "Try Before You Buy" (Home Fitting) and AI-driven size recommendations.

### 1.3 Target Audience & Stakeholders
- **Students:** Nursing and medical students requiring affordable, standardized attire.
- **Professionals:** Practicing medical staff seeking premium, customized scrubs.
- **Vendors:** Local manufacturers and retailers of medical wear.
- **Admins:** System governors responsible for quality control and financial auditing.

> **FIGURE 1: [INSERT SCREENSHOT OF THE APP SPLASH SCREEN OR HOME SCREEN DASHBOARD]**
> *Caption: Figure 1: The NurseWearConnect Multi-Role Dashboard showing personalized greetings and featured categories (NurseWearConnect, 2024).*

---

## CHAPTER 2: SYSTEM ARCHITECTURE & DESIGN

### 2.1 Technological Stack
The system is built on a modern, reactive stack to ensure high performance and scalability:
- **Frontend:** Kotlin with Jetpack Compose (Declarative UI).
- **Backend-as-a-Service:** Supabase (PostgreSQL, Realtime, Storage, Auth).
- **Networking:** Retrofit for REST API communication.
- **Local Persistence:** Room Database for cart management and offline resilience.
- **Financial Integration:** Safaricom M-Pesa API and Paystack SDK.

### 2.2 Architectural Pattern (MVVM)
The application follows the **Model-View-ViewModel (MVVM)** pattern. This separation of concerns ensures that business logic (handled in `HomeViewModel.kt`) is decoupled from the UI layer, facilitating easier unit testing and maintenance.

### 2.3 Multi-Role Navigation Logic
A core innovation of NurseWearConnect is its role-based UI adaptation. Upon login, the system queries the `profiles` table in Supabase. The `HomeViewModel` then dynamically updates the `HomeUiState` to reveal role-specific screens:
- **Admin:** Access to User Management, Vendor Approvals, and System Logs.
- **Vendor:** Access to Inventory Management and Sales Analytics.
- **Consumer:** Access to Catalog, Cart, and Order Tracking.

> **FIGURE 2: [INSERT SCREENSHOT OF THE NAVIGATION DRAWER OR ROLE-SPECIFIC NAVIGATION BAR]**
> *Caption: Figure 2: The Adaptive Navigation Suite showing role-specific entry points (NurseWearConnect, 2024).*

---

## CHAPTER 3: FRONTEND ENGINEERING

### 3.1 Jetpack Compose Implementation
The UI is entirely built using Jetpack Compose, leveraging the `Material3` design system. This allows for a consistent look and feel across different Android devices, from small-screen phones to tablets and foldables.

### 3.2 Adaptive Navigation
The system utilizes `NavigationSuiteScaffold` to provide a responsive layout. On phones, it displays a bottom navigation bar; on tablets, it transitions to a permanent navigation rail.

### 3.3 Key UI Components
- **Marketing Components:** Dynamic banners and coupon sliders that adjust based on active promotions.
- **Product Catalog:** A high-performance grid with advanced filtering (price range, size, material) and sorting (price, rating, newest).
- **Checkout Flow:** A multi-step process that handles address selection, delivery method, and secure payment initialization.

> **FIGURE 3: [INSERT SCREENSHOT OF THE PRODUCT CATALOG SCREEN WITH FILTERS OPEN]**
> *Caption: Figure 3: Advanced Catalog Filtering System with real-time results (NurseWearConnect, 2024).*

> **FIGURE 4: [INSERT SCREENSHOT OF THE CHECKOUT SCREEN SHOWING M-PESA AND WALLET OPTIONS]**
> *Caption: Figure 4: The Integrated Checkout Interface supporting multiple payment modalities (NurseWearConnect, 2024).*

---

## CHAPTER 4: BACKEND & DATABASE ENGINEERING

### 4.1 Supabase Integration
The backend utilizes Supabase for real-time data synchronization. When an order status is updated by a vendor, the `OrdersRealtimeJob` in the Android app detects the change via WebSockets and updates the student's UI instantly.

### 4.2 Database Schema
The database is structured into 15+ interconnected tables. Key tables include:
- `profiles`: Stores user metadata and roles.
- `products`: Contains item details, stock levels, and vendor IDs.
- `orders` & `order_items`: Manage transaction history and multi-vendor item splitting.
- `wallets`: Handles virtual currency and loyalty point balances.

### 4.3 Row-Level Security (RLS)
Security is enforced at the database level using RLS policies. This ensures that:
- Vendors can **only** see and edit their own products.
- Students can **only** access their own order history and profile.
- Admins have **bypass** permissions for system-wide governance.

> **FIGURE 5: [INSERT SCREENSHOT OF THE SUPABASE DATABASE SCHEMA OR A RELEVANT ER DIAGRAM]**
> *Caption: Figure 5: Relational Database Schema highlighting the integrity between Products, Orders, and Vendors (NurseWearConnect, 2024).*

---

## CHAPTER 5: ADVANCED BUSINESS LOGIC

### 5.1 Payment Ecosystem
NurseWearConnect implements a complex "Deferred Order Creation" logic (Level 42 Fix). Orders are only persisted to the database *after* a successful payment verification from Paystack or M-Pesa, preventing "ghost orders" from cluttering the system.

### 5.2 AI Demand Forecasting
The system includes a mock ML-based forecasting engine that analyzes sales trends and stock velocity. Vendors receive alerts when high-demand items are likely to go out of stock within 7 days.

### 5.3 Loyalty & Tiers
The `LoyaltyPointCalculator` awards points for every successful purchase. These points are automatically converted into wallet balance once a user reaches specific milestones (Bronze, Silver, Gold).

> **FIGURE 6: [INSERT SCREENSHOT OF THE VENDOR ANALYTICS DASHBOARD SHOWING SALES TRENDS]**
> *Caption: Figure 6: Business Intelligence Dashboard for Vendors with revenue forecasting (NurseWearConnect, 2024).*

---

## CHAPTER 6: USER GUIDES

### 6.1 Student/Professional User Guide
1.  **Search:** Use the visual search or catalog filters to find scrubs.
2.  **Fitting:** Select "Request Home Fitting" if you want to try the items before purchase.
3.  **Checkout:** Pay via M-Pesa STK Push or your pre-loaded Wallet.
4.  **Track:** Monitor your order milestones (Pending -> Processing -> Shipped -> Delivered).

### 6.2 Vendor Management Guide
1.  **Inventory:** Add products with multiple sizes and colors. The system optimizes images automatically.
2.  **Orders:** View orders containing your items. Update status to "Processing" when ready.
3.  **Payouts:** Request withdrawals of your earnings directly to your registered M-Pesa number.

### 6.3 Admin Governance Guide
1.  **Approvals:** Vetting new vendor applications by reviewing their uploaded documents.
2.  **Audit Logs:** Monitor real-time system logs to detect any unauthorized actions or system errors.
3.  **Marketing:** Manage app-wide banners and seasonal coupons.

> **FIGURE 7: [INSERT SCREENSHOT OF THE ADMIN USER MANAGEMENT OR LOGS SCREEN]**
> *Caption: Figure 7: Administrative Governance Suite showing the System Audit Log (NurseWearConnect, 2024).*

---

## CHAPTER 7: DEPLOYMENT & MAINTENANCE

### 7.1 CI/CD Pipeline
The application uses Docker for standardized builds. A GitHub Action triggers on every push to `main`, compiling the APK and generating a `supabase_upload.sh` script for secure deployment to the Supabase Storage bucket.

### 7.2 Security Protocols
- **Biometrics:** Users can enable Fingerprint/FaceID for secure wallet transactions.
- **Encrypted Storage:** All session tokens are stored using `EncryptedSharedPreferences`.

---

## CHAPTER 8: REFERENCES & APPENDICES

### 8.1 References (APA Style)
- NurseWearConnect. (2024). *System Architecture and Module Specification (MODULES.MD)*. Internal Documentation.
- NurseWearConnect. (2024). *Business Logic and Enterprise Features (LOGICS.MD)*. Internal Documentation.
- Supabase. (2024). *PostgreSQL Row-Level Security Policies for E-Commerce*.
- Android Developers. (2024). *Jetpack Compose Adaptive Navigation Guidelines*.

### 8.2 Appendix: Glossary of Terms
- **STK Push:** Sim Toolkit Push, used for M-Pesa payments.
- **RLS:** Row-Level Security.
- **MVVM:** Model-View-ViewModel.

---
**[END OF DOCUMENT]**
