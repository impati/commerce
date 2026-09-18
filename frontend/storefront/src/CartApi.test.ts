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

  await api.checkout('key', 'quote', 'address', 'address-token');

  expect(fetch).toHaveBeenCalledWith('/api/checkout', expect.objectContaining({
    body: JSON.stringify({ paymentToken: 'card_test_success', quoteId: 'quote', addressId: 'address', addressConfirmationToken: 'address-token' }),
    headers: expect.objectContaining({ 'Idempotency-Key': 'key' })
  }));
});

test('preserves actionable quote errors from the BFF', async () => {
  const response = new Response(
    JSON.stringify({ code: 'quote_changed', message: 'Review' }),
    { status: 409 }
  );
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

  await expect(api.checkout('key', 'quote', 'address', 'address-token')).rejects.toMatchObject({
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

// [PD-0022-R4] 관리 경로에는 조회 버전을 포함하고 편집으로 기본 지정을 바꾸지 않는다.
test('sends address management versions and encodes identifiers', async () => {
  const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 200 })));
  vi.stubGlobal('fetch', fetch);
  const address = { alias: 'home', recipient: 'Owner', phone: '010', line1: 'Road', city: 'Seoul',
    postalCode: '00000', defaultAddress: true };
  await api.addAddress(address, 4);
  await api.updateAddress('address/a', address, 5);
  await api.setDefaultAddress('address/a', 6);
  await api.removeAddress('address/a', 7);
  expect(fetch).toHaveBeenNthCalledWith(1, '/api/me/addresses', expect.objectContaining({
    method: 'POST', body: JSON.stringify({ ...address, expectedVersion: 4 })
  }));
  const { defaultAddress: _defaultAddress, ...fields } = address;
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/me/addresses/address%2Fa', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ ...fields, expectedVersion: 5 })
  }));
  expect(fetch).toHaveBeenNthCalledWith(3, '/api/me/addresses/address%2Fa/default', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ expectedVersion: 6 })
  }));
  expect(fetch).toHaveBeenNthCalledWith(4, '/api/me/addresses/address%2Fa?expectedVersion=7', expect.objectContaining({ method: 'DELETE' }));
});

// [PD-0022-R9] 응답이 오지 않는 관리 요청도 제한 시간 이후 결과 조회로 전환할 수 있다.
test('bounds an address mutation without issuing a second write', async () => {
  vi.useFakeTimers();
  const fetch = vi.fn().mockReturnValue(new Promise(() => {}));
  vi.stubGlobal('fetch', fetch);
  const request = api.removeAddress('a', 4);
  const rejection = expect(request).rejects.toBeInstanceOf(ApiUnavailableError);
  await vi.advanceTimersByTimeAsync(10000);
  await rejection;
  expect(fetch).toHaveBeenCalledTimes(1);
  expect((fetch.mock.calls[0][1].signal as AbortSignal).aborted).toBe(true);
});
