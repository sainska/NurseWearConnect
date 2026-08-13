import { create } from 'zustand';
import { apiGet, apiPost } from '../services/api';

const useChatStore = create((set, get) => ({
  messages: [],
  loading: false,
  error: null,
  isSupportOnline: true,

  // Actions
  fetchMessages: async (orderId) => {
    set({ loading: true, error: null });
    try {
      const response = await apiGet(`/chat/${orderId}`);
      set({ messages: response.data, loading: false });
    } catch (err) {
      set({ error: err.message, loading: false });
    }
  },

  sendMessage: async (orderId, text, sender = 'user') => {
    const newMessage = {
      id: Date.now(),
      text,
      sender,
      timestamp: new Date().toISOString(),
      status: 'sending'
    };

    set({ messages: [...get().messages, newMessage] });

    try {
      const response = await apiPost(`/chat/${orderId}`, { text, sender });
      // Update the temporary message with the one from server
      const updatedMessages = get().messages.map(msg =>
        msg.id === newMessage.id ? { ...response.data, status: 'sent' } : msg
      );
      set({ messages: updatedMessages });

      // Simulate Support Response if it's a simulation environment
      if (process.env.NODE_ENV === 'development') {
        setTimeout(() => {
          get().simulateSupportResponse(orderId);
        }, 2000);
      }
    } catch (err) {
      const updatedMessages = get().messages.map(msg =>
        msg.id === newMessage.id ? { ...msg, status: 'error' } : msg
      );
      set({ messages: updatedMessages, error: err.message });
    }
  },

  simulateSupportResponse: (orderId) => {
    const responses = [
      "Hello! I'm looking into your order status right now.",
      "Your package has been picked up by the courier.",
      "Yes, we can adjust the hem by 2 inches as requested.",
      "The embroidery will be done in the Slate Blue color you selected.",
      "Is there anything else I can help you with?"
    ];
    const randomResponse = responses[Math.floor(Math.random() * responses.length)];

    const supportMessage = {
      id: Date.now() + 1,
      text: randomResponse,
      sender: 'support',
      timestamp: new Date().toISOString(),
      status: 'sent'
    };

    set({ messages: [...get().messages, supportMessage] });
  },

  clearChat: () => {
    set({ messages: [] });
  }
}));

export default useChatStore;
