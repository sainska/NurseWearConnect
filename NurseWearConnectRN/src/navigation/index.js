/**
 * This file serves as a blueprint for the React Native navigation structure.
 * It uses placeholders for screen components as they are built.
 */

// Note: Actual implementation will require @react-navigation/native and @react-navigation/stack/bottom-tabs
// This file defines the structure for the developer to follow.

export const RootNavigationStructure = {
  AuthStack: {
    Onboarding: 'OnboardingScreen',
    Login: 'LoginScreen',
    Register: 'RegisterScreen',
    OTP: 'OTPScreen',
    ForgotPassword: 'ResetPasswordScreen'
  },
  MainAppTabs: {
    Home: {
      component: 'HomeScreen',
      icon: 'home-outline'
    },
    Catalog: {
      component: 'CatalogScreen',
      icon: 'shirt-outline'
    },
    Cart: {
      component: 'CartScreen',
      icon: 'cart-outline'
    },
    Orders: {
      component: 'OrdersScreen',
      icon: 'receipt-outline'
    },
    Profile: {
      component: 'ProfileScreen',
      icon: 'person-outline'
    }
  },
  ModalStack: {
    ProductDetail: 'ProductDetailScreen',
    Checkout: 'CheckoutScreen',
    Tracking: 'TrackingScreen',
    Chat: 'ChatScreen',
    Tailoring: 'TailoringScreen'
  }
};
