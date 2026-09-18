const CHECKOUT_KEY = 'impati.checkout.';
let accessToken: string | null = null;

export type PendingCheckout = {
  idempotencyKey: string;
  orderId?: string;
  quoteId?: string;
  addressId?: string;
  addressConfirmationToken?: string;
};

type AddressChange = { key: string; phase: 'sending' | 'checking'; deadline: number };

function addressChangePrefix(memberId: string): string {
  return 'impati.address-change.' + memberId + '.';
}

function addressChanges(memberId: string): AddressChange[] {
  const changes: AddressChange[] = [];
  for (let index = 0; index < localStorage.length; index++) {
    const key = localStorage.key(index);
    if (key?.startsWith(addressChangePrefix(memberId))) {
      try {
        const value = JSON.parse(localStorage.getItem(key)!) as Omit<AddressChange, 'key'>;
        changes.push({ key, phase: value.phase, deadline: value.deadline });
      } catch {
        // 손상된 표식도 조회를 한 번 마치기 전에는 결과 미확인으로 취급한다.
        changes.push({ key, phase: 'checking', deadline: 0 });
      }
    }
  }
  return changes;
}

/**
 * 접근 토큰은 페이지 메모리에만 둔다. 장기 세션 토큰은 HttpOnly 쿠키라 JavaScript가 읽지 않는다.
 *
 * 이전 버전이 localStorage에 남긴 토큰은 마이그레이션 때 한 번 지운다. 체크아웃 복구 정보는
 * 인증 수단이 아니므로 그대로 보관한다 (ADR-0020).
 */
localStorage.removeItem('impati.session');
localStorage.removeItem('impati.access');

export const session = {
  addressChangePending(memberId: string): boolean {
    return addressChanges(memberId).length > 0;
  },
  addressChangeInFlight(memberId: string): boolean {
    return addressChanges(memberId).some(change => change.deadline > Date.now() && change.phase === 'sending');
  },
  beginAddressChange(memberId: string, timeoutMs = 10000): string {
    const key = addressChangePrefix(memberId) + crypto.randomUUID();
    localStorage.setItem(key, JSON.stringify({ phase: 'sending', deadline: Date.now() + timeoutMs }));
    window.dispatchEvent(new Event('address-change'));
    return key;
  },
  markAddressChangeChecking(key: string) {
    localStorage.setItem(key, JSON.stringify({ phase: 'checking', deadline: 0 }));
    window.dispatchEvent(new Event('address-change'));
  },
  finishAddressCheck(memberId: string) {
    for (const change of addressChanges(memberId)) {
      if (change.phase === 'checking' || change.deadline <= Date.now()) {
        localStorage.removeItem(change.key);
      }
    }
    window.dispatchEvent(new Event('address-change'));
  },
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
