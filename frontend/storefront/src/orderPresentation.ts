import type { OrderCancellationState, OrderCustomerState } from './types';

const orderStatuses: Record<string, string> = {
  PAID: '결제 완료', FULFILLING: '배송 준비·진행 중', DELIVERED: '배송 완료', CANCELLED: '주문 취소'
};
const timelineLabels: Record<string, string> = {
  ORDER_CREATED: '주문이 접수되었습니다', ORDER_PAID: '결제가 완료되었습니다',
  SHIPMENT_CREATED: '배송 준비가 시작되었습니다',
  SHIPMENT_REGISTERED: '택배 접수가 완료되어 운송장이 발급되었습니다',
  SHIPMENT_PICKED_UP: '택배사가 상품을 집하했습니다',
  SHIPMENT_IN_TRANSIT: '배송 중입니다',
  SHIPMENT_DELIVERY_FAILED: '배송하지 못해 반송을 시작했습니다',
  SHIPMENT_RETURNED: '반송이 완료되었습니다',
  ORDER_DELIVERED: '배송이 완료되었습니다',
  CHECKOUT_FAILED: '구매가 실패했습니다',
  ORDER_CANCELLATION_REQUESTED: '주문 취소를 요청했습니다',
  ORDER_CANCELLED: '주문 취소가 완료되었습니다'
};

export function orderStatusText(
  state: OrderCustomerState,
  status: string | null,
  cancellation: OrderCancellationState = 'NONE'
): string {
  if (cancellation === 'PROCESSING') return '주문 취소 처리 중';
  if (cancellation === 'CHECKING') return '주문 취소 확인 중';
  if (cancellation === 'COMPLETED') return '주문 취소 완료';
  if (state === 'PROCESSING') return '주문 처리 중';
  if (state === 'CHECKING') return '확인 중';
  if (state === 'FAILED') return '구매 실패';
  if (state !== 'SUCCEEDED') return '상태 확인 중';
  return orderStatuses[status ?? ''] ?? '주문 완료';
}

export function timelineText(type: string): string {
  return timelineLabels[type] ?? '주문 상태가 변경되었습니다';
}

export function orderDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '시각 확인 중' : new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium', timeStyle: 'short'
  }).format(date);
}
