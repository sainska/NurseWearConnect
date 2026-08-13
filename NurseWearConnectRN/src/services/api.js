import axios from 'axios';

const API_BASE_URL = 'https://api.nursewearconnect.com/v1'; // Placeholder

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request Interceptor for Token Injection
api.interceptors.request.use(
  async (config) => {
    // Logic to get token from storage would go here
    // const token = await AsyncStorage.getItem('userToken');
    // if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

export const apiGet = (url) => api.get(url);
export const apiPost = (url, data) => api.post(url, data);
export const apiPut = (url, data) => api.put(url, data);
export const apiDelete = (url) => api.delete(url);

export default api;
