import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';

const useCartStore = create(
  persist(
    (set, get) => ({
      items: [], // { id, name, price, quantity, size, color, tailoringNotes }
      total: 0,

      // Actions
      addItem: (product, tailoringNotes = '') => {
        const { items } = get();
        const existingItem = items.find(item =>
          item.id === product.id &&
          item.size === product.selectedSize &&
          item.color === product.selectedColor
        );

        if (existingItem) {
          const updatedItems = items.map(item =>
            item === existingItem
              ? { ...item, quantity: item.quantity + 1, tailoringNotes: tailoringNotes || item.tailoringNotes }
              : item
          );
          set({ items: updatedItems });
        } else {
          set({
            items: [...items, {
              ...product,
              quantity: 1,
              tailoringNotes: tailoringNotes || '',
              size: product.selectedSize,
              color: product.selectedColor
            }]
          });
        }
        get().calculateTotal();
      },

      removeItem: (productId, size, color) => {
        const { items } = get();
        set({
          items: items.filter(item =>
            !(item.id === productId && item.size === size && item.color === color)
          )
        });
        get().calculateTotal();
      },

      updateQuantity: (productId, size, color, quantity) => {
        const { items } = get();
        if (quantity < 1) return;

        const updatedItems = items.map(item =>
          (item.id === productId && item.size === size && item.color === color)
            ? { ...item, quantity }
            : item
        );
        set({ items: updatedItems });
        get().calculateTotal();
      },

      updateTailoringNotes: (productId, size, color, notes) => {
        const { items } = get();
        const updatedItems = items.map(item =>
          (item.id === productId && item.size === size && item.color === color)
            ? { ...item, tailoringNotes: notes }
            : item
        );
        set({ items: updatedItems });
      },

      clearCart: () => {
        set({ items: [], total: 0 });
      },

      calculateTotal: () => {
        const { items } = get();
        const total = items.reduce((sum, item) => sum + (item.price * item.quantity), 0);
        set({ total });
      }
    }),
    {
      name: 'cart-storage',
      storage: createJSONStorage(() => AsyncStorage),
    }
  )
);

export default useCartStore;
