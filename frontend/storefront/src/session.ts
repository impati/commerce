const STORAGE_KEY = 'impati.session';

/**
 * 세션 토큰 보관.
 *
 * TODO localStorage는 XSS로 읽힌다. 운영에서는 HttpOnly 쿠키가 맞고, 그러려면 게이트웨이가
 * 쿠키를 세팅하고 CSRF 대응이 따라온다. BL-0005.
 */
export const session = {
  read(): string | null {
    return localStorage.getItem(STORAGE_KEY);
  },
  write(token: string) {
    localStorage.setItem(STORAGE_KEY, token);
  },
  clear() {
    localStorage.removeItem(STORAGE_KEY);
  }
};
