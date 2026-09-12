const SESSION_KEY = 'impati.session';
const ACCESS_KEY = 'impati.access';
const CHECKOUT_KEY = 'impati.checkout.';

export type PendingCheckout = { idempotencyKey: string; orderId?: string };

/**
 * 세션 토큰과 접근 토큰 보관.
 *
 * 요청에는 접근 토큰을 붙이고, 그것이 만료되면 세션 토큰으로 새로 받는다 (ADR-0007).
 * 세션 토큰은 갱신에만 쓰이며 수명이 14일이라 접근 토큰보다 탈취 피해가 크다.
 *
 * TODO localStorage는 XSS로 읽힌다. 특히 세션 토큰이 그렇다. 운영에서는 HttpOnly 쿠키가 맞고,
 * 그러려면 게이트웨이가 쿠키를 세팅하고 CSRF 대응이 따라온다. BL-0005.
 */
export const session = {
  read(): string | null {
    return localStorage.getItem(SESSION_KEY);
  },
  readAccess(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  },
  write(sessionToken: string, accessToken: string) {
    localStorage.setItem(SESSION_KEY, sessionToken);
    localStorage.setItem(ACCESS_KEY, accessToken);
  },
  writeAccess(accessToken: string) {
    localStorage.setItem(ACCESS_KEY, accessToken);
  },
  clear() {
    localStorage.removeItem(SESSION_KEY);
    localStorage.removeItem(ACCESS_KEY);
  },
  readPendingCheckout(memberId: string): PendingCheckout | null {
    const value = localStorage.getItem(CHECKOUT_KEY + memberId);
    if (!value) return null;
    try {
      return JSON.parse(value) as PendingCheckout;
    } catch {
      localStorage.removeItem(CHECKOUT_KEY + memberId);
      return null;
    }
  },
  writePendingCheckout(memberId: string, pending: PendingCheckout) {
    localStorage.setItem(CHECKOUT_KEY + memberId, JSON.stringify(pending));
  },
  clearPendingCheckout(memberId: string) {
    localStorage.removeItem(CHECKOUT_KEY + memberId);
  }
};
