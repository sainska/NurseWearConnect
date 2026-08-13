import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { apiPost } from '../services/api';

const useAuthStore = create(
  persist(
    (set, get) => ({
      user: null,
      token: null,
      isAuthenticated: false,
      loading: false,
      error: null,

      // Actions
      loginUser: async (credentials) => {
        set({ loading: true, error: null });
        try {
          const response = await apiPost('/auth/login', credentials);
          const { user, token } = response.data;
          set({ user, token, isAuthenticated: true, loading: false });
        } catch (err) {
          set({ error: err.message, loading: false });
        }
      },

      registerUser: async (userData) => {
        set({ loading: true, error: null });
        try {
          const response = await apiPost('/auth/register', userData);
          const { user, token } = response.data;
          set({ user, token, isAuthenticated: true, loading: false });
        } catch (err) {
          set({ error: err.message, loading: false });
        }
      },

      logoutUser: () => {
        set({ user: null, token: null, isAuthenticated: false, error: null });
      },

      verifyOTP: async (code) => {
        set({ loading: true, error: null });
        try {
          await apiPost('/auth/verify-otp', { code });
          set({ loading: false });
          return true;
        } catch (err) {
          set({ error: err.message, loading: false });
          return false;
        }
      },

      resetPassword: async (email) => {
        set({ loading: true, error: null });
        try {
          await apiPost('/auth/reset-password', { email });
          set({ loading: false });
        } catch (err) {
          set({ error: err.message, loading: false });
        }
      },

      updateProfile: async (data) => {
        set({ loading: true });
        try {
          const response = await apiPost('/user/update', data);
          set({ user: response.data, loading: false });
        } catch (err) {
          set({ error: err.message, loading: false });
        }
      }
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => AsyncStorage),
    }
  )
);

export default useAuthStore;
