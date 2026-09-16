import { PackageOpen } from 'lucide-react';
import type { Cart } from './types';
import { formatMoney } from './format';

export function CartSummary({ cart, unavailable, refreshing, onRetry }: {
  cart: Cart; unavailable: boolean; refreshing: boolean; onRetry: () => void;
}) {
  return <>
    {unavailable && <p role="alert">장바구니를 확인할 수 없습니다. 다시 시도해주세요.</p>}
    <div className="cart-list">
      {!unavailable && cart.lines.length === 0 ? <div className="empty-state">
        <PackageOpen size={22} /><span>Cart empty</span>
      </div> : cart.lines.map((item) => <div className="cart-line" key={item.skuId}>
        <div>
          <strong>{item.productName ?? item.skuId}</strong>
          <span>{item.informationAvailable ? item.skuName : '상품 정보 확인 불가'}</span>
          <span>{item.availableQuantity == null ? '재고 확인 불가'
            : item.availableQuantity < item.quantity ? `재고 부족 · 구매 가능 ${item.availableQuantity}개` : `구매 가능 ${item.availableQuantity}개`}</span>
        </div>
        <div className="quantity"><b>{item.quantity}개</b></div>
      </div>)}
    </div>
    <div className="total-row">
      <span>상품 합계</span>
      <strong>{!unavailable && cart.lines.length === 0 ? '상품을 담아주세요'
        : !unavailable && cart.quote ? formatMoney(cart.quote.total) : '금액 확인 불가'}</strong>
    </div>
    <button className="link-button" type="button" disabled={refreshing} onClick={onRetry}>
      {refreshing ? '확인 중…' : '장바구니 다시 확인'}
    </button>
  </>;
}
