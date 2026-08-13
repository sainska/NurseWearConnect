import { apiGet, apiPost, apiPut, apiDelete } from './api';

// Product Module
export const productApi = {
  getProducts: () => apiGet('/products'),
  getProductById: (id) => apiGet(`/products/${id}`),
  getCategories: () => apiGet('/categories'),
  getFeaturedProducts: () => apiGet('/products/featured'),
};

// Cart and Orders Module
export const orderApi = {
  createOrder: (orderData) => apiPost('/orders', orderData),
  getUserOrders: (userId) => apiGet(`/orders/user/${userId}`),
  getOrderDetails: (orderId) => apiGet(`/orders/${orderId}`),
  getOrderTracking: (orderId) => apiGet(`/orders/${orderId}/tracking`),
};

// Checkout and Payment Module
export const paymentApi = {
  initiateStkPush: (data) => apiPost('/payment/stk-push', data),
  checkPaymentStatus: (checkoutId) => apiGet(`/payment/status/${checkoutId}`),
};

// User Profile and Tailoring Notes
export const userApi = {
  getProfile: (userId) => apiGet(`/user/${userId}`),
  updateProfile: (userId, data) => apiPut(`/user/${userId}`, data),
  saveTailoringNotes: (userId, notes) => apiPost(`/user/${userId}/tailoring-notes`, notes),
};

// Chat Module
export const chatApi = {
  getChatHistory: (orderId) => apiGet(`/chat/${orderId}`),
  sendMessage: (orderId, data) => apiPost(`/chat/${orderId}`, data),
};
