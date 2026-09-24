// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { beforeEach, afterEach, expect, test, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import { api, ApiError, UnauthorizedError } from './api';
import { session } from './session';
import { formatMoney } from './format';
import type { Cart, Checkout, Product } from './types';

const product: Product = {
  id: 'p',
  name: 'Catalog product',
  brand: 'B',
  category: 'home',
  description: 'D',
  status: 'PUBLISHED',
  tags: [],
  skus: [{
    id: 'sku',
    productId: 'p',
    name: 'Catalog SKU',
    price: { amount: 999, currency: 'KRW' },
    attributes: {},
    status: 'PUBLISHED'
  }]
};

const originalQuote = {
  id: 'quote-original',
  cartVersion: 5,
  lines: [{
    skuId: 'sku',
    quantity: 2,
    unitPrice: { amount: 100, currency: 'KRW' },
    lineTotal: { amount: 200, currency: 'KRW' }
  }],
  total: { amount: 3200, currency: 'KRW' },
  priceBreakdown: {
    productAmount: { amount: 200, currency: 'KRW' },
    shippingFee: { amount: 3000, currency: 'KRW' },
    totalAmount: { amount: 3200, currency: 'KRW' }
  }
};

const cart: Cart = {
  memberId: 'm',
  version: 5,
  lines: [{
    skuId: 'sku',
    quantity: 2,
    productName: 'Cart product',
    skuName: 'Cart SKU',
    availableQuantity: 3,
    informationAvailable: true
  }],
  quote: originalQuote,
  unavailable: [],
  checkoutAllowed: true
};

const updatedQuote = {
  ...originalQuote,
  id: 'quote-new',
  cartVersion: 6,
  lines: [{
    ...originalQuote.lines[0],
    quantity: 3,
    lineTotal: { amount: 300, currency: 'KRW' }
  }],
  total: { amount: 3300, currency: 'KRW' },
  priceBreakdown: {
    productAmount: { amount: 300, currency: 'KRW' },
    shippingFee: { amount: 3000, currency: 'KRW' },
    totalAmount: { amount: 3300, currency: 'KRW' }
  }
};

const updated: Cart = {
  ...cart,
  version: 6,
  lines: [{ ...cart.lines[0], quantity: 3 }],
  quote: updatedQuote
};

const failedCheckout: Checkout = {
  order: {
    id: 'order-original',
    memberId: 'm',
    lines: [{
      skuId: 'sku',
      productId: 'p',
      productName: 'Cart product',
      skuName: 'Cart SKU',
      quantity: 2,
      unitPrice: originalQuote.lines[0].unitPrice,
      lineTotal: originalQuote.lines[0].lineTotal
    }],
    shippingAddress: {
      id: 'address',
      alias: 'home',
      recipient: 'Member',
      phone: '010-0000-0000',
      line1: 'Road',
      city: 'Seoul',
      postalCode: '00000',
      defaultAddress: true
    },
    paymentId: null,
    shipmentId: null,
    inventoryReservationId: null,
    checkoutStatus: 'FAILED',
    paymentCleanupStatus: 'DONE',
    failureCode: 'out_of_stock',
    status: 'CANCELLED',
    total: originalQuote.total,
    priceBreakdown: originalQuote.priceBreakdown
  },
  shipment: null,
  payment: null
};

beforeEach(() => {
  localStorage.clear();
  session.clear();
  vi.spyOn(api, 'me').mockResolvedValue({ id: 'm', name: 'Member', email: 'm@example.test', status: 'ACTIVE', addressBookVersion: 1, addresses: [{ ...failedCheckout.order.shippingAddress, confirmationToken: 'address-token' }] });
  vi.spyOn(api, 'home').mockResolvedValue({ title: 'Home', subtitle: 'Storefront', sections: [] });
  vi.spyOn(api, 'products').mockResolvedValue([product]);
  vi.spyOn(api, 'stock').mockResolvedValue([]);
  vi.spyOn(api, 'notifications').mockResolvedValue([]);
  vi.spyOn(api, 'cart').mockResolvedValue(cart);
  vi.spyOn(api, 'addCartItem');
  vi.spyOn(api, 'checkout');
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function open() {
  render(<MemoryRouter><App /></MemoryRouter>);
}

async function ready() {
  await waitFor(() => expect(screen.getByRole('button', { name: 'Checkout' })).toBeEnabled());
}

// [PD-0021-R1, PD-0026-R10] 상품 목록의 가격을 합산하지 않고 서버 견적의 금액 구성을 표시한다.
test('shows the server quote breakdown instead of multiplying catalog prices', async () => {
  open();
  await ready();
  expect(screen.getByText('상품 금액')).toBeInTheDocument();
  expect(screen.getByText(formatMoney(originalQuote.priceBreakdown.productAmount))).toBeInTheDocument();
  expect(screen.getByText('배송비')).toBeInTheDocument();
  expect(screen.getByText(formatMoney(originalQuote.priceBreakdown.shippingFee))).toBeInTheDocument();
  expect(screen.getByText('최종 결제 금액')).toBeInTheDocument();
  expect(screen.getByText(formatMoney(originalQuote.total))).toBeInTheDocument();
  expect(screen.queryByText(formatMoney({ amount: 1998, currency: 'KRW' }))).not.toBeInTheDocument();
});

// [PD-0026-R10] 결제 결과도 주문에 확정된 금액 구성을 그대로 표시한다.
test('shows the confirmed price breakdown in the checkout result', async () => {
  vi.mocked(api.checkout).mockResolvedValue(failedCheckout);
  open();
  await ready();

  fireEvent.click(screen.getByRole('button', { name: 'Checkout' }));

  expect(await screen.findByRole('link', { name: 'order-original · 상세 보기' })).toBeInTheDocument();
  expect(screen.getAllByText(formatMoney(failedCheckout.order.priceBreakdown.productAmount))).toHaveLength(2);
  expect(screen.getAllByText(formatMoney(failedCheckout.order.priceBreakdown.shippingFee))).toHaveLength(2);
  expect(screen.getAllByText(formatMoney(failedCheckout.order.priceBreakdown.totalAmount))).toHaveLength(2);
});

// [PD-0021-R4] 견적 미확인은 0원으로 바뀌지 않으며 결제할 수 없다.
test('keeps quantities but disables checkout when the quote is unavailable', async () => {
  vi.mocked(api.cart).mockResolvedValue({ ...cart, quote: null, unavailable: ['quote'], checkoutAllowed: false });
  open();
  expect(await screen.findByText('금액 확인 불가')).toBeInTheDocument();
  expect(await screen.findByText('2개')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
});

test('shows a cart error instead of a fake empty cart', async () => {
  vi.mocked(api.cart).mockRejectedValue(new TypeError('offline'));
  open();
  expect(await screen.findByRole('alert')).toHaveTextContent('장바구니를 확인할 수 없습니다');
  expect(screen.queryByText('Cart empty')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
});

// [PD-0021-R5] 담기 성공 뒤 조회 실패는 조회만 다시 시도하고 상품을 중복해서 담지 않는다.
test('reports committed additions and retries only the failed view lookup', async () => {
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockRejectedValueOnce(new TypeError('quote offline')).mockResolvedValueOnce(updated);
  vi.mocked(api.addCartItem).mockResolvedValue({ memberId: 'm', version: 6, lines: [{ skuId: 'sku', quantity: 3 }] });
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Add' }));
  expect(await screen.findByText('상품을 담았습니다. 장바구니와 금액을 다시 확인해주세요.')).toBeInTheDocument();
  expect(screen.getByText('3개')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '장바구니 다시 확인' }));
  await ready();
  expect(api.addCartItem).toHaveBeenCalledTimes(1);
  expect(api.cart).toHaveBeenCalledTimes(3);
  expect(screen.getByText(formatMoney(updatedQuote.total))).toBeInTheDocument();
});

test('does not simulate a successful addition on an explicit rejection', async () => {
  vi.mocked(api.addCartItem).mockRejectedValue(new ApiError(409, '장바구니가 변경됐습니다.', 'cart_changed'));
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Add' }));
  expect(await screen.findByText('장바구니가 변경됐습니다.')).toBeInTheDocument();
  expect(screen.getByText('2개')).toBeInTheDocument();
  expect(screen.queryByText('3개')).not.toBeInTheDocument();
  expect(screen.queryByText('상품을 담았습니다.')).not.toBeInTheDocument();
});

// [PD-0021-R5] 전송 실패에서 성공을 단정하거나 변경 요청을 자동 반복하지 않는다.
test('requeries an unknown addition without repeating the command', async () => {
  vi.mocked(api.addCartItem).mockRejectedValue(new TypeError('lost response'));
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockResolvedValueOnce(updated);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Add' }));
  expect(await screen.findByText('상품 담기 결과를 확인하지 못했습니다. 장바구니를 다시 확인해주세요.')).toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('3개')).toBeInTheDocument());
  expect(api.addCartItem).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('상품을 담았습니다.')).not.toBeInTheDocument();
});

// [PD-0021-R2, PD-0021-R3] 새 견적을 조회해도 자동으로 주문하지 않고 새 클릭을 기다린다.
test('requires a new confirmation after a quote conflict', async () => {
  vi.mocked(api.checkout).mockRejectedValue(new ApiError(409, '새 견적을 확인해주세요.', 'quote_changed'));
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockResolvedValueOnce(updated);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Checkout' }));
  expect(await screen.findByText('새 견적을 확인해주세요.')).toBeInTheDocument();
  expect(api.checkout).toHaveBeenCalledTimes(1);
  expect(api.checkout).toHaveBeenCalledWith(expect.any(String), 'quote-original', 'address', 'address-token');
  expect(session.readPendingCheckout('m')).toBeNull();
  expect(screen.getByText(formatMoney(updatedQuote.total))).toBeInTheDocument();
});

// [PD-0021-R6] 새 견적을 조회해도 결과 미확인 주문은 원래 키와 견적으로 회수한다.
test('recovers an unknown checkout using its original key and quote', async () => {
  vi.mocked(api.checkout).mockRejectedValueOnce(new TypeError('lost')).mockRejectedValueOnce(new TypeError('lost'))
    .mockResolvedValueOnce(failedCheckout);
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockResolvedValueOnce(updated);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Checkout' }));
  await waitFor(() => expect(api.checkout).toHaveBeenCalledTimes(2));
  const pending = session.readPendingCheckout('m')!;
  expect(pending.quoteId).toBe('quote-original');
  expect(pending.addressId).toBe('address');
  expect(pending.addressConfirmationToken).toBe('address-token');
  await waitFor(() => expect(screen.getByRole('button', { name: '장바구니 다시 확인' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '장바구니 다시 확인' }));
  await waitFor(() => expect(screen.getByText(formatMoney(updatedQuote.total))).toBeInTheDocument());
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Checkout' }));
  await waitFor(() => expect(api.checkout).toHaveBeenCalledTimes(3));
  expect(api.checkout).toHaveBeenNthCalledWith(3, pending.idempotencyKey, 'quote-original', 'address', 'address-token');
  await waitFor(() => expect(session.readPendingCheckout('m')).toBeNull());
});

test('disables checkout while stock is unavailable', async () => {
  vi.mocked(api.cart).mockResolvedValue({ ...cart, lines: [{ ...cart.lines[0], availableQuantity: null }], unavailable: ['inventory:sku'], checkoutAllowed: false });
  open();
  expect(await screen.findByText('재고 확인 불가')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
});

test('distinguishes an empty cart from a failed price lookup', async () => {
  vi.mocked(api.cart).mockResolvedValue({ memberId: 'm', version: 5, lines: [], quote: null, unavailable: [], checkoutAllowed: false });
  open();
  expect(await screen.findByText('Cart empty')).toBeInTheDocument();
  expect(screen.getByText('상품을 담아주세요')).toBeInTheDocument();
  expect(screen.queryByText('금액 확인 불가')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
});

// [PD-0021-R3] 먼저 시작한 조회가 늦게 끝나도 최신 확인 내용을 유지한다.
test('ignores an older cart response after a newer storefront refresh', async () => {
  let resolveOld!: (value: Cart) => void;
  const old = new Promise<Cart>(resolve => { resolveOld = resolve; });
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockReturnValueOnce(old).mockResolvedValueOnce(updated);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: '장바구니 다시 확인' }));
  await waitFor(() => expect(api.cart).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByTitle('Refresh'));
  await waitFor(() => expect(screen.getByText('3개')).toBeInTheDocument());
  await act(async () => { resolveOld(cart); });
  expect(screen.getByText('3개')).toBeInTheDocument();
  expect(screen.queryByText('2개')).not.toBeInTheDocument();
  expect(screen.getByText(formatMoney(updatedQuote.total))).toBeInTheDocument();
});

test('ignores an older failed lookup after a newer successful refresh', async () => {
  let rejectOld!: (reason: Error) => void;
  const old = new Promise<Cart>((_, reject) => { rejectOld = reject; });
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockReturnValueOnce(old).mockResolvedValueOnce(updated);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: '장바구니 다시 확인' }));
  await waitFor(() => expect(api.cart).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByTitle('Refresh'));
  await waitFor(() => expect(screen.getByText('3개')).toBeInTheDocument());
  await act(async () => { rejectOld(new UnauthorizedError()); });
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeEnabled();
  expect(screen.getByRole('heading', { name: 'Member' })).toBeInTheDocument();
});

test('does not apply a previous members cart after logout and another login', async () => {
  let resolveOld!: (value: Cart) => void;
  const old = new Promise<Cart>(resolve => { resolveOld = resolve; });
  const nextMember = { id: 'next', name: 'Next member', email: 'next@example.test', status: 'ACTIVE', addressBookVersion: 1, addresses: [{ ...failedCheckout.order.shippingAddress, confirmationToken: 'address-token' }] };
  const nextCart = { ...updated, memberId: 'next', lines: [{ ...updated.lines[0], productName: 'Next cart product' }] };
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockReturnValueOnce(old).mockResolvedValueOnce(nextCart);
  vi.spyOn(api, 'logout').mockResolvedValue();
  vi.spyOn(api, 'login').mockResolvedValue({ accessToken: 'next-token', accessTokenExpiresAt: '2030-01-01T00:00:00Z' });
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: '장바구니 다시 확인' }));
  await waitFor(() => expect(api.cart).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByRole('button', { name: '로그아웃' }));
  await screen.findByRole('button', { name: '로그인' });
  await waitFor(() => expect(screen.getByTitle('Refresh')).toBeEnabled());
  vi.mocked(api.me).mockResolvedValue(nextMember);
  fireEvent.click(screen.getByRole('button', { name: '로그인' }));
  await screen.findByText('Next cart product');
  await act(async () => { resolveOld(cart); });
  expect(screen.getByText('Next cart product')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeEnabled();
  expect(screen.queryByText('Cart product')).not.toBeInTheDocument();
  expect(screen.getByText('3개')).toBeInTheDocument();
});

test('does not let a delayed bootstrap replace a newer cart lookup', async () => {
  let resolveOld!: (value: Cart) => void;
  const old = new Promise<Cart>(resolve => { resolveOld = resolve; });
  vi.mocked(api.cart).mockReturnValueOnce(old).mockResolvedValueOnce(updated);
  open();
  await waitFor(() => expect(api.cart).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: '장바구니 다시 확인' }));
  await screen.findByText('3개');
  await act(async () => { resolveOld(cart); });
  expect(screen.getByText('3개')).toBeInTheDocument();
  expect(screen.queryByText('2개')).not.toBeInTheDocument();
});

test('invalidates a query started before a committed addition', async () => {
  let resolveOld!: (value: Cart) => void;
  const old = new Promise<Cart>(resolve => { resolveOld = resolve; });
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockReturnValueOnce(old).mockResolvedValueOnce(updated);
  vi.mocked(api.addCartItem).mockResolvedValue({ memberId: 'm', version: 6, lines: [{ skuId: 'sku', quantity: 3 }] });
  open();
  await ready();
  fireEvent.click(screen.getByTitle('Refresh'));
  await waitFor(() => expect(api.cart).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByRole('button', { name: 'Add' }));
  await screen.findByText('3개');
  await act(async () => { resolveOld(cart); });
  expect(screen.getByText('3개')).toBeInTheDocument();
  expect(screen.queryByText('2개')).not.toBeInTheDocument();
});

test('labels a cart-only failure and clears the label after recovery', async () => {
  vi.mocked(api.cart).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(cart);
  open();
  const failure = await screen.findByText('장바구니 조회 실패');
  expect(failure).toHaveClass('partial');
  expect(screen.queryByText('Gateway connected')).not.toBeInTheDocument();
  expect(screen.queryByText('Partial — demo: cart')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '장바구니 다시 확인' }));
  await ready();
  expect(screen.getByText('Gateway connected')).toBeInTheDocument();
  expect(screen.queryByText('장바구니 조회 실패')).not.toBeInTheDocument();
});

test('keeps authenticated purchase data outside the demo fallback status', async () => {
  vi.mocked(api.home).mockRejectedValue(new TypeError('offline'));
  vi.mocked(api.products).mockRejectedValue(new TypeError('offline'));
  vi.mocked(api.stock).mockRejectedValue(new TypeError('offline'));
  vi.mocked(api.notifications).mockRejectedValue(new TypeError('offline'));
  open();
  await ready();
  expect(screen.getByText(formatMoney(originalQuote.total))).toBeInTheDocument();
  expect(screen.getByText(/Partial — demo:/)).toHaveClass('partial');
  expect(screen.queryByText('Demo mode')).not.toBeInTheDocument();
});

test('ignores bootstrap identity arriving after a newer login', async () => {
  let resolveOld!: (value: Awaited<ReturnType<typeof api.me>>) => void;
  const old = new Promise<Awaited<ReturnType<typeof api.me>>>(resolve => { resolveOld = resolve; });
  const nextMember = { id: 'next', name: 'Next member', email: 'next@example.test', status: 'ACTIVE', addressBookVersion: 1, addresses: [{ ...failedCheckout.order.shippingAddress, confirmationToken: 'address-token' }] };
  const nextCart = { ...updated, memberId: 'next', lines: [{ ...updated.lines[0], productName: 'Next cart product' }] };
  vi.mocked(api.me).mockReturnValueOnce(old).mockResolvedValue(nextMember);
  vi.mocked(api.cart).mockResolvedValue(nextCart);
  vi.spyOn(api, 'login').mockResolvedValue({ accessToken: 'next-token', accessTokenExpiresAt: '2030-01-01T00:00:00Z' });
  open();
  await waitFor(() => expect(api.me).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: '로그인' }));
  await screen.findByText('Next cart product');
  await act(async () => {
    resolveOld({ id: 'm', name: 'Member', email: 'm@example.test', status: 'ACTIVE', addressBookVersion: 1, addresses: [{ ...failedCheckout.order.shippingAddress, confirmationToken: 'address-token' }] });
  });
  expect(screen.getByRole('heading', { name: 'Next member' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Member' })).not.toBeInTheDocument();
  expect(api.cart).toHaveBeenCalledTimes(1);
});

// BL-0069: Unapplied input must not purchase the old quantity/quote.
test.each(['Enter', 'button'] as const)('blocks checkout until a typed quantity is explicitly applied via %s', async method => {
  vi.spyOn(api, 'changeCartQuantity').mockResolvedValue({ memberId: 'm', version: 6, lines: [{ skuId: 'sku', quantity: 3 }] });
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockResolvedValueOnce(updated);
  open();
  await ready();
  const input = screen.getByRole('textbox', { name: 'Cart product 수량' });
  fireEvent.change(input, { target: { value: '3' } });
  expect(screen.getByText('수량 변경을 적용해주세요.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  expect(api.changeCartQuantity).not.toHaveBeenCalled();
  if (method === 'Enter') {
    fireEvent.keyDown(input, { key: 'Enter' });
  } else {
    fireEvent.click(screen.getByRole('button', { name: 'Cart product 수량 적용' }));
  }
  await ready();
  expect(api.changeCartQuantity).toHaveBeenCalledWith('sku', 3, 5);
  expect(api.changeCartQuantity).toHaveBeenCalledTimes(1);
  expect(screen.getByText(formatMoney(updatedQuote.total))).toBeInTheDocument();
  expect(screen.queryByText('수량 변경을 적용해주세요.')).not.toBeInTheDocument();
});

test('rejects invalid input and re-enables checkout when the input is restored', async () => {
  vi.spyOn(api, 'changeCartQuantity');
  open();
  await ready();
  const input = screen.getByRole('textbox', { name: 'Cart product 수량' });
  for (const value of ['', '0', '-1', '1.5', '2147483648']) {
    fireEvent.change(input, { target: { value } });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(screen.getByRole('button', { name: 'Cart product 수량 적용' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  }
  expect(api.changeCartQuantity).not.toHaveBeenCalled();
  fireEvent.change(input, { target: { value: '2' } });
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeEnabled();
});

test('decrements immediately and disables minus at one', async () => {
  const one = { ...cart, version: 6, lines: [{ ...cart.lines[0], quantity: 1 }] };
  vi.spyOn(api, 'changeCartQuantity').mockResolvedValue(one);
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockResolvedValueOnce(one);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Cart product 수량 감소' }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Cart product 수량 감소' })).toBeDisabled());
  expect(api.changeCartQuantity).toHaveBeenCalledWith('sku', 1, 5);
});

test('locks cart commands and checkout through the follow-up quote lookup', async () => {
  let resolveView!: (value: Cart) => void;
  const lookup = new Promise<Cart>(resolve => {
    resolveView = resolve;
  });
  vi.spyOn(api, 'changeCartQuantity').mockResolvedValue({ memberId: 'm', version: 6, lines: [{ skuId: 'sku', quantity: 3 }] });
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockReturnValueOnce(lookup);
  open();
  await ready();
  const plus = screen.getByRole('button', { name: 'Cart product 수량 증가' });
  fireEvent.click(plus);
  fireEvent.click(plus);
  await waitFor(() => expect(api.cart).toHaveBeenCalledTimes(2));
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'sku 수량 증가' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'sku 제거' })).toBeDisabled();
  expect(api.changeCartQuantity).toHaveBeenCalledTimes(1);
  await act(async () => {
    resolveView(updated);
  });
  await ready();
});

test('removes a line and reports success independently of a failed view lookup', async () => {
  vi.spyOn(api, 'removeCartItem').mockResolvedValue({ memberId: 'm', version: 6, lines: [] });
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockRejectedValueOnce(new TypeError('offline'))
    .mockResolvedValueOnce({ memberId: 'm', version: 6, lines: [], checkoutAllowed: false });
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Cart product 제거' }));
  expect(await screen.findByText('상품을 제거했습니다. 장바구니와 금액을 다시 확인해주세요.')).toBeInTheDocument();
  expect(api.removeCartItem).toHaveBeenCalledWith('sku', 5);
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '장바구니 다시 확인' }));
  expect(await screen.findByText('Cart empty')).toBeInTheDocument();
  expect(api.removeCartItem).toHaveBeenCalledTimes(1);
});

test('reloads a conflicting quantity without automatically reapplying the request', async () => {
  vi.spyOn(api, 'changeCartQuantity').mockRejectedValue(new ApiError(409, '장바구니가 변경됐습니다.', 'cart_changed'));
  vi.mocked(api.cart).mockResolvedValueOnce(cart).mockResolvedValueOnce(updated);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Cart product 수량 증가' }));
  expect(await screen.findByText('장바구니가 변경됐습니다.')).toBeInTheDocument();
  await ready();
  expect(screen.getByRole('textbox', { name: 'Cart product 수량' })).toHaveValue('3');
  expect(api.changeCartQuantity).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('수량을 변경했습니다.')).not.toBeInTheDocument();
});

test('requeries an unknown removal without declaring or repeating a successful removal', async () => {
  vi.spyOn(api, 'removeCartItem').mockRejectedValue(new TypeError('lost response'));
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Cart product 제거' }));
  expect(await screen.findByText('상품 제거 결과를 확인하지 못했습니다. 장바구니를 다시 확인해주세요.')).toBeInTheDocument();
  await ready();
  expect(api.removeCartItem).toHaveBeenCalledTimes(1);
  expect(screen.getByText('2개')).toBeInTheDocument();
  expect(screen.queryByText('상품을 제거했습니다.')).not.toBeInTheDocument();
});

test('clears unapplied quantities when logging back into the same account', async () => {
  vi.spyOn(api, 'logout').mockResolvedValue();
  vi.spyOn(api, 'login').mockResolvedValue({ accessToken: 'token', accessTokenExpiresAt: '2030-01-01T00:00:00Z' });
  open();
  await ready();
  fireEvent.change(screen.getByRole('textbox', { name: 'Cart product 수량' }), { target: { value: '3' } });
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '로그아웃' }));
  await screen.findByRole('button', { name: '로그인' });
  await waitFor(() => expect(screen.getByTitle('Refresh')).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '로그인' }));
  await ready();
  expect(screen.getByRole('textbox', { name: 'Cart product 수량' })).toHaveValue('2');
  expect(screen.queryByText('수량 변경을 적용해주세요.')).not.toBeInTheDocument();
});

// [PD-0022-R3] 배송지가 없으면 구매 견적이 있어도 새 주문을 시작하지 않는다.
test('blocks a new checkout when the member has no delivery address', async () => {
  vi.mocked(api.me).mockResolvedValue({ id: 'm', name: 'Member', email: 'm@example.test', status: 'ACTIVE',
    addressBookVersion: 0, addresses: [] });
  open();
  await screen.findByText('Cart product');
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  expect(api.checkout).not.toHaveBeenCalled();
});

// [PD-0022-R6] 서버가 발견한 주소 변경은 최신 값 조회와 명시적 확인으로 이어진다.
test('blocks checkout after address_changed until the updated address is acknowledged', async () => {
  const original = { ...failedCheckout.order.shippingAddress, confirmationToken: 'address-token' };
  const latest = { ...original, line1: 'Updated road', confirmationToken: 'updated-address-token' };
  const identity = { id: 'm', name: 'Member', email: 'm@example.test', status: 'ACTIVE', addressBookVersion: 1 };
  vi.mocked(api.me).mockResolvedValueOnce({ ...identity, addresses: [original] })
    .mockResolvedValue({ ...identity, addressBookVersion: 2, addresses: [latest] });
  vi.mocked(api.checkout).mockRejectedValueOnce(new ApiError(409, '배송지가 변경됐습니다.', 'address_changed'))
    .mockResolvedValueOnce(failedCheckout);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Checkout' }));
  const acknowledgement = await screen.findByRole('button', { name: '변경된 배송지 확인' });
  expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
  expect(session.readPendingCheckout('m')).toBeNull();
  expect(api.checkout).toHaveBeenCalledTimes(1);
  fireEvent.click(acknowledgement);
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Checkout' }));
  await waitFor(() => expect(api.checkout).toHaveBeenCalledTimes(2));
  expect(api.checkout).toHaveBeenLastCalledWith(expect.any(String), 'quote-original', 'address', 'updated-address-token');
});

// [PD-0022-R7] 다른 주소를 선택해도 미확인 주문은 원래 주소 확인값으로 회수한다.
test('recovers the original address even after another address is chosen on the screen', async () => {
  const original = { ...failedCheckout.order.shippingAddress, confirmationToken: 'address-token' };
  const office = { ...original, id: 'office', alias: 'office', defaultAddress: false, confirmationToken: 'office-token' };
  vi.mocked(api.me).mockResolvedValue({ id: 'm', name: 'Member', email: 'm@example.test', status: 'ACTIVE',
    addressBookVersion: 2, addresses: [original, office] });
  vi.mocked(api.checkout).mockRejectedValueOnce(new TypeError('lost')).mockRejectedValueOnce(new TypeError('lost'))
    .mockResolvedValueOnce(failedCheckout);
  open();
  await ready();
  fireEvent.click(screen.getByRole('button', { name: 'Checkout' }));
  await waitFor(() => expect(api.checkout).toHaveBeenCalledTimes(2));
  await ready();
  fireEvent.click(screen.getByRole('radio', { name: /office/ }));
  fireEvent.click(screen.getByRole('button', { name: 'Checkout' }));
  await waitFor(() => expect(api.checkout).toHaveBeenCalledTimes(3));
  expect(api.checkout).toHaveBeenLastCalledWith(expect.any(String), 'quote-original', 'address', 'address-token');
});
