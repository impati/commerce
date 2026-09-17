// @vitest-environment jsdom
import { afterEach, expect, test, vi } from 'vitest';
import { api, ApiError, ApiUnavailableError } from './api';
import { session } from './session';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  session.clear();
});

test('sends the confirmed quote with the original request key', async () => {
  const response = new Response('{}', {
    status: 201,
    headers: { 'Content-Type': 'application/json' }
  });
  const fetch = vi.fn().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetch);

  await api.checkout('key', 'quote');

  expect(fetch).toHaveBeenCalledWith('/api/checkout', expect.objectContaining({
    body: JSON.stringify({ paymentToken: 'card_test_success', quoteId: 'quote' }),
    headers: expect.objectContaining({ 'Idempotency-Key': 'key' })
  }));
});

test('preserves actionable quote errors from the BFF', async () => {
  const response = new Response(
    JSON.stringify({ code: 'quote_changed', message: 'Review' }),
    { status: 409 }
  );
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

  await expect(api.checkout('key', 'quote')).rejects.toMatchObject({
    status: 409,
    code: 'quote_changed',
    message: 'Review'
  } satisfies Partial<ApiError>);
});

test('bounds the complete cart request and aborts its fetch', async () => {
  vi.useFakeTimers();
  const fetch = vi.fn().mockReturnValue(new Promise(() => {}));
  vi.stubGlobal('fetch', fetch);
  const request = api.cart();
  const rejection = expect(request).rejects.toBeInstanceOf(ApiUnavailableError);
  const signal = fetch.mock.calls[0][1].signal as AbortSignal;
  expect(signal.aborted).toBe(false);
  await vi.advanceTimersByTimeAsync(10000);
  await rejection;
  expect(signal.aborted).toBe(true);
});

test('sends the viewed cart version with absolute quantity changes and removals', async () => {
  const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 200 })));
  vi.stubGlobal('fetch', fetch);
  await api.changeCartQuantity('sku/a', 3, 5);
  await api.removeCartItem('sku/a', 6);
  expect(fetch).toHaveBeenNthCalledWith(1, '/api/cart/items/sku%2Fa', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ quantity: 3, expectedVersion: 5 })
  }));
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/cart/items/sku%2Fa?expectedVersion=6', expect.objectContaining({
    method: 'DELETE'
  }));
});
