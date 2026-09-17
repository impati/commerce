import { PackageOpen } from 'lucide-react';
import type { Cart, CartLine } from './types';
import { formatMoney } from './format';

type CartSummaryProps = {
  cart: Cart;
  unavailable: boolean;
  refreshing: boolean;
  onRetry: () => void;
};

function stockLabel(line: CartLine): string {
  if (line.availableQuantity == null) {
    return '재고 확인 불가';
  }
  if (line.availableQuantity < line.quantity) {
    return `재고 부족 · 구매 가능 ${line.availableQuantity}개`;
  }
  return `구매 가능 ${line.availableQuantity}개`;
}

function totalLabel(cart: Cart, unavailable: boolean): string {
  if (unavailable) {
    return '금액 확인 불가';
  }
  if (cart.lines.length === 0) {
    return '상품을 담아주세요';
  }
  if (!cart.quote) {
    return '금액 확인 불가';
  }
  return formatMoney(cart.quote.total);
}

export function CartSummary({ cart, unavailable, refreshing, onRetry }: CartSummaryProps) {
  const showEmptyCart = !unavailable && cart.lines.length === 0;

  return (
    <>
      {unavailable && (
        <p role="alert">장바구니를 확인할 수 없습니다. 다시 시도해주세요.</p>
      )}
      <div className="cart-list">
        {showEmptyCart ? (
          <div className="empty-state">
            <PackageOpen size={22} />
            <span>Cart empty</span>
          </div>
        ) : (
          cart.lines.map((item) => (
            <div className="cart-line" key={item.skuId}>
              <div>
                <strong>{item.productName ?? item.skuId}</strong>
                <span>{item.informationAvailable ? item.skuName : '상품 정보 확인 불가'}</span>
                <span>{stockLabel(item)}</span>
              </div>
              <div className="quantity">
                <b>{item.quantity}개</b>
              </div>
            </div>
          ))
        )}
      </div>
      <div className="total-row">
        <span>상품 합계</span>
        <strong>{totalLabel(cart, unavailable)}</strong>
      </div>
      <button className="link-button" type="button" disabled={refreshing} onClick={onRetry}>
        {refreshing ? '확인 중…' : '장바구니 다시 확인'}
      </button>
    </>
  );
}
