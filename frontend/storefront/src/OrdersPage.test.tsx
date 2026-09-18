// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { ApiError, UnauthorizedError, api } from './api';
import { CommerceRoutes } from './CommerceRoutes';
import { orderStatusText, timelineText } from './orderPresentation';
import type { Member, OrderDetail, OrderSummary } from './types';

const member: Member = { id: 'mem_owner', email: 'owner@example.test', name: 'Owner', status: 'ACTIVE', addressBookVersion: 0, addresses: [] };
const summary: OrderSummary = { id: 'ord_a', orderedAt: '2026-09-16T03:00:00Z', representativeProductName: 'Snapshot product',
  representativeSkuName: 'Ivory / M', additionalProductCount: 1, totalQuantity: 3, total: { amount: 25000, currency: 'KRW' },
  checkoutResult: 'SUCCEEDED', orderStatus: 'FULFILLING' };
const detail: OrderDetail = { id: 'ord_a', orderedAt: summary.orderedAt, checkoutResult: 'SUCCEEDED', orderStatus: 'FULFILLING',
  lines: [{ skuId: 'sku_a', productId: 'prd_a', productName: 'Snapshot product', skuName: 'Ivory / M', quantity: 2,
    unitPrice: { amount: 10000, currency: 'KRW' }, lineTotal: { amount: 20000, currency: 'KRW' } }], total: summary.total,
  shippingAddress: { recipient: 'Snapshot Recipient', phone: '010-1234-5678', line1: 'Snapshot road', city: 'Seoul', postalCode: '12345' },
  trackingNumber: 'TRK-visible', timeline: [{ type: 'ORDER_CREATED', occurredAt: summary.orderedAt },
    { type: 'ORDER_PAID', occurredAt: '2026-09-16T03:05:00Z' }] };

beforeEach(() => {
  vi.spyOn(api, 'me').mockResolvedValue(member);
  vi.spyOn(api, 'orders').mockResolvedValue({ items: [summary], nextCursor: null });
  vi.spyOn(api, 'order').mockResolvedValue(detail);
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });

function Back() {
  const navigate = useNavigate();
  return <button type="button" onClick={() => navigate(-1)}>브라우저 뒤로</button>;
}
function open(path = '/orders') {
  return render(<MemoryRouter initialEntries={[path]}><Back /><CommerceRoutes /></MemoryRouter>);
}

// [PD-0020-R5, PD-0020-R6] 요약 정보와 다음 커서를 사용한다. DB 경계는 서버 테스트가 검증한다.
test('shows summaries and appends the next cursor page', async () => {
  vi.mocked(api.orders).mockResolvedValueOnce({ items: [summary], nextCursor: 'opaque-cursor' })
    .mockResolvedValueOnce({ items: [{ ...summary, id: 'ord_b', representativeProductName: 'Older product' }], nextCursor: null });
  open();
  expect(await screen.findByText('Snapshot product 외 1개 상품')).toBeInTheDocument();
  expect(screen.getByText('Ivory / M · 총 3개')).toBeInTheDocument();
  expect(screen.getByText('배송 준비·진행 중')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '더 보기' }));
  expect(await screen.findByText('Older product 외 1개 상품')).toBeInTheDocument();
  expect(api.orders).toHaveBeenNthCalledWith(2, 'opaque-cursor');
  expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument();
});

// [PD-0020-R7, PD-0020-R8] 직접 진입해도 전체 구매 내용과 사실 시각이 표시된다.
test('loads detail from a direct URL and supports refresh', async () => {
  open('/orders/ord_a');
  expect(await screen.findByText('Snapshot Recipient')).toBeInTheDocument();
  expect(screen.getByText('TRK-visible')).toBeInTheDocument();
  expect(screen.getByText('주문이 접수되었습니다')).toBeInTheDocument();
  expect(screen.getByText('결제가 완료되었습니다')).toBeInTheDocument();
  expect(document.querySelector('time[datetime="2026-09-16T03:05:00Z"]')).not.toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '새로고침' }));
  await waitFor(() => expect(api.order).toHaveBeenCalledTimes(2));
  expect(await screen.findByText('Snapshot Recipient')).toBeInTheDocument();
});

test('supports list-to-detail navigation and browser back', async () => {
  open();
  fireEvent.click(await screen.findByRole('link', { name: '주문 상세 →' }));
  expect(await screen.findByText('Snapshot Recipient')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '브라우저 뒤로' }));
  expect(await screen.findByText('Snapshot product 외 1개 상품')).toBeInTheDocument();
});

// [PD-0020-R9] 인증 전에는 ID가 URL에 있어도 주문을 요청하지 않으며 로그인 후 같은 URL을 이어간다.
test('guards a deep link and continues after login', async () => {
  vi.mocked(api.me).mockRejectedValueOnce(new UnauthorizedError());
  vi.spyOn(api, 'login').mockResolvedValue({ accessToken: 'access', accessTokenExpiresAt: '2026-09-16T04:00:00Z' });
  open('/orders/ord_a');
  expect(await screen.findByText('로그인 후 주문을 확인해주세요')).toBeInTheDocument();
  expect(api.order).not.toHaveBeenCalled();
  expect(api.orders).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '로그인' }));
  expect(await screen.findByText('Snapshot Recipient')).toBeInTheDocument();
  expect(api.order).toHaveBeenCalledWith('ord_a');
});

// [PD-0020-R9] 없는 주문과 타 회원 주문 모두 같은 안내를 사용한다.
test('renders the same not-found view for a rejected detail', async () => {
  vi.mocked(api.order).mockRejectedValue(new ApiError(404, 'order not found'));
  open('/orders/ord_other');
  expect(await screen.findByText('주문을 찾을 수 없습니다')).toBeInTheDocument();
  expect(screen.queryByText('Snapshot Recipient')).not.toBeInTheDocument();
});

test('shows an empty page without inventing demo orders', async () => {
  vi.mocked(api.orders).mockResolvedValue({ items: [], nextCursor: null });
  open();
  expect(await screen.findByText('아직 주문 내역이 없습니다.')).toBeInTheDocument();
  expect(screen.queryByText('Snapshot product 외 1개 상품')).not.toBeInTheDocument();
});

test('retries failures instead of replacing private data with demo orders', async () => {
  vi.mocked(api.orders).mockRejectedValueOnce(new ApiError(503, 'unavailable'));
  open();
  expect(await screen.findByRole('alert')).toHaveTextContent('주문 정보를 불러오지 못했습니다');
  expect(screen.queryByText('Snapshot product 외 1개 상품')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
  expect(await screen.findByText('Snapshot product 외 1개 상품')).toBeInTheDocument();
});

test('preserves existing items and cursor when loading more fails', async () => {
  vi.mocked(api.orders).mockResolvedValueOnce({ items: [summary], nextCursor: 'same-cursor' })
    .mockRejectedValueOnce(new ApiError(503, 'unavailable'))
    .mockResolvedValueOnce({ items: [], nextCursor: null });
  open();
  fireEvent.click(await screen.findByRole('button', { name: '더 보기' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('다음 주문을 불러오지 못했습니다');
  expect(screen.getByText('Snapshot product 외 1개 상품')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '더 보기' }));
  await waitFor(() => expect(api.orders).toHaveBeenNthCalledWith(3, 'same-cursor'));
});

// [PD-0020-R2, PD-0020-R3, PD-0020-R4, PD-0020-R8] 내부 상태 이름을 원문으로 화면에 흘리지 않는다.
test('uses customer labels rather than raw internal statuses', () => {
  expect(orderStatusText('PROCESSING', 'CANCELLED')).toBe('주문 처리 중');
  expect(orderStatusText('CHECKING', 'CANCELLED')).toBe('확인 중');
  expect(orderStatusText('FAILED', null)).toBe('구매 실패');
  expect(orderStatusText('SUCCEEDED', 'UNEXPECTED_INTERNAL_STATE')).toBe('주문 완료');
  expect(timelineText('INTERNAL_RETRY')).toBe('주문 상태가 변경되었습니다');
});

test('refreshes processing detail automatically until it completes', async () => {
  vi.useFakeTimers();
  vi.mocked(api.order).mockResolvedValueOnce({ ...detail, checkoutResult: 'PROCESSING', orderStatus: null })
    .mockResolvedValueOnce(detail);
  open('/orders/ord_a');
  await act(async () => { await Promise.resolve(); });
  expect(screen.getByText('주문 처리 중')).toBeInTheDocument();
  await act(async () => { await vi.advanceTimersByTimeAsync(10_000); });
  expect(screen.getByText('배송 준비·진행 중')).toBeInTheDocument();
  await act(async () => { await vi.advanceTimersByTimeAsync(20_000); });
  expect(api.order).toHaveBeenCalledTimes(2);
});

test('clears private detail when its session expires', async () => {
  vi.mocked(api.order).mockResolvedValueOnce(detail).mockRejectedValueOnce(new UnauthorizedError());
  open('/orders/ord_a');
  expect(await screen.findByText('Snapshot Recipient')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '새로고침' }));
  expect(await screen.findByText('로그인 후 주문을 확인해주세요')).toBeInTheDocument();
  expect(screen.queryByText('Snapshot Recipient')).not.toBeInTheDocument();
});

test('keeps attention-required and failed orders visible with customer guidance', async () => {
  vi.mocked(api.order).mockResolvedValueOnce({ ...detail, checkoutResult: 'CHECKING', orderStatus: null })
    .mockResolvedValueOnce({ ...detail, checkoutResult: 'FAILED', orderStatus: null });
  open('/orders/ord_a');
  expect(await screen.findByText('확인 중')).toBeInTheDocument();
  expect(screen.getByText('주문을 확인하고 있습니다. 확인이 끝나면 결과가 갱신됩니다.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '새로고침' }));
  expect(await screen.findByText('구매 실패')).toBeInTheDocument();
  expect(screen.getByText('Snapshot Recipient')).toBeInTheDocument();
});
