import {
  createDemoCheckout,
  displayHome,
  initialCart,
  initialNotifications,
  initialStock,
  products
} from './mockData';
import { session } from './session';
import type { Cart, Checkout, DisplayHome, Member, Notification, Product, Session, Shipment, Stock } from './types';

const apiBase = import.meta.env.VITE_API_BASE_URL ?? '/api';

export class ApiUnavailableError extends Error {
  constructor() {
    super('api unavailable');
  }
}

/** 세션이 없거나 만료됐다. 호출자는 로그인 화면으로 돌려보내야 한다. */
export class UnauthorizedError extends Error {
  constructor() {
    super('unauthorized');
  }
}

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = session.read();
  const response = await fetch(`${apiBase}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers
    },
    ...init
  });
  if (response.status === 401) {
    throw new UnauthorizedError();
  }
  if (!response.ok) {
    const body = await response.text();
    let message = body;
    try {
      message = (JSON.parse(body) as { message?: string }).message ?? body;
    } catch {
      // 본문이 JSON이 아니면 그대로 쓴다
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

/** 인증이 필요 없는 조회. 실패하면 데모 데이터로 대체한다. */
async function publicRequest<T>(path: string): Promise<T> {
  try {
    return await request<T>(path);
  } catch (error) {
    if (error instanceof UnauthorizedError) {
      throw error;
    }
    throw new ApiUnavailableError();
  }
}

export const api = {
  register(email: string, name: string, password: string): Promise<Member> {
    return request<Member>('/members', {
      method: 'POST',
      body: JSON.stringify({ email, name, password })
    });
  },

  verifyEmail(token: string): Promise<Member> {
    return request<Member>('/members/verifications', {
      method: 'POST',
      body: JSON.stringify({ token })
    });
  },

  login(email: string, password: string): Promise<Session> {
    return request<Session>('/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
  },

  logout(): Promise<void> {
    return request<void>('/logout', { method: 'POST' });
  },

  me(): Promise<Member> {
    return request<Member>('/me');
  },

  home(): Promise<DisplayHome> {
    return publicRequest<DisplayHome>('/display/home');
  },

  products(): Promise<Product[]> {
    return publicRequest<Product[]>('/products');
  },

  stock(): Promise<Stock[]> {
    return publicRequest<Stock[]>('/inventory');
  },

  cart(): Promise<Cart> {
    return request<Cart>('/cart');
  },

  addCartItem(skuId: string, quantity: number): Promise<Cart> {
    return request<Cart>('/cart/items', {
      method: 'POST',
      body: JSON.stringify({ skuId, quantity })
    });
  },

  checkout(): Promise<Checkout> {
    return request<Checkout>('/checkout', {
      method: 'POST',
      body: JSON.stringify({ paymentToken: 'card_test_success' })
    });
  },

  ship(shipmentId: string): Promise<Shipment> {
    return request<Shipment>(`/shipments/${shipmentId}/ship`, { method: 'POST', body: '{}' });
  },

  deliver(shipmentId: string): Promise<{ shipment: Shipment; order: Checkout['order'] }> {
    return request<{ shipment: Shipment; order: Checkout['order'] }>(`/shipments/${shipmentId}/deliver`, {
      method: 'POST',
      body: '{}'
    });
  },

  notifications(): Promise<Notification[]> {
    return request<Notification[]>('/notifications');
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
