const CHECKOUT_KEY = 'impati.checkout.';
let accessToken: string | null = null;

export type PendingCheckout = { idempotencyKey: string; orderId?: string };

/**
 * 접근 토큰은 페이지 메모리에만 둔다. 장기 세션 토큰은 HttpOnly 쿠키라 JavaScript가 읽지 않는다.
 *
 * 이전 버전이 localStorage에 남긴 토큰은 마이그레이션 때 한 번 지운다. 체크아웃 복구 정보는
 * 인증 수단이 아니므로 그대로 보관한다 (ADR-0020).
 */
localStorage.removeItem('impati.session');
localStorage.removeItem('impati.access');

export const session = {
  readAccess(): string | null {
    return accessToken;
  },
  writeAccess(token: string) {
    accessToken = token;
  },
  clear() {
    accessToken = null;
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
