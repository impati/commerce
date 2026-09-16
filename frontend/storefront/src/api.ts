import {
  createDemoCheckout,
  displayHome,
  initialCart,
  initialNotifications,
  initialStock,
  products
} from './mockData';
import { session } from './session';
import type {
  Cart,
  Checkout,
  DisplayHome,
  IssuedAccessToken,
  Member,
  Notification,
  Product,
  Shipment,
  Stock
} from './types';
import type { OrderDetail, OrderPage } from './types';

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
  constructor(readonly status: number, message: string, readonly code?: string) {
    super(message);
  }
}

async function send(path: string, init?: RequestInit): Promise<Response> {
  const token = session.readAccess();
  return fetch(`${apiBase}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers
    }
  });
}

/**
 * 진행 중인 갱신. 동시에 나간 요청들이 한꺼번에 401을 받아도 갱신은 한 번만 돈다.
 *
 * 묶지 않으면 요청 수만큼 갱신이 나가고, 그만큼 member-service를 부르게 된다 — 요청당 조회를
 * 없애려는 목적과 정면으로 어긋난다.
 */
let refreshing: Promise<boolean> | null = null;

function refreshAccessToken(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const response = await fetch(`${apiBase}/sessions/refresh`, {
        method: 'POST',
        credentials: 'include'
      });
      if (!response.ok) {
        return false;
      }
      const issued = (await response.json()) as IssuedAccessToken;
      session.writeAccess(issued.accessToken);
      return true;
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

/**
 * 접근 토큰이 만료됐으면 한 번 갱신하고 재시도한다.
 *
 * 갱신은 세션 토큰이 살아 있을 때만 성공한다. 실패하면 401이 그대로 올라가고 호출자가
 * 로그인 화면으로 돌려보낸다.
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response = await send(path, init);
  if (response.status === 401 && (await refreshAccessToken())) {
    response = await send(path, init);
  }
  if (response.status === 401) {
    throw new UnauthorizedError();
  }
  if (!response.ok) {
    const body = await response.text();
    let message = body;
    let code: string | undefined;
    try {
      const parsed = JSON.parse(body) as { message?: string; code?: string };
      message = parsed.message ?? body;
      code = parsed.code;
    } catch {
      // 본문이 JSON이 아니면 그대로 쓴다
    }
    throw new ApiError(response.status, message, code);
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

  login(email: string, password: string): Promise<IssuedAccessToken> {
    return request<IssuedAccessToken>('/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
  },

  async logout(): Promise<void> {
    await fetch(`${apiBase}/logout`, {
      method: 'POST',
      credentials: 'include'
    });
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

  checkout(idempotencyKey: string, quoteId: string): Promise<Checkout> {
    return request<Checkout>('/checkout', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify({ paymentToken: 'card_test_success', quoteId })
    });
  },

  orders(cursor?: string): Promise<OrderPage> {
    const query = new URLSearchParams({ size: '20' });
    if (cursor) query.set('cursor', cursor);
    return request<OrderPage>(`/orders?${query}`);
  },

  order(orderId: string): Promise<OrderDetail> {
    return request<OrderDetail>(`/orders/${encodeURIComponent(orderId)}`);
  },

  checkoutResult(orderId: string): Promise<Checkout> {
    return request<Checkout>(`/orders/${orderId}/checkout-result`);
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
