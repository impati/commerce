import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError, UnauthorizedError, api } from './api';
import { session } from './session';
import { formatMoney } from './format';
import { orderDate, orderStatusText, timelineText } from './orderPresentation';
import { PriceBreakdownView } from './PriceBreakdownView';
import type { Member, OrderCustomerState, OrderDetail, OrderReturn, OrderSummary, ReturnPickupAddress, ReturnReason } from './types';

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
  const [cancellationNotice, setCancellationNotice] = useState('');
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
    setCancellationNotice('');
  }, [member, orderId]);

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
    setCancellationNotice('');
    try {
      await api.cancelOrder(detail.id);
      setRevision(value => value + 1);
    } catch (problem) {
      if (problem instanceof UnauthorizedError) expired();
      else if (problem instanceof ApiError && problem.code === 'cancellation_not_allowed') {
        setCancellationNotice('이미 배송이 시작되어 주문을 취소할 수 없습니다.');
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
          {cancellationNotice && <div className="order-error" role="alert"><p>{cancellationNotice}</p></div>}
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
      <dt>배송 상태</dt><dd>{shipmentStatusText(detail.shipmentStatus)}</dd>
      <dt>택배사</dt><dd>{detail.carrierName ?? '아직 접수되지 않았습니다'}</dd>
      <dt>운송장</dt><dd>{detail.trackingNumber ?? '아직 발급되지 않았습니다'}</dd>
    </dl></section>
    {(detail.orderStatus === 'DELIVERED' || detail.orderStatus === 'RETURNED'
      || detail.shipmentStatus === 'RETURNED') && <ReturnPanel detail={detail} />}
    <section className="panel"><h2>진행 이력</h2>{detail.timeline.length === 0 ? <p>아직 기록된 진행 이력이 없습니다.</p> :
      <ol className="order-event-list">{detail.timeline.map((event, index) => <li key={`${event.type}-${index}`}>
        <strong>{timelineText(event.type)}</strong><time dateTime={event.occurredAt}>{orderDate(event.occurredAt)}</time>
      </li>)}</ol>}</section>
  </div>;
}

const returnReasonText: Record<string, string> = {
  CHANGE_OF_MIND: '단순 변심', DEFECT_DAMAGE: '상품 하자·파손', WRONG_ITEM: '오배송·계약 내용 불일치',
  FAILED_DELIVERY: '배송 실패 후 판매자 반송'
};

const returnStatusText: Record<OrderReturn['status'], string> = {
  REQUESTED: '회수 접수 중', PICKUP_SCHEDULED: '회수 예정', PICKUP_FAILED: '회수 실패',
  RESCHEDULE_PENDING: '회수 재접수 중', WITHDRAWAL_PENDING: '철회 처리 중', IN_TRANSIT: '반품 운송 중',
  RECEIVED: '판매자 입고·검수 중', COMPLETED: '반품·환불 완료', WITHDRAWN: '반품 철회',
  ATTENTION_REQUIRED: '담당자 확인 중'
};

function ReturnPanel({ detail }: { detail: OrderDetail }) {
  const [current, setCurrent] = useState<OrderReturn | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [reason, setReason] = useState<ReturnReason>('CHANGE_OF_MIND');
  const [description, setDescription] = useState('');
  const [awareDate, setAwareDate] = useState('');
  const [pickup, setPickup] = useState<ReturnPickupAddress>({ ...detail.shippingAddress });

  useEffect(() => {
    let stopped = false;
    async function load() {
      try {
        const result = await api.orderReturn(detail.id);
        if (!stopped) { setCurrent(result); setPickup(result.pickupAddress); setError(''); }
      } catch (problem) {
        if (!stopped && problem instanceof ApiError && problem.status === 404) setCurrent(null);
        else if (!stopped) setError('반품 상태를 불러오지 못했습니다.');
      } finally { if (!stopped) setLoading(false); }
    }
    load();
    return () => { stopped = true; };
  }, [detail.id]);

  useEffect(() => {
    if (!current || ['COMPLETED', 'WITHDRAWN'].includes(current.status)) return;
    let stopped = false;
    let timer: number;
    async function update() {
      try {
        const result = await api.orderReturn(detail.id);
        if (!stopped) { setCurrent(result); setPickup(result.pickupAddress); setError(''); }
        if (!stopped && !['COMPLETED', 'WITHDRAWN'].includes(result.status)) {
          timer = window.setTimeout(update, 10_000);
        }
      } catch {
        if (!stopped) {
          setError('최신 반품 상태를 확인하지 못했습니다.');
          timer = window.setTimeout(update, 10_000);
        }
      }
    }
    timer = window.setTimeout(update, 10_000);
    return () => { stopped = true; window.clearTimeout(timer); };
  }, [detail.id, current?.status]);

  function changePickup(field: keyof ReturnPickupAddress, value: string) {
    setPickup(previous => ({ ...previous, [field]: value }));
  }

  async function requestReturn(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError('');
    try {
      setCurrent(await api.requestReturn(detail.id, reason, description,
        reason === 'CHANGE_OF_MIND' ? null : awareDate, pickup));
    } catch (problem) {
      if (problem instanceof ApiError && problem.status === 409) setError('반품 신청 기간 또는 현재 주문 상태를 확인해주세요.');
      else setError('반품을 접수하지 못했습니다. 다시 시도해주세요.');
    } finally { setBusy(false); }
  }

  async function withdraw() {
    if (busy || !window.confirm('회수 전 반품 신청을 철회할까요?')) return;
    setBusy(true); setError('');
    try { setCurrent(await api.withdrawReturn(detail.id)); }
    catch { setError('반품 철회를 접수하지 못했습니다.'); }
    finally { setBusy(false); }
  }

  async function reschedule(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError('');
    try { setCurrent(await api.rescheduleReturn(detail.id, pickup)); }
    catch { setError('회수 재접수를 완료하지 못했습니다.'); }
    finally { setBusy(false); }
  }

  const canRequest = !current || current.status === 'WITHDRAWN';
  const canWithdraw = current && ['REQUESTED', 'PICKUP_SCHEDULED', 'PICKUP_FAILED'].includes(current.status);
  if (loading) return <section className="panel"><h2>반품</h2><p role="status">반품 가능 상태를 확인하고 있습니다.</p></section>;

  return <section className="panel return-panel"><h2>반품</h2>
    {current && <div className="return-summary">
      <p><strong>{returnStatusText[current.status]}</strong></p>
      <dl className="order-address">
        <dt>사유</dt><dd>{returnReasonText[current.reason] ?? '반품'}</dd>
        <dt>환불 예정액</dt><dd>{formatMoney(current.refundAmount)}</dd>
        <dt>회수 주소</dt><dd>{current.pickupAddress.line1}, {current.pickupAddress.city} ({current.pickupAddress.postalCode})</dd>
      </dl>
      {current.reason === 'CHANGE_OF_MIND' && detail.priceBreakdown.shippingFee.amount > 0 &&
        <p className="order-guidance">유료 배송 주문은 최초 배송비 3,000원을 제외한 금액이 환불됩니다.</p>}
      {canWithdraw && <button type="button" className="secondary-button" onClick={withdraw} disabled={busy}>반품 신청 철회</button>}
    </div>}
    {current?.status === 'PICKUP_FAILED' && <form className="auth-form return-form" onSubmit={reschedule}>
      <h3>회수 다시 신청</h3><PickupFields value={pickup} onChange={changePickup} />
      <button className="secondary-button" disabled={busy}>{busy ? '접수 중…' : '이 주소로 회수 재접수'}</button>
    </form>}
    {canRequest && detail.orderStatus === 'DELIVERED' && <form className="auth-form return-form" onSubmit={requestReturn}>
      {current?.status === 'WITHDRAWN' && <p className="order-guidance">철회한 신청과 별개로 신청 기간 안에는 다시 접수할 수 있습니다.</p>}
      <label>반품 사유<select value={reason} onChange={event => setReason(event.target.value as ReturnReason)}>
        <option value="CHANGE_OF_MIND">단순 변심</option><option value="DEFECT_DAMAGE">상품 하자·파손</option>
        <option value="WRONG_ITEM">오배송·계약 내용 불일치</option>
      </select></label>
      {reason !== 'CHANGE_OF_MIND' && <label>하자·오배송을 안 날짜<input type="date" required value={awareDate} onChange={event => setAwareDate(event.target.value)} /></label>}
      <label>상세 설명 (선택)<textarea maxLength={500} value={description} onChange={event => setDescription(event.target.value)} /></label>
      <fieldset><legend>회수 주소</legend><p>기존 배송지가 기본값이며, 회수 전 다른 주소로 바꿀 수 있습니다.</p>
        <PickupFields value={pickup} onChange={changePickup} />
      </fieldset>
      {reason === 'CHANGE_OF_MIND' && detail.priceBreakdown.shippingFee.amount > 0 &&
        <p className="order-guidance">유료 배송 주문은 최초 배송비 3,000원을 제외한 금액이 환불됩니다.</p>}
      {reason === 'CHANGE_OF_MIND' && detail.priceBreakdown.shippingFee.amount === 0 &&
        <p className="order-guidance">무료 배송 주문은 배송비나 회수비를 차감하지 않고 전액 환불됩니다.</p>}
      <p className="order-guidance">단순 변심은 배송일로부터 7일 이내 신청할 수 있습니다. 상품 하자·오배송은 배송 후 3개월 이내이면서 안 날부터 30일 이내 신청할 수 있습니다.</p>
      <button className="secondary-button" disabled={busy}>{busy ? '접수 중…' : '전체 주문 반품 신청'}</button>
    </form>}
    {error && <p role="alert" className="order-error">{error}</p>}
  </section>;
}

function PickupFields({ value, onChange }: {
  value: ReturnPickupAddress; onChange: (field: keyof ReturnPickupAddress, value: string) => void;
}) {
  return <div className="return-address-fields">
    <label>수령인<input required value={value.recipient} onChange={event => onChange('recipient', event.target.value)} /></label>
    <label>연락처<input required value={value.phone} onChange={event => onChange('phone', event.target.value)} /></label>
    <label>주소<input required value={value.line1} onChange={event => onChange('line1', event.target.value)} /></label>
    <label>도시<input required value={value.city} onChange={event => onChange('city', event.target.value)} /></label>
    <label>우편번호<input required value={value.postalCode} onChange={event => onChange('postalCode', event.target.value)} /></label>
  </div>;
}

function shipmentStatusText(status: string | null): string {
  return ({ READY: '배송 준비 중', AWAITING_PICKUP: '집하 대기', IN_TRANSIT: '배송 중',
    DELIVERED: '배송 완료', RETURNING: '반송 중', RETURNED: '반송 완료', CANCELLED: '배송 취소' } as Record<string, string>)[status ?? '']
    ?? '상태 확인 중';
}
