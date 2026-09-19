import { formatMoney } from './format';
import type { PriceBreakdown } from './types';

export function PriceBreakdownView({ value }: { value: PriceBreakdown }) {
  return <dl className="price-breakdown" aria-label="결제 금액 구성">
    <div><dt>상품 금액</dt><dd>{formatMoney(value.productAmount)}</dd></div>
    <div><dt>배송비</dt><dd>{value.shippingFee.amount === 0 ? '무료' : formatMoney(value.shippingFee)}</dd></div>
    <div className="price-breakdown-total"><dt>최종 결제 금액</dt><dd>{formatMoney(value.totalAmount)}</dd></div>
  </dl>;
}
