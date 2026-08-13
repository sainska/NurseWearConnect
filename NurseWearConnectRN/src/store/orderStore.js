import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { apiGet, apiPost } from '../services/api';

const useOrderStore = create(
  persist(
    (set, get) => ({
      orders: [],
      loading: false,
      error: null,
      trackingInfo: null, // Current order tracking details

      // Actions
      fetchOrders: async (userId) => {
        set({ loading: true, error: null });
        try {
          const response = await apiGet(`/orders/user/${userId}`);
          set({ orders: response.data, loading: false });
        } catch (err) {
          set({ error: err.message, loading: false });
        }
      },

      createOrder: async (orderData) => {
        set({ loading: true, error: null });
        try {
          const response = await apiPost('/orders', orderData);
          set({ orders: [...get().orders, response.data], loading: false });
          return response.data; // Return the created order for success handling
        } catch (err) {
          set({ error: err.message, loading: false });
          throw err;
        }
      },

      fetchTracking: async (orderId) => {
        set({ loading: true, error: null });
        try {
          const response = await apiGet(`/orders/${orderId}/tracking`);
          set({ trackingInfo: response.data, loading: false });
        } catch (err) {
          set({ error: err.message, loading: false });
        }
      },

      // Simulation for M-Pesa STK Push
      initiatePayment: async (orderDetails) => {
        set({ loading: true, error: null });
        try {
          // In a real app, this would call a backend endpoint that interacts with M-Pesa
          const response = await apiPost('/payment/stk-push', orderDetails);
          set({ loading: false });
          return response.data; // Should contain CheckoutRequestID
        } catch (err) {
          set({ error: err.message, loading: false });
          throw err;
        }
      }
    }),
    {
      name: 'order-storage',
      storage: createJSONStorage(() => AsyncStorage),
    }
  )
);

export default useOrderStore;
