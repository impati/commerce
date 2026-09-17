import {
  Check,
  CreditCard,
  Loader2,
  PackageCheck,
  RefreshCcw,
  Search,
  ShoppingBag,
  Truck,
  Wifi,
  WifiOff
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError, UnauthorizedError, api, fallback } from './api';
import { CartRequestTracker } from './CartRequestTracker';
import { CartSummary } from './CartSummary';
import { session } from './session';
import type { PendingCheckout } from './session';
import { formatMoney } from './format';
import { orderStatusText } from './orderPresentation';
import { productImages } from './mockData';
import type { Cart, Checkout, DisplayHome, Member, Notification, Product, Shipment, Stock } from './types';

type ApiMode = 'live' | 'partial' | 'demo';
type BusyAction = 'load' | 'cart' | 'checkout' | 'ship' | 'deliver' | null;

const categories = ['all', 'apparel', 'home', 'travel'];

/**
 * 첫 화면에 필요한 5개를 각각 독립적으로 가져온다.
 *
 * Promise.all이었을 때는 알림 하나가 죽어도 상품까지 데모 데이터로 바뀌었다.
 * 살아있는 것은 실제 데이터를 쓰고, 죽은 것만 폴백으로 대체한다.
 */
async function fetchStorefront(authenticated: boolean) {
  const [homeResult, productsResult, stockResult, cartResult, notificationsResult] = await Promise.allSettled([
    api.home(),
    api.products(),
    api.stock(),
    authenticated ? api.cart() : Promise.resolve<Cart>({ memberId: '', lines: [], version: 0 }),
    authenticated ? api.notifications() : Promise.resolve([])
  ]);

  const degraded: string[] = [];
  function pick<T>(result: PromiseSettledResult<T>, name: string, fallbackValue: T): T {
    if (result.status === 'fulfilled') {
      return result.value;
    }
    degraded.push(name);
    return fallbackValue;
  }

  const data = {
    home: pick(homeResult, 'display', fallback.home),
    products: pick(productsResult, 'products', fallback.products),
    stock: pick(stockResult, 'inventory', fallback.stock),
    cart: cartResult.status === 'fulfilled' ? cartResult.value : null,
    notifications: pick(notificationsResult, 'notifications', fallback.notifications)
  };

  let fallbackMode: ApiMode = 'live';
  if (degraded.length > 0) {
    // 회원의 구매 데이터는 데모로 대체하지 않으므로 전체 데모 상태로 표시하지 않는다.
    fallbackMode = !authenticated && degraded.length >= 3 ? 'demo' : 'partial';
  }
  const cartFailed = authenticated && cartResult.status === 'rejected';
  const mode: ApiMode = cartFailed && fallbackMode === 'live' ? 'partial' : fallbackMode;
  return { data, degraded, mode, fallbackMode, cartFailed };
}

function noticeFor(mode: ApiMode, degraded: string[], cartFailed = false): string {
  if (cartFailed) {
    if (degraded.length > 0) {
      return `장바구니 조회 실패 · demo: ${degraded.join(', ')}`;
    }
    return '장바구니 조회 실패';
  }
  if (mode === 'live') {
    return 'Gateway connected';
  }
  if (mode === 'demo') {
    return 'Demo mode';
  }
  return `Partial — demo: ${degraded.join(', ')}`;
}

async function requestPendingCheckout(pending: PendingCheckout): Promise<Checkout> {
  if (pending.orderId) {
    return api.checkoutResult(pending.orderId);
  }
  if (!pending.quoteId) {
    throw new Error('새 견적을 확인하고 다시 결제해주세요.');
  }
  return api.checkout(pending.idempotencyKey, pending.quoteId);
}

async function requestCheckoutWithRecovery(pending: PendingCheckout): Promise<Checkout> {
  try {
    return await requestPendingCheckout(pending);
  } catch (error) {
    if (error instanceof UnauthorizedError || (error instanceof ApiError && error.status < 500)) {
      throw error;
    }
    // 결과 미확인은 새 주문을 만들지 않고 원래 키와 견적으로 한 번 더 확인한다.
    return requestPendingCheckout(pending);
  }
}

export function App() {
  const cartRequests = useRef(new CartRequestTracker());
  const [degradedResources, setDegradedResources] = useState<string[]>([]);
  const [fallbackMode, setFallbackMode] = useState<ApiMode>('live');
  const [cartUnavailable, setCartUnavailable] = useState(false);
  const [home, setHome] = useState<DisplayHome>(fallback.home);
  const [products, setProducts] = useState<Product[]>(fallback.products);
  const [cart, setCart] = useState<Cart>({ memberId: '', lines: [], version: 0 });
  const [stock, setStock] = useState<Stock[]>(fallback.stock);
  const [notifications, setNotifications] = useState<Notification[]>(fallback.notifications);
  const [checkout, setCheckout] = useState<Checkout | null>(null);
  const [shipment, setShipment] = useState<Shipment | null>(null);
  const [selectedSku, setSelectedSku] = useState<Record<string, string>>({});
  const [category, setCategory] = useState('all');
  const [query, setQuery] = useState('');
  const [apiMode, setApiMode] = useState<ApiMode>('live');
  const [busy, setBusy] = useState<BusyAction>('load');
  const [notice, setNotice] = useState('Ready');
  const [member, setMember] = useState<Member | null>(null);
  const [authView, setAuthView] = useState<'login' | 'register'>('login');
  const [authEmail, setAuthEmail] = useState('demo@impati.test');
  const [authName, setAuthName] = useState('');
  const [authPassword, setAuthPassword] = useState('demo-password');
  const [authNotice, setAuthNotice] = useState('');
  const [authBusy, setAuthBusy] = useState(false);

  function changeMember(current: Member | null) {
    cartRequests.current.changeMember(current?.id ?? null);
    setMember(current);
    setBusy(null);
    setCart({ memberId: current?.id ?? '', lines: [], version: 0 });
    setCartUnavailable(false);
  }

  async function loadStorefront(request = cartRequests.current.begin()) {
    const result = await fetchStorefront(request.memberId !== null);
    if (!cartRequests.current.isCurrent(request)) {
      return false;
    }
    applyStorefront(result);
    return true;
  }

  function applyStorefront(result: Awaited<ReturnType<typeof fetchStorefront>>) {
    setHome(result.data.home);
    setProducts(result.data.products);
    if (result.data.cart) {
      setCart(result.data.cart);
    }
    setCartUnavailable(result.cartFailed);
    setStock(result.data.stock);
    setNotifications(result.data.notifications);
    setApiMode(result.mode);
    setFallbackMode(result.fallbackMode);
    setDegradedResources(result.degraded);
    setNotice(noticeFor(result.mode, result.degraded, result.cartFailed));
  }

  useEffect(() => {
    let ignore = false;

    async function bootstrap() {
      const bootRequest = cartRequests.current.begin();
      setBusy('load');

      // 인증 링크로 들어온 경우 먼저 처리한다. 토큰은 한 번만 쓸 수 있으므로 URL에서 지운다.
      // 토큰은 프래그먼트에 있다. 프래그먼트는 서버로 전송되지 않아 접근 로그에 남지 않는다 (ADR-0006).
      const verificationToken = new URLSearchParams(window.location.hash.slice(1)).get('token');
      if (verificationToken) {
        try {
          await api.verifyEmail(verificationToken);
          setAuthNotice('이메일이 확인됐습니다. 로그인해주세요.');
        } catch (error) {
          setAuthNotice(error instanceof Error ? error.message : '인증에 실패했습니다.');
        }
        window.history.replaceState({}, '', window.location.pathname);
      }

      // 세션이 유효하지 않다는 응답만 로그아웃으로 해석한다. 게이트웨이 장애는 세션에 대한 답이
      // 아니며 HttpOnly 쿠키는 브라우저가 계속 보관하므로, 장애가 끝나면 다시 복원할 수 있다.
      if (ignore || !cartRequests.current.isCurrent(bootRequest)) {
        return;
      }
      let current: Member | null = null;
      try {
        current = await api.me();
      } catch (error) {
        if (ignore || !cartRequests.current.isCurrent(bootRequest)) {
          return;
        }
        if (error instanceof UnauthorizedError) {
          session.clear();
        } else {
          setAuthNotice('로그인 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.');
        }
      }
      if (ignore || !cartRequests.current.isCurrent(bootRequest)) {
        return;
      }
      changeMember(current);
      setBusy('load');
      const request = cartRequests.current.begin();
      await loadStorefront(request);
      if (ignore || !cartRequests.current.sameSession(request)) {
        return;
      }
      if (cartRequests.current.isCurrent(request)) {
        setBusy(null);
      }
      if (current) {
        await resumePendingCheckout(current.id);
      }
    }

    bootstrap();
    return () => {
      ignore = true;
      cartRequests.current.changeMember(null);
    };
  }, []);

  const filteredProducts = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return products.filter((product) => {
      const categoryMatches = category === 'all' || product.category === category;
      const queryMatches =
        !normalized ||
        product.name.toLowerCase().includes(normalized) ||
        product.brand.toLowerCase().includes(normalized) ||
        product.tags.join(' ').toLowerCase().includes(normalized);
      return categoryMatches && queryMatches;
    });
  }, [category, products, query]);

  const checkoutState = checkout?.order.checkoutStatus === 'SUCCEEDED' ? 'SUCCEEDED'
    : checkout?.order.checkoutStatus === 'FAILED' && checkout.order.paymentCleanupStatus === 'DONE' ? 'FAILED' : 'PROCESSING';

  async function refresh() {
    const request = cartRequests.current.begin();
    setBusy('load');
    try {
      await loadStorefront(request);
    } finally {
      if (cartRequests.current.isCurrent(request)) {
        setBusy(null);
      }
    }
  }

  async function loadCart(request = cartRequests.current.begin()): Promise<boolean | null> {
    try {
      const result = await api.cart();
      if (!cartRequests.current.isCurrent(request)) {
        return null;
      }
      setCart(result);
      setCartUnavailable(false);
      setApiMode(fallbackMode);
      setNotice(current => current.startsWith('장바구니 조회 실패') ? '' : current);
      return true;
    } catch (error) {
      if (!cartRequests.current.isCurrent(request)) {
        return null;
      }
      setCartUnavailable(true);
      setNotice('');
      if (error instanceof UnauthorizedError) {
        handleExpiredSession();
      }
      return false;
    }
  }

  async function retryCart() {
    const request = cartRequests.current.begin();
    setBusy('cart');
    try {
      await loadCart(request);
    } finally {
      if (cartRequests.current.isCurrent(request)) {
        setBusy(null);
      }
    }
  }

  async function submitAuth() {
    const authRequest = cartRequests.current.begin();
    setAuthBusy(true);
    setAuthNotice('');
    try {
      if (authView === 'register') {
        await api.register(authEmail, authName || authEmail, authPassword);
        setAuthNotice('가입됐습니다. 발송된 인증 링크로 이메일을 확인해주세요.');
        setAuthView('login');
        return;
      }
      const issued = await api.login(authEmail, authPassword);
      if (!cartRequests.current.sameSession(authRequest)) {
        return;
      }
      session.writeAccess(issued.accessToken);
      const current = await api.me();
      if (!cartRequests.current.sameSession(authRequest)) {
        return;
      }
      changeMember(current);
      const request = cartRequests.current.begin();
      await loadStorefront(request);
      if (!cartRequests.current.sameSession(request)) {
        return;
      }
      await resumePendingCheckout(current.id);
      setAuthNotice('');
    } catch (error) {
      setAuthNotice(error instanceof Error ? error.message : '요청이 실패했습니다.');
    } finally {
      setAuthBusy(false);
    }
  }

  async function signOut() {
    changeMember(null);
    session.clear();
    setCheckout(null);
    setShipment(null);
    const request = cartRequests.current.begin();
    try {
      await api.logout();
    } catch {
      // 서버가 이미 폐기했거나 닿지 않아도 로컬 세션은 지운다.
    }
    if (cartRequests.current.isCurrent(request)) {
      await loadStorefront(request);
    }
  }

  /** 세션이 끊겼다. 로그인 화면으로 돌려보낸다. */
  function handleExpiredSession() {
    session.clear();
    changeMember(null);
    setCartUnavailable(true);
    setAuthNotice('세션이 만료됐습니다. 다시 로그인해주세요.');
  }

  async function addToCart(product: Product) {
    const skuId = selectedSku[product.id] ?? product.skus[0]?.id;
    if (!skuId) {
      return;
    }
    if (!member) {
      setAuthNotice('장바구니를 쓰려면 로그인해주세요.');
      return;
    }
    const mutation = cartRequests.current.begin();
    setBusy('cart');
    try {
      const result = await api.addCartItem(skuId, 1);
      if (!cartRequests.current.sameSession(mutation)) {
        return;
      }
      cartRequests.current.invalidateQueries();
      setCart(result);
      setCartUnavailable(false);
      const loaded = await loadCart();
      if (loaded !== null) {
        setNotice(loaded ? '상품을 담았습니다.' : '상품을 담았습니다. 장바구니와 금액을 다시 확인해주세요.');
      }
    } catch (error) {
      if (!cartRequests.current.sameSession(mutation)) {
        return;
      }
      if (error instanceof UnauthorizedError) {
        handleExpiredSession();
        return;
      }
      setCartUnavailable(true);
      if (error instanceof ApiError && error.status < 500 && error.code !== 'outcome_unknown') {
        setNotice(error.message);
      } else {
        setNotice('상품 담기 결과를 확인하지 못했습니다. 장바구니를 다시 확인해주세요.');
      }
      await loadCart();
    } finally {
      if (cartRequests.current.sameSession(mutation)) {
        setBusy(null);
      }
    }
  }

  async function runCheckout() {
    if (!member) {
      setAuthNotice('주문하려면 로그인해주세요.');
      return;
    }
    const existing = session.readPendingCheckout(member.id);
    let pending = existing;
    if (!pending) {
      const quote = cart.quote;
      if (cartUnavailable || !cart.checkoutAllowed || !quote) {
        setNotice('장바구니와 견적·재고를 다시 확인해주세요.');
        return;
      }
      pending = { idempotencyKey: crypto.randomUUID(), quoteId: quote.id };
    }
    if (!pending.quoteId && !pending.orderId) {
      session.clearPendingCheckout(member.id);
      setNotice('새 견적을 확인하고 다시 결제해주세요.');
      await loadCart();
      return;
    }
    if (cart.lines.length === 0 && !existing) {
      setNotice('Cart is empty');
      return;
    }
    const request = cartRequests.current.begin();
    setBusy('checkout');
    session.writePendingCheckout(member.id, pending);
    try {
      const result = await requestCheckoutWithRecovery(pending);
      if (!cartRequests.current.sameSession(request)) {
        return;
      }
      // loadCart는 실패를 화면 상태에 기록한다. 주문 접수 결과는 계속 처리한다.
      await loadCart();
      if (cartRequests.current.sameSession(request)) {
        await acceptCheckoutResult(result, member.id, pending.idempotencyKey);
      }
    } catch (error) {
      if (!cartRequests.current.sameSession(request)) {
        return;
      }
      if (error instanceof UnauthorizedError) {
        handleExpiredSession();
        return;
      }
      if (error instanceof ApiError && error.status < 500) {
        session.clearPendingCheckout(member.id);
        await loadCart();
        setNotice(error.message);
      } else {
        setNotice('주문 접수 결과를 확인하지 못했습니다. 같은 요청으로 결과를 다시 확인합니다.');
      }
    } finally {
      if (cartRequests.current.sameSession(request)) {
        setBusy(null);
      }
    }
  }

  async function resumePendingCheckout(memberId: string) {
    const pending = session.readPendingCheckout(memberId);
    if (!pending) {
      return;
    }
    if (!pending.orderId && !pending.quoteId) {
      session.clearPendingCheckout(memberId);
      setNotice('새 견적을 확인하고 다시 결제해주세요.');
      return;
    }
    const request = cartRequests.current.begin();
    try {
      const result = await requestPendingCheckout(pending);
      if (cartRequests.current.sameSession(request)) {
        await acceptCheckoutResult(result, memberId, pending.idempotencyKey);
      }
    } catch (error) {
      if (!cartRequests.current.sameSession(request)) {
        return;
      }
      if (error instanceof UnauthorizedError) {
        handleExpiredSession();
        return;
      }
      if (error instanceof ApiError && error.status < 500) {
        session.clearPendingCheckout(memberId);
        await loadCart();
        setNotice(error.message);
      } else {
        setNotice('주문 접수 결과를 다시 확인해주세요.');
      }
    }
  }

  async function acceptCheckoutResult(result: Checkout, memberId: string, idempotencyKey: string) {
    session.writePendingCheckout(memberId, { idempotencyKey, orderId: result.order.id });
    setCheckout(result);
    setShipment(result.shipment);
    if (result.order.checkoutStatus === 'PROCESSING') {
      setNotice('Checkout is processing');
      window.setTimeout(() => pollCheckout(result.order.id, memberId, 0), 1500);
      return;
    }
    if (result.order.checkoutStatus === 'SUCCEEDED' && !result.shipment) {
      setNotice('Checkout completed. Loading shipment details.');
      window.setTimeout(() => pollCheckout(result.order.id, memberId, 0), 1500);
      return;
    }
    await finishCheckout(result, memberId);
  }

  async function pollCheckout(orderId: string, memberId: string, attempt: number) {
    if (attempt >= 30) {
      setNotice('Checkout is still processing. Reload to check the order again.');
      return;
    }
    try {
      const result = await api.checkoutResult(orderId);
      setCheckout(result);
      setShipment(result.shipment);
      if (result.order.checkoutStatus === 'PROCESSING') {
        window.setTimeout(() => pollCheckout(orderId, memberId, attempt + 1), 1500);
        return;
      }
      await finishCheckout(result, memberId);
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        handleExpiredSession();
        return;
      }
      window.setTimeout(() => pollCheckout(orderId, memberId, attempt + 1), 1500);
    }
  }

  async function finishCheckout(result: Checkout, memberId: string) {
    const order = result.order;
    session.clearPendingCheckout(memberId);
    if (order.checkoutStatus === 'FAILED') {
      const cleanup = order.paymentCleanupStatus && order.paymentCleanupStatus !== 'DONE'
        ? ' Payment cleanup is in progress.'
        : '';
      setNotice(`Checkout failed (${order.failureCode ?? 'CHECKOUT_FAILED'}).${cleanup}`);
      return;
    }
    const [stockResult, notificationsResult] = await Promise.allSettled([api.stock(), api.notifications()]);
    if (stockResult.status === 'fulfilled') setStock(stockResult.value);
    if (notificationsResult.status === 'fulfilled') setNotifications(notificationsResult.value);
    setNotice('Checkout completed');
  }

  async function shipOrder() {
    if (!shipment) return;
    setBusy('ship');
    try {
      const shipped = apiMode !== 'demo' ? await api.ship(shipment.id) : { ...shipment, status: 'IN_TRANSIT' };
      setShipment(shipped);
      setCheckout((current) =>
        current ? { ...current, shipment: shipped, order: { ...current.order, status: 'FULFILLING' } } : current
      );
      setNotice('Shipment in transit');
    } catch {
      const shipped = { ...shipment, status: 'IN_TRANSIT' };
      setShipment(shipped);
      setCheckout((current) => (current ? { ...current, shipment: shipped } : current));
      setApiMode('demo');
      setNotice('Shipment in transit');
    } finally {
      setBusy(null);
    }
  }

  async function deliverOrder() {
    if (!shipment || !checkout) return;
    setBusy('deliver');
    try {
      const delivered =
        apiMode !== 'demo'
          ? await api.deliver(shipment.id)
          : { shipment: { ...shipment, status: 'DELIVERED' }, order: { ...checkout.order, status: 'DELIVERED' } };
      setShipment(delivered.shipment);
      setCheckout((current) =>
        current ? { ...current, shipment: delivered.shipment, order: delivered.order } : current
      );
      setNotifications((current) => [
        ...current,
        {
          id: `ntf_ui_${Date.now()}`,
          eventType: 'OrderDelivered',
          memberId: member?.id ?? 'unknown',
          subject: 'Order delivered',
          body: `Order ${delivered.order.id} has been delivered.`
        }
      ]);
      setNotice('Delivery completed');
    } catch {
      const deliveredShipment = { ...shipment, status: 'DELIVERED' };
      setShipment(deliveredShipment);
      setCheckout((current) =>
        current
          ? { ...current, shipment: deliveredShipment, order: { ...current.order, status: 'DELIVERED' } }
          : current
      );
      setApiMode('demo');
      setNotice('Delivery completed');
    } finally {
      setBusy(null);
    }
  }

  const connectionMode = cartUnavailable && apiMode === 'live' ? 'partial' : apiMode;
  const connectionNotice = noticeFor(apiMode, degradedResources, cartUnavailable && member !== null);

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Impati Commerce</p>
          <h1>{home.title}</h1>
        </div>
        <div className="topbar-actions">
          <Link className="order-link" to="/orders">주문 내역</Link>
          <span className={`connection ${connectionMode}`}>
            {connectionMode !== 'demo' ? <Wifi size={16} /> : <WifiOff size={16} />}
            {connectionNotice}
          </span>
          {notice && notice !== connectionNotice && <span role="status">{notice}</span>}
          <button className="icon-button" type="button" onClick={refresh} title="Refresh" disabled={busy === 'load'}>
            {busy === 'load' ? <Loader2 className="spin" size={18} /> : <RefreshCcw size={18} />}
          </button>
        </div>
      </header>

      <main className="commerce-layout">
        <section className="catalog-area">
          <div className="section-head">
            <div>
              <p className="eyebrow">Storefront</p>
              <h2>{home.subtitle}</h2>
            </div>
            <div className="search-box">
              <Search size={18} />
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search"
                aria-label="Search products"
              />
            </div>
          </div>

          <div className="segments" role="tablist" aria-label="Categories">
            {categories.map((item) => (
              <button
                className={category === item ? 'active' : ''}
                key={item}
                type="button"
                onClick={() => setCategory(item)}
              >
                {item}
              </button>
            ))}
          </div>

          <div className="display-strip">
            {home.sections.slice(0, 3).map((section) => (
              <div className="display-band" key={section.key}>
                <span>{section.title}</span>
                <strong>{section.products.length}</strong>
              </div>
            ))}
          </div>

          <div className="product-grid">
            {filteredProducts.map((product) => {
              const skuId = selectedSku[product.id] ?? product.skus[0]?.id;
              const sku = product.skus.find((candidate) => candidate.id === skuId) ?? product.skus[0];
              const skuStock = stock.find((item) => item.skuId === sku?.id);
              return (
                <article className="product-card" key={product.id}>
                  <img className="product-image" src={productImages[product.name]} alt={product.name} />
                  <div className="product-body">
                    <div className="product-title-row">
                      <div>
                        <p>{product.brand}</p>
                        <h3>{product.name}</h3>
                      </div>
                      <strong>{formatMoney(sku?.price)}</strong>
                    </div>
                    <p className="description">{product.description}</p>
                    <div className="tag-row">
                      {product.tags.map((tag) => (
                        <span key={tag}>{tag}</span>
                      ))}
                    </div>
                    <div className="sku-row">
                      <select
                        value={sku?.id}
                        onChange={(event) =>
                          setSelectedSku((current) => ({ ...current, [product.id]: event.target.value }))
                        }
                        aria-label={`${product.name} SKU`}
                      >
                        {product.skus.map((item) => (
                          <option value={item.id} key={item.id}>
                            {item.name}
                          </option>
                        ))}
                      </select>
                      <span className="stock-pill">{skuStock?.available ?? 0} left</span>
                    </div>
                    <button
                      className="primary-button"
                      type="button"
                      onClick={() => addToCart(product)}
                      disabled={busy === 'cart' || !sku}
                    >
                      <ShoppingBag size={18} />
                      Add
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        </section>

        <aside className="side-rail">
          {!member ? (
            <section className="panel">
              <div className="panel-head">
                <div>
                  <p className="eyebrow">{authView === 'login' ? 'Sign in' : 'Sign up'}</p>
                  <h2>{authView === 'login' ? '로그인' : '가입'}</h2>
                </div>
              </div>

              <div className="auth-form">
                <label>
                  <span>Email</span>
                  <input
                    value={authEmail}
                    onChange={(event) => setAuthEmail(event.target.value)}
                    autoComplete="username"
                  />
                </label>
                {authView === 'register' ? (
                  <label>
                    <span>Name</span>
                    <input value={authName} onChange={(event) => setAuthName(event.target.value)} />
                  </label>
                ) : null}
                <label>
                  <span>Password</span>
                  <input
                    type="password"
                    value={authPassword}
                    onChange={(event) => setAuthPassword(event.target.value)}
                    autoComplete="current-password"
                  />
                </label>

                {authNotice ? <p className="auth-notice">{authNotice}</p> : null}

                <button className="primary" type="button" onClick={submitAuth} disabled={authBusy}>
                  {authBusy ? <Loader2 className="spin" size={18} /> : null}
                  {authView === 'login' ? '로그인' : '가입하기'}
                </button>
                <button
                  className="link-button"
                  type="button"
                  onClick={() => {
                    setAuthView(authView === 'login' ? 'register' : 'login');
                    setAuthNotice('');
                  }}
                >
                  {authView === 'login' ? '계정이 없으신가요? 가입' : '이미 계정이 있으신가요? 로그인'}
                </button>
              </div>
            </section>
          ) : (
          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Member</p>
                <h2>{member.name}</h2>
                <p className="auth-notice">{member.email}</p>
              </div>
              <span className="count-badge">{cart.lines.reduce((sum, line) => sum + line.quantity, 0)}</span>
            </div>

            <CartSummary cart={cart} unavailable={cartUnavailable} refreshing={busy === 'cart'} onRetry={retryCart} />

            <button
              className="checkout-button"
              type="button"
              onClick={runCheckout}
              disabled={busy !== null || checkout?.order.checkoutStatus === 'PROCESSING'
                || ((!cart.checkoutAllowed || cartUnavailable || !cart.quote) && !(member && session.readPendingCheckout(member.id)))}
            >
              {busy === 'checkout' ? <Loader2 className="spin" size={18} /> : <CreditCard size={18} />}
              Checkout
            </button>

            <button className="link-button" type="button" onClick={signOut}>
              로그아웃
            </button>
          </section>
          )}

          <section className="panel">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Order</p>
                <h2>{checkout ? orderStatusText(checkoutState, checkout.order.status) : 'waiting'}</h2>
              </div>
              <PackageCheck size={22} />
            </div>

            <div className="timeline">
              <Step active={checkoutState === 'SUCCEEDED'} done={checkoutState === 'SUCCEEDED'} label="Paid" />
              <Step active={shipment?.status === 'IN_TRANSIT'} done={shipment?.status === 'IN_TRANSIT' || shipment?.status === 'DELIVERED'} label="Shipped" />
              <Step active={shipment?.status === 'DELIVERED'} done={shipment?.status === 'DELIVERED'} label="Delivered" />
            </div>

            {checkout && (
              <div className="order-meta">
                <Link className="order-link" to={`/orders/${encodeURIComponent(checkout.order.id)}`}>{checkout.order.id} · 상세 보기</Link>
                <strong>{formatMoney(checkout.order.total)}</strong>
              </div>
            )}

            <div className="action-row">
              <button
                className="secondary-button"
                type="button"
                onClick={shipOrder}
                disabled={!shipment || shipment.status !== 'READY' || busy === 'ship'}
              >
                {busy === 'ship' ? <Loader2 className="spin" size={17} /> : <Truck size={17} />}
                Ship
              </button>
              <button
                className="secondary-button"
                type="button"
                onClick={deliverOrder}
                disabled={!shipment || shipment.status === 'DELIVERED' || busy === 'deliver'}
              >
                {busy === 'deliver' ? <Loader2 className="spin" size={17} /> : <Check size={17} />}
                Deliver
              </button>
            </div>
          </section>

          <section className="panel compact-panel">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Inventory</p>
                <h2>{stock.reduce((sum, item) => sum + item.available, 0)} available</h2>
              </div>
            </div>
            <div className="stock-list">
              {stock.slice(0, 5).map((item) => (
                <div key={item.skuId}>
                  <span>{item.skuId.replace('sku_', '')}</span>
                  <strong>{item.available}</strong>
                </div>
              ))}
            </div>
          </section>

          <section className="panel compact-panel">
            <div className="panel-head">
              <div>
                <p className="eyebrow">Notifications</p>
                <h2>{notifications.length}</h2>
              </div>
            </div>
            <div className="notification-list">
              {notifications.slice(-3).map((notification) => (
                <div key={notification.id}>
                  <strong>{notification.subject}</strong>
                  <span>{notification.body}</span>
                </div>
              ))}
              {notifications.length === 0 && <span className="quiet">No events</span>}
            </div>
          </section>
        </aside>
      </main>
    </div>
  );
}

function Step({ active, done, label }: { active: boolean; done: boolean; label: string }) {
  return (
    <div className={`step ${active ? 'active' : ''} ${done ? 'done' : ''}`}>
      <span>{done ? <Check size={14} /> : null}</span>
      <b>{label}</b>
    </div>
  );
}
