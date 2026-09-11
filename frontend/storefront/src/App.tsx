import {
  Check,
  CreditCard,
  Loader2,
  Minus,
  PackageCheck,
  PackageOpen,
  Plus,
  RefreshCcw,
  Search,
  ShoppingBag,
  Truck,
  Wifi,
  WifiOff
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { UnauthorizedError, api, fallback } from './api';
import { session } from './session';
import { compactStatus, formatMoney } from './format';
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
    authenticated ? api.cart() : Promise.resolve(fallback.cart),
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
    cart: pick(cartResult, 'cart', fallback.cart),
    notifications: pick(notificationsResult, 'notifications', fallback.notifications)
  };

  const total = authenticated ? 5 : 3;
  const mode: ApiMode = degraded.length === 0 ? 'live' : degraded.length >= total ? 'demo' : 'partial';
  return { data, degraded, mode };
}

function noticeFor(mode: ApiMode, degraded: string[]): string {
  if (mode === 'live') {
    return 'Gateway connected';
  }
  if (mode === 'demo') {
    return 'Demo mode';
  }
  return `Partial — demo: ${degraded.join(', ')}`;
}

export function App() {
  const [home, setHome] = useState<DisplayHome>(fallback.home);
  const [products, setProducts] = useState<Product[]>(fallback.products);
  const [cart, setCart] = useState<Cart>(fallback.cart);
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

  function applyStorefront(result: Awaited<ReturnType<typeof fetchStorefront>>) {
    setHome(result.data.home);
    setProducts(result.data.products);
    setCart(result.data.cart);
    setStock(result.data.stock);
    setNotifications(result.data.notifications);
    setApiMode(result.mode);
    setNotice(noticeFor(result.mode, result.degraded));
  }

  useEffect(() => {
    let ignore = false;

    async function bootstrap() {
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

      // 세션을 버리는 것은 서버가 세션이 유효하지 않다고 답했을 때뿐이다. 게이트웨이가 닿지
      // 않거나 오류를 내는 것은 세션에 대한 답이 아니므로 토큰을 남긴다 — 서버에는 아직 살아
      // 있고, 여기서 지우면 장애가 끝나도 사용자가 다시 로그인해야 한다.
      let current: Member | null = null;
      if (session.read()) {
        try {
          current = await api.me();
        } catch (error) {
          if (error instanceof UnauthorizedError) {
            session.clear();
          } else {
            setAuthNotice('로그인 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.');
          }
        }
      }
      if (ignore) return;
      setMember(current);

      const result = await fetchStorefront(current !== null);
      if (ignore) return;
      applyStorefront(result);
      if (current) {
        const pending = session.readPendingCheckout(current.id);
        if (pending?.orderId) {
          try {
            const order = await api.order(pending.orderId);
            setCheckout({ order, payment: null, shipment: null });
            if (order.checkoutStatus === 'PROCESSING') {
              window.setTimeout(() => pollCheckout(order.id, 0), 1500);
            } else {
              session.clearPendingCheckout(current.id);
              setNotice(order.checkoutStatus === 'FAILED'
                ? `Checkout failed (${order.failureCode ?? 'CHECKOUT_FAILED'}).`
                : 'Checkout completed');
            }
          } catch {
            setNotice('A pending checkout will be checked again on retry.');
          }
        }
      }
      setBusy(null);
    }

    bootstrap();
    return () => {
      ignore = true;
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

  const cartItems = useMemo(() => {
    return cart.lines.map((line) => {
      const product = products.find((candidate) => candidate.skus.some((sku) => sku.id === line.skuId));
      const sku = product?.skus.find((candidate) => candidate.id === line.skuId);
      return {
        ...line,
        product,
        sku,
        lineTotal: (sku?.price.amount ?? 0) * line.quantity
      };
    });
  }, [cart.lines, products]);

  const cartTotal = cartItems.reduce((sum, item) => sum + item.lineTotal, 0);

  async function refresh() {
    setBusy('load');
    try {
      applyStorefront(await fetchStorefront(member !== null));
    } finally {
      setBusy(null);
    }
  }

  async function submitAuth() {
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
      session.write(issued.sessionToken, issued.accessToken);
      const current = await api.me();
      setMember(current);
      applyStorefront(await fetchStorefront(true));
      setAuthNotice('');
    } catch (error) {
      setAuthNotice(error instanceof Error ? error.message : '요청이 실패했습니다.');
    } finally {
      setAuthBusy(false);
    }
  }

  async function signOut() {
    try {
      await api.logout();
    } catch {
      // 서버가 이미 폐기했거나 닿지 않아도 로컬 세션은 지운다
    }
    if (member) session.clearPendingCheckout(member.id);
    session.clear();
    setMember(null);
    setCheckout(null);
    setShipment(null);
    applyStorefront(await fetchStorefront(false));
  }

  /** 세션이 끊겼다. 로그인 화면으로 돌려보낸다. */
  function handleExpiredSession() {
    if (member) session.clearPendingCheckout(member.id);
    session.clear();
    setMember(null);
    setAuthNotice('세션이 만료됐습니다. 다시 로그인해주세요.');
  }

  async function addToCart(product: Product) {
    const skuId = selectedSku[product.id] ?? product.skus[0]?.id;
    if (!skuId) return;
    if (!member) {
      setAuthNotice('장바구니를 쓰려면 로그인해주세요.');
      return;
    }
    setBusy('cart');
    try {
      setCart(await api.addCartItem(skuId, 1));
      setNotice('Cart updated');
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        handleExpiredSession();
        return;
      }
      setApiMode('demo');
      setCart((current) => addLine(current, skuId));
      setNotice('Cart updated');
    } finally {
      setBusy(null);
    }
  }

  async function runCheckout() {
    if (cart.lines.length === 0) {
      setNotice('Cart is empty');
      return;
    }
    if (!member) {
      setAuthNotice('주문하려면 로그인해주세요.');
      return;
    }
    setBusy('checkout');
    const pending = session.readPendingCheckout(member.id) ?? { idempotencyKey: crypto.randomUUID() };
    session.writePendingCheckout(member.id, pending);
    try {
      let result: Checkout;
      try {
        result = await api.checkout(pending.idempotencyKey);
      } catch (first) {
        if (first instanceof UnauthorizedError) throw first;
        result = await api.checkout(pending.idempotencyKey);
      }
      session.writePendingCheckout(member.id, {
        idempotencyKey: pending.idempotencyKey,
        orderId: result.order.id
      });
      setCheckout(result);
      setShipment(result.shipment);
      try {
        setCart(await api.cart());
      } catch {
        // 주문 접수 결과는 이미 받았다. 장바구니 조회 실패가 그 결과를 뒤집지는 않는다.
      }
      if (result.order.checkoutStatus === 'PROCESSING') {
        setNotice('Checkout is processing');
        window.setTimeout(() => pollCheckout(result.order.id, 0), 1500);
      } else {
        finishCheckout(result.order);
      }
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        handleExpiredSession();
        return;
      }
      setNotice('Checkout response was not received. Retry uses the same request key.');
    } finally {
      setBusy(null);
    }
  }

  async function pollCheckout(orderId: string, attempt: number) {
    if (!member || attempt >= 30) {
      setNotice('Checkout is still processing. Reload to check the order again.');
      return;
    }
    try {
      const order = await api.order(orderId);
      setCheckout((current) => current ? { ...current, order } : { order, payment: null, shipment: null });
      if (order.checkoutStatus === 'PROCESSING') {
        window.setTimeout(() => pollCheckout(orderId, attempt + 1), 1500);
        return;
      }
      finishCheckout(order);
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        handleExpiredSession();
        return;
      }
      window.setTimeout(() => pollCheckout(orderId, attempt + 1), 1500);
    }
  }

  function finishCheckout(order: Checkout['order']) {
    if (!member) return;
    session.clearPendingCheckout(member.id);
    if (order.checkoutStatus === 'FAILED') {
      const cleanup = order.paymentCleanupStatus && order.paymentCleanupStatus !== 'DONE'
        ? ' Payment cleanup is in progress.'
        : '';
      setNotice(`Checkout failed (${order.failureCode ?? 'CHECKOUT_FAILED'}).${cleanup}`);
      return;
    }
    setStock((current) => reduceStock(
      current,
      order.lines.map((line) => ({ skuId: line.skuId, quantity: line.quantity }))
    ));
    setNotifications((current) => [
      ...current,
      {
        id: `ntf_ui_${Date.now()}`,
        eventType: 'OrderPaid',
        memberId: member.id,
        subject: 'Order paid',
        body: `Order ${order.id} has been paid.`
      }
    ]);
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

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Impati Commerce</p>
          <h1>{home.title}</h1>
        </div>
        <div className="topbar-actions">
          <span className={`connection ${apiMode}`}>
            {apiMode !== 'demo' ? <Wifi size={16} /> : <WifiOff size={16} />}
            {notice}
          </span>
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

            <div className="cart-list">
              {cartItems.length === 0 ? (
                <div className="empty-state">
                  <PackageOpen size={22} />
                  <span>Cart empty</span>
                </div>
              ) : (
                cartItems.map((item) => (
                  <div className="cart-line" key={item.skuId}>
                    <div>
                      <strong>{item.product?.name ?? item.skuId}</strong>
                      <span>{item.sku?.name ?? item.skuId}</span>
                    </div>
                    <div className="quantity">
                      <Minus size={14} />
                      <b>{item.quantity}</b>
                      <Plus size={14} />
                    </div>
                  </div>
                ))
              )}
            </div>

            <div className="total-row">
              <span>Total</span>
              <strong>{formatMoney({ amount: cartTotal, currency: 'KRW' })}</strong>
            </div>

            <button
              className="checkout-button"
              type="button"
              onClick={runCheckout}
              disabled={busy === 'checkout' || cart.lines.length === 0}
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
                <h2>{checkout ? compactStatus(checkout.order.status) : 'waiting'}</h2>
              </div>
              <PackageCheck size={22} />
            </div>

            <div className="timeline">
              <Step active={Boolean(checkout)} done={Boolean(checkout)} label="Paid" />
              <Step active={shipment?.status === 'IN_TRANSIT'} done={shipment?.status === 'IN_TRANSIT' || shipment?.status === 'DELIVERED'} label="Shipped" />
              <Step active={shipment?.status === 'DELIVERED'} done={shipment?.status === 'DELIVERED'} label="Delivered" />
            </div>

            {checkout && (
              <div className="order-meta">
                <span>{checkout.order.id}</span>
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

function addLine(cart: Cart, skuId: string): Cart {
  const existing = cart.lines.find((line) => line.skuId === skuId);
  if (existing) {
    return {
      ...cart,
      lines: cart.lines.map((line) =>
        line.skuId === skuId ? { ...line, quantity: line.quantity + 1 } : line
      )
    };
  }
  return { ...cart, lines: [...cart.lines, { skuId, quantity: 1 }] };
}

function reduceStock(stock: Stock[], lines: Cart['lines']): Stock[] {
  return stock.map((item) => {
    const line = lines.find((candidate) => candidate.skuId === item.skuId);
    if (!line) return item;
    return {
      ...item,
      onHand: Math.max(0, item.onHand - line.quantity),
      available: Math.max(0, item.available - line.quantity)
    };
  });
}
