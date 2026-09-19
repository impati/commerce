import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError, UnauthorizedError, api } from './api';
import { session } from './session';
import { formatMoney } from './format';
import { orderDate, orderStatusText, timelineText } from './orderPresentation';
import { PriceBreakdownView } from './PriceBreakdownView';
import type { Member, OrderCustomerState, OrderDetail, OrderSummary } from './types';

export function OrdersPage() {
  const { orderId } = useParams();
  const [member, setMember] = useState<Member | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [authError, setAuthError] = useState('');
  const [authRetry, setAuthRetry] = useState(0);
  const [email, setEmail] = useState('demo@impati.test');
  const [password, setPassword] = useState('demo-password');
  const [loginBusy, setLoginBusy] = useState(false);
  const [items, setItems] = useState<OrderSummary[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [detail, setDetail] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');
  const [notFound, setNotFound] = useState(false);
  const [cancellationBusy, setCancellationBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const generation = useRef(0);

  function expired(message = '세션이 만료됐습니다. 다시 로그인해주세요.') {
    generation.current++;
    session.clear();
    setMember(null);
    setItems([]);
    setDetail(null);
    setNextCursor(null);
    setAuthError(message);
  }

  useEffect(() => {
    let ignore = false;
    setAuthLoading(true);
    setAuthError('');
    api.me().then(current => { if (!ignore) setMember(current); }).catch(problem => {
      if (ignore) return;
      if (problem instanceof UnauthorizedError) session.clear();
      else setAuthError('로그인 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.');
    }).finally(() => { if (!ignore) setAuthLoading(false); });
    return () => { ignore = true; };
  }, [authRetry]);

  useEffect(() => {
    const current = ++generation.current;
    setItems([]);
    setDetail(null);
    setNextCursor(null);
    setError('');
    setNotFound(false);
    setLoadingMore(false);
    if (!member) return;
    setLoading(true);
    const request = orderId ? api.order(orderId) : api.orders();
    request.then(result => {
      if (current !== generation.current) return;
      if ('items' in result) {
        setItems(result.items);
        setNextCursor(result.nextCursor);
      } else setDetail(result);
    }).catch(problem => {
      if (current !== generation.current) return;
      if (problem instanceof UnauthorizedError) expired();
      else if (problem instanceof ApiError && problem.status === 404) setNotFound(true);
      else setError('주문 정보를 불러오지 못했습니다. 다시 시도해주세요.');
    }).finally(() => { if (current === generation.current) setLoading(false); });
    return () => { generation.current++; };
  }, [member, orderId, revision]);

  // 상세의 진행 상태는 자동 갱신한다. 페이지를 탐색 중인 목록은 고객의 새로고침으로 갱신한다.
  useEffect(() => {
    if (!member || !orderId || !detail || (
      !['PROCESSING', 'CHECKING'].includes(detail.checkoutResult)
      && !['PROCESSING', 'CHECKING'].includes(detail.cancellationStatus)
    )) return;
    const current = generation.current;
    let cancelled = false;
    let timer: number;
    async function update() {
      try {
        const result = await api.order(orderId!);
        if (!cancelled && current === generation.current) { setDetail(result); setError(''); }
      } catch (problem) {
        if (cancelled || current !== generation.current) return;
        if (problem instanceof UnauthorizedError) expired();
        else setError('최신 상태를 확인하지 못했습니다. 다시 시도해주세요.');
      } finally {
        if (!cancelled && current === generation.current) timer = window.setTimeout(update, 10_000);
      }
    }
    timer = window.setTimeout(update, 10_000);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [member, orderId, detail?.checkoutResult, detail?.cancellationStatus]);

  async function cancelOrder() {
    if (!detail || cancellationBusy || !window.confirm(
      '상품 금액과 배송비를 포함한 최종 결제 금액 전액이 환불됩니다. 주문을 취소할까요?'
    )) return;
    setCancellationBusy(true);
    setError('');
    try {
      await api.cancelOrder(detail.id);
      setRevision(value => value + 1);
    } catch (problem) {
      if (problem instanceof UnauthorizedError) expired();
      else if (problem instanceof ApiError && problem.code === 'cancellation_not_allowed') {
        setError('이미 배송이 시작되어 주문을 취소할 수 없습니다.');
        setRevision(value => value + 1);
      } else setError('주문 취소를 접수하지 못했습니다. 다시 시도해주세요.');
    } finally { setCancellationBusy(false); }
  }

  async function login(event: FormEvent) {
    event.preventDefault();
    setLoginBusy(true);
    setAuthError('');
    try {
      const issued = await api.login(email, password);
      session.writeAccess(issued.accessToken);
      setMember(await api.me());
    } catch {
      setAuthError('로그인하지 못했습니다. 계정 정보와 연결 상태를 확인해주세요.');
    } finally { setLoginBusy(false); }
  }

  async function more() {
    if (!nextCursor || loadingMore || !member) return;
    const current = generation.current;
    setLoadingMore(true);
    setError('');
    try {
      const page = await api.orders(nextCursor);
      if (current !== generation.current) return;
      setItems(previous => [...previous, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch (problem) {
      if (current !== generation.current) return;
      if (problem instanceof UnauthorizedError) expired();
      else setError('다음 주문을 불러오지 못했습니다. 더 보기를 다시 눌러주세요.');
    } finally { if (current === generation.current) setLoadingMore(false); }
  }

  async function logout() {
    try { await api.logout(); } catch { /* 응답을 받지 못해도 화면의 개인정보는 비운다. */ }
    finally { expired('로그아웃되었습니다.'); }
  }

  return <div className="app-shell">
    <header className="topbar">
      <div><p className="eyebrow">Impati Commerce</p><h1>주문 내역</h1></div>
      <nav className="topbar-actions" aria-label="주문 메뉴">
        <Link className="order-link" to="/">쇼핑 계속하기</Link>
        {member && <button className="link-button" type="button" onClick={logout}>로그아웃</button>}
      </nav>
    </header>
    <main className="orders-layout">
      {authLoading ? <p role="status">로그인 정보를 확인하고 있습니다.</p> : !member ?
        <section className="panel order-login"><h2>로그인 후 주문을 확인해주세요</h2>
          <p>로그인하면 현재 주소의 주문 내역으로 이어집니다.</p>
          <form className="auth-form" onSubmit={login}>
            <label>Email<input type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} required /></label>
            <label>Password<input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required /></label>
            {authError && <p role="alert">{authError}</p>}
            <button className="checkout-button" disabled={loginBusy}>{loginBusy ? '로그인 중…' : '로그인'}</button>
            <button type="button" className="link-button" onClick={() => setAuthRetry(n => n + 1)}>기존 세션 다시 확인</button>
            <Link to="/">가입은 쇼핑 화면에서 진행해주세요</Link>
          </form>
        </section> : <>
          <div className="section-head">
            <div><p className="eyebrow">{member.name}님의 주문</p><h2>{orderId ? '주문 상세' : '전체 주문'}</h2></div>
            <button type="button" className="secondary-button" disabled={loading || loadingMore} onClick={() => setRevision(n => n + 1)}>새로고침</button>
          </div>
          {orderId && <Link className="order-link" to="/orders">← 주문 목록</Link>}
          {loading ? <p role="status">주문 정보를 불러오고 있습니다.</p> : notFound ?
            <section className="panel"><h2>주문을 찾을 수 없습니다</h2><p>주문 목록에서 확인해주세요.</p></section> :
            orderId ? detail?.id === orderId && <DetailView detail={detail} onCancel={cancelOrder} cancellationBusy={cancellationBusy} /> : <>
              {items.length === 0 && !error && <section className="panel empty-state"><p>아직 주문 내역이 없습니다.</p><Link to="/">상품 둘러보기</Link></section>}
              <div className="order-cards">{items.map(item => <SummaryCard item={item} key={item.id} />)}</div>
              {nextCursor && <button type="button" className="secondary-button order-more" onClick={more} disabled={loadingMore}>{loadingMore ? '불러오는 중…' : '더 보기'}</button>}
            </>}
          {error && <div className="order-error" role="alert"><p>{error}</p>
            {!loadingMore && (!nextCursor || orderId || items.length === 0) && <button type="button" className="secondary-button" onClick={() => setRevision(n => n + 1)}>다시 시도</button>}
          </div>}
        </>}
    </main>
  </div>;
}

function Status({ state, status, cancellation }: {
  state: OrderCustomerState; status: string | null; cancellation: OrderSummary['cancellationStatus'];
}) {
  return <span className={`order-status ${state.toLowerCase()}`}>{orderStatusText(state, status, cancellation)}</span>;
}

function SummaryCard({ item }: { item: OrderSummary }) {
  return <article className="panel order-card">
    <div className="order-card-head"><time dateTime={item.orderedAt}>{orderDate(item.orderedAt)}</time><Status state={item.checkoutResult} status={item.orderStatus} cancellation={item.cancellationStatus} /></div>
    <p className="order-id">{item.id}</p>
    <h3>{item.representativeProductName}{item.additionalProductCount > 0 && ` 외 ${item.additionalProductCount}개 상품`}</h3>
    <p>{item.representativeSkuName} · 총 {item.totalQuantity}개</p>
    <PriceBreakdownView value={item.priceBreakdown} />
    <div className="order-card-head"><Link className="order-link" to={`/orders/${encodeURIComponent(item.id)}`}>주문 상세 →</Link></div>
  </article>;
}

function DetailView({ detail, onCancel, cancellationBusy }: {
  detail: OrderDetail; onCancel: () => void; cancellationBusy: boolean;
}) {
  const address = detail.shippingAddress;
  return <div className="order-detail">
    <section className="panel"><div className="order-card-head"><h2>주문 정보</h2><Status state={detail.checkoutResult} status={detail.orderStatus} cancellation={detail.cancellationStatus} /></div>
      <p className="order-id">{detail.id}</p><time dateTime={detail.orderedAt}>{orderDate(detail.orderedAt)}</time>
      {detail.checkoutResult === 'PROCESSING' && <p className="order-guidance" role="status">주문 처리가 진행 중입니다. 결과는 자동으로 갱신됩니다.</p>}
      {detail.checkoutResult === 'CHECKING' && <p className="order-guidance" role="status">주문을 확인하고 있습니다. 확인이 끝나면 결과가 갱신됩니다.</p>}
      {detail.checkoutResult === 'FAILED' && <p className="order-guidance">구매가 완료되지 않았습니다. 주문한 상품과 처리 이력을 아래에서 확인할 수 있습니다.</p>}
      {detail.cancellationStatus === 'PROCESSING' && <p className="order-guidance" role="status">주문 취소를 처리하고 있습니다. 결과는 자동으로 갱신됩니다.</p>}
      {detail.cancellationStatus === 'CHECKING' && <p className="order-guidance" role="status">주문 취소 상태를 확인하고 있습니다. 확인이 끝나면 결과가 갱신됩니다.</p>}
      {detail.cancellationStatus === 'COMPLETED' && <p className="order-guidance">주문 취소와 전액 환불이 완료되었습니다.</p>}
      {detail.cancellable && <button className="secondary-button" type="button" onClick={onCancel} disabled={cancellationBusy}>
        {cancellationBusy ? '취소 접수 중…' : '주문 취소'}
      </button>}
    </section>
    <section className="panel"><h2>주문 상품</h2><ul className="order-lines">{detail.lines.map((line, index) => <li key={`${line.skuId}-${index}`}>
      <div><strong>{line.productName}</strong><p>{line.skuName}</p><span>{formatMoney(line.unitPrice)} × {line.quantity}개</span></div>
      <strong>{formatMoney(line.lineTotal)}</strong>
    </li>)}</ul><PriceBreakdownView value={detail.priceBreakdown} /></section>
    <section className="panel"><h2>배송 정보</h2><dl className="order-address">
      <dt>수령인</dt><dd>{address.recipient}</dd><dt>연락처</dt><dd>{address.phone}</dd>
      <dt>주소</dt><dd>{address.line1}, {address.city} ({address.postalCode})</dd>
      <dt>운송장</dt><dd>{detail.trackingNumber ?? '아직 발급되지 않았습니다'}</dd>
    </dl></section>
    <section className="panel"><h2>진행 이력</h2>{detail.timeline.length === 0 ? <p>아직 기록된 진행 이력이 없습니다.</p> :
      <ol className="order-event-list">{detail.timeline.map((event, index) => <li key={`${event.type}-${index}`}>
        <strong>{timelineText(event.type)}</strong><time dateTime={event.occurredAt}>{orderDate(event.occurredAt)}</time>
      </li>)}</ol>}</section>
  </div>;
}
