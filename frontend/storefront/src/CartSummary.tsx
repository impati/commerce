import { PackageOpen } from 'lucide-react';
import type { Cart, CartLine } from './types';
import { formatMoney } from './format';

type CartSummaryProps = {
  cart: Cart;
  unavailable: boolean;
  refreshing: boolean;
  commandsDisabled: boolean;
  quantityDrafts: Record<string, string>;
  onQuantityInput: (skuId: string, value: string) => void;
  onQuantityChange: (skuId: string, quantity: number) => void;
  onRemove: (skuId: string) => void;
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

export function CartSummary({
  cart, unavailable, refreshing, commandsDisabled, quantityDrafts,
  onQuantityInput, onQuantityChange, onRemove, onRetry
}: CartSummaryProps) {
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
              <CartLineControls
                item={item}
                draft={quantityDrafts[item.skuId] ?? String(item.quantity)}
                disabled={commandsDisabled}
                onInput={onQuantityInput}
                onChange={onQuantityChange}
                onRemove={onRemove}
              />
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

type CartLineControlsProps = {
  item: CartLine;
  draft: string;
  disabled: boolean;
  onInput: (skuId: string, value: string) => void;
  onChange: (skuId: string, quantity: number) => void;
  onRemove: (skuId: string) => void;
};

function CartLineControls({ item, draft, disabled, onInput, onChange, onRemove }: CartLineControlsProps) {
  const label = item.productName ?? item.skuId;
  const quantity = Number(draft);
  const validQuantity = /^[0-9]+$/.test(draft) && Number.isInteger(quantity)
    && quantity >= 1 && quantity <= 2147483647;

  return (
    <div className="cart-line-controls">
      <b>{item.quantity}개</b>
      <div className="quantity-editor">
        <button
          type="button"
          aria-label={`${label} 수량 감소`}
          disabled={disabled || !validQuantity || quantity <= 1}
          onClick={() => {
            onChange(item.skuId, quantity - 1);
          }}
        >
          −
        </button>
        <input
          type="text"
          inputMode="numeric"
          aria-label={`${label} 수량`}
          value={draft}
          disabled={disabled}
          aria-invalid={!validQuantity}
          onChange={event => {
            onInput(item.skuId, event.target.value);
          }}
          onKeyDown={event => {
            if (event.key === 'Enter' && validQuantity && !disabled) {
              event.preventDefault();
              onChange(item.skuId, quantity);
            }
          }}
        />
        <button
          type="button"
          aria-label={`${label} 수량 증가`}
          disabled={disabled || !validQuantity || quantity >= 2147483647}
          onClick={() => {
            onChange(item.skuId, quantity + 1);
          }}
        >
          +
        </button>
      </div>
      {!validQuantity && <span role="alert">수량은 1 이상의 정수로 입력해주세요.</span>}
      <div className="cart-line-actions">
        <button
          type="button"
          disabled={disabled || !validQuantity || draft === String(item.quantity)}
          aria-label={`${label} 수량 적용`}
          onClick={() => {
            onChange(item.skuId, quantity);
          }}
        >
          적용
        </button>
        <button
          type="button"
          disabled={disabled}
          aria-label={`${label} 제거`}
          onClick={() => {
            onRemove(item.skuId);
          }}
        >
          제거
        </button>
      </div>
    </div>
  );
}
