import {
  createDemoCheckout,
  demoMemberId,
  displayHome,
  initialCart,
  initialNotifications,
  initialStock,
  products
} from './mockData';
import type { Cart, Checkout, DisplayHome, Notification, Product, Shipment, Stock } from './types';

const apiBase = import.meta.env.VITE_API_BASE_URL ?? '/api';

export class ApiUnavailableError extends Error {
  constructor() {
    super('api unavailable');
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers
    },
    ...init
  });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.json() as Promise<T>;
}

export const api = {
  async home(): Promise<DisplayHome> {
    try {
      return await request<DisplayHome>('/display/home');
    } catch {
      throw new ApiUnavailableError();
    }
  },

  async products(): Promise<Product[]> {
    try {
      return await request<Product[]>('/products');
    } catch {
      throw new ApiUnavailableError();
    }
  },

  async cart(memberId = demoMemberId): Promise<Cart> {
    try {
      return await request<Cart>(`/cart/${memberId}`);
    } catch {
      throw new ApiUnavailableError();
    }
  },

  async addCartItem(memberId: string, skuId: string, quantity: number): Promise<Cart> {
    return request<Cart>(`/cart/${memberId}/items`, {
      method: 'POST',
      body: JSON.stringify({ skuId, quantity })
    });
  },

  async checkout(memberId: string): Promise<Checkout> {
    return request<Checkout>('/checkout', {
      method: 'POST',
      body: JSON.stringify({ memberId, paymentToken: 'card_test_success' })
    });
  },

  async ship(shipmentId: string): Promise<Shipment> {
    return request<Shipment>(`/shipments/${shipmentId}/ship`, {
      method: 'POST',
      body: '{}'
    });
  },

  async deliver(shipmentId: string): Promise<{ shipment: Shipment; order: Checkout['order'] }> {
    return request<{ shipment: Shipment; order: Checkout['order'] }>(`/shipments/${shipmentId}/deliver`, {
      method: 'POST',
      body: '{}'
    });
  },

  async stock(): Promise<Stock[]> {
    try {
      return await request<Stock[]>('/inventory');
    } catch {
      throw new ApiUnavailableError();
    }
  },

  async notifications(): Promise<Notification[]> {
    try {
      return await request<Notification[]>('/notifications');
    } catch {
      throw new ApiUnavailableError();
    }
  }
};

export const fallback = {
  home: displayHome,
  products,
  cart: initialCart,
  stock: initialStock,
  notifications: initialNotifications,
  checkout: createDemoCheckout
};

