// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest';
import { api, ApiError } from './api';
import { session } from './session';
afterEach(() => { vi.unstubAllGlobals(); session.clear(); });
test('sends the confirmed quote with the original request key', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response('{}', { status: 201, headers: { 'Content-Type': 'application/json' } }));
  vi.stubGlobal('fetch', fetch);
  await api.checkout('key', 'quote');
  expect(fetch).toHaveBeenCalledWith('/api/checkout', expect.objectContaining({
    body: JSON.stringify({ paymentToken: 'card_test_success', quoteId: 'quote' }),
    headers: expect.objectContaining({ 'Idempotency-Key': 'key' })
  }));
});
test('preserves actionable quote errors from the BFF', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'quote_changed', message: 'Review' }), { status: 409 })));
  await expect(api.checkout('key', 'quote')).rejects.toMatchObject({ status: 409, code: 'quote_changed', message: 'Review' } satisfies Partial<ApiError>);
});
