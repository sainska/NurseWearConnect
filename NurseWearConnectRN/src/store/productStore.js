import { create } from 'zustand';
import { apiGet } from '../services/api';

const useProductStore = create((set, get) => ({
  products: [],
  filteredProducts: [],
  categories: [],
  selectedCategory: 'All',
  loading: false,
  error: null,
  currentProduct: null,

  // Actions
  fetchProducts: async () => {
    set({ loading: true, error: null });
    try {
      const response = await apiGet('/products');
      set({
        products: response.data,
        filteredProducts: response.data,
        loading: false
      });
    } catch (err) {
      set({ error: err.message, loading: false });
    }
  },

  fetchCategories: async () => {
    try {
      const response = await apiGet('/categories');
      set({ categories: ['All', ...response.data] });
    } catch (err) {
      console.error('Failed to fetch categories:', err);
    }
  },

  setCategory: (category) => {
    const { products } = get();
    const filtered = category === 'All'
      ? products
      : products.filter(p => p.category === category);
    set({ selectedCategory: category, filteredProducts: filtered });
  },

  searchProducts: (query) => {
    const { products, selectedCategory } = get();
    const filtered = products.filter(p =>
      p.name.toLowerCase().includes(query.toLowerCase()) &&
      (selectedCategory === 'All' || p.category === selectedCategory)
    );
    set({ filteredProducts: filtered });
  },

  fetchProductById: async (id) => {
    set({ loading: true, error: null });
    try {
      const response = await apiGet(`/products/${id}`);
      set({ currentProduct: response.data, loading: false });
    } catch (err) {
      set({ error: err.message, loading: false });
    }
  }
}));

export default useProductStore;
