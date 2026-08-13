# NurseWear Connect - Comprehensive Feature Audit & TODO List

This document tracks functionalities to be checked, implemented, or refined across all screens to ensure a consistent, secure, and high-quality experience.

## 🛡️ Global Security & Auth
- [x] **Custom 4-digit OTP**: Implementation of `password_resets` table and `reset_password_with_otp` RPC.
- [ ] **Session Expiry**: Verify that the 5-minute inactivity logout in `MainActivity` correctly clears sensitive data and redirects to Login.
- [x] **Vendor Lockdown**: `MainActivity` now checks for `active` status before allowing access to the main scaffold.
- [ ] **Role-Based Access Control (RBAC)**: Audit all screens to ensure users cannot navigate to unauthorized screens (e.g., Admin screens for Vendors).
- [x] **Audit Logging**: `ApiService.logAction` integrated for password reset flows.
- [ ] **OTP Security**: Finalize testing of 4-digit recovery code expiry (15m) and `is_used` consumption via Edge Function trigger.

## 📱 Auth & Onboarding Screens
### Login / Register / Recovery
- [x] **Form Validation**: Real-time visual feedback for email, phone (KE), and password strength implemented in `RegisterScreen` and `PasswordRecoveryScreen`.
- [ ] **Biometric Setup**: Add a prompt after the first successful login to enable biometrics.
- [x] **Vendor Document Upload**: `RegistrationViewModel` handles license upload to Supabase Storage.
- [x] **Loading States**: `ShimmerPlaceholder` integrated into `ProductGrid` for loading states.
- [x] **4-Digit OTP UI**: `OtpVerificationContent` in `PasswordRecoveryScreen` handles the 4-digit input flow.

## 🏠 Main User Screens (Student/Professional)
### Home / Catalog / Search
- [x] **Pull-to-Refresh**: `PullToRefreshBox` implemented on Home and Catalog screens.
- [x] **Search Debounce**: `HomeViewModel` setup with 300ms debounce.
- [ ] **Favorites Sync**: Verify that toggling favorites updates the local Room database (`AppDatabase`) for offline visibility.
- [ ] **Image Caching**: Use Coil's `crossfade` and `placeholder` for all product images.

### Cart & Checkout
- [x] **Inventory Check**: `PaymentRepository.initiateMpesaPayment` calls `apiService.validateInventory` before triggering STK push.
- [x] **M-Pesa STK Push**: `stk-push` Edge Function implemented; `checkStatus` polling integrated for callback processing.
- [ ] **Order Tracking**: Implement a "Timeline" view in the Orders screen for real-time status updates.

## 💼 Vendor Management Screens
### Vendor Dashboard & Analytics
- [x] **Vendor Pending Screen**: Displays status and notes for vendors awaiting approval.
- [ ] **Product Management**: Ensure vendors can only edit their own products.
- [ ] **Earnings Breakdown**: Add a detailed view of commissions deducted per sale (Brand600 style).

## 🔑 Admin Governance Screens
### Inventory & Vendor Approvals
- [x] **Vendor Approvals**: `AdminVendorApprovalsScreen` implemented to manage pending vendors.
- [x] **Inventory Management**: `AdminInventoryScreen` for global product control.
- [ ] **Bulk Actions**: Allow admins to approve multiple pending vendors at once.
- [ ] **System Logs**: Implement a filter for "Severity" (Error, Warning, Info) in the Admin Logs screen using `ApiService.getSystemLogs`.
- [ ] **Reporting**: Add a "Export to CSV/PDF" function for monthly sales and payout reports.

## 🎨 UI/UX Refinements
- [ ] **Edge-to-Edge**: Ensure all screens respect `systemBarsPadding()` to avoid content overlaps with status/navigation bars.
- [ ] **Empty States**: Create branded illustrations for "No Orders", "Cart Empty", and "Search Not Found".
- [ ] **Accessibility**: Add content descriptions to all icons and ensure a minimum touch target of 48dp for all buttons.
- [ ] **Branding**: Ensure all `Brand600` (#0D9488) usages are consistent across buttons, icons, and loaders.

## 🧪 Testing Checklist
- [ ] **End-to-End Recovery**: Request OTP -> Check Email (via Resend) -> Verify -> Change Password.
- [ ] **Deep Linking Security**: Attempt to deep-link into `AdminInventory` using a Vendor account.
- [ ] **Offline Mode**: Turn off internet and verify that cached products from Room are still visible.
- [ ] **Desugaring**: Test on a device running Android 7 (API 24) to ensure `java.time` (used in `RecoveryUtils` and `AuthRepository`) doesn't crash.
  build the favorites and quick re-order in past oders logics across all the system and ensure to have sql for the codes updated  2 ensure the bottomnavigation bar when user scrolls up the bar disappears something like that logic