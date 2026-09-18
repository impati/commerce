import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, UnauthorizedError } from './api';
import { session } from './session';
import type { AddressChoice, Member } from './types';

export function ShippingAddressSelector({ member, revision, disabled, onChange, onExpired }: {
  member: Member;
  revision: number;
  disabled: boolean;
  onChange: (address: AddressChoice | null) => void;
  onExpired: () => void;
}) {
  const [addresses, setAddresses] = useState(member.addresses);
  const selected = useRef<AddressChoice | null>(member.addresses.find(address => address.defaultAddress) ?? null);
  const [selectedId, setSelectedId] = useState(selected.current?.id ?? '');
  const [needsConfirmation, setNeedsConfirmation] = useState(false);
  const [loading, setLoading] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const [error, setError] = useState('');
  const generation = useRef(0);
  const callbacks = useRef({ onChange, onExpired });
  callbacks.current = { onChange, onExpired };

  useEffect(() => {
    callbacks.current.onChange(session.addressChangePending(member.id) ? null : selected.current);
    return () => {
      generation.current++;
    };
  }, [member.id]);

  async function refresh() {
    const request = ++generation.current;
    setLoading(true);
    setUnavailable(true);
    callbacks.current.onChange(null);
    try {
      const latest = await api.me();
      if (request !== generation.current) {
        return;
      }
      if (latest.id !== member.id) {
        callbacks.current.onExpired();
        return;
      }
      const current = latest.addresses.find(address => address.id === selected.current?.id);
      setAddresses(latest.addresses);
      session.finishAddressCheck(member.id);
      if (session.addressChangePending(member.id)) {
        setError('다른 요청의 배송지 저장 결과를 확인 중입니다. 완료 후 다시 확인해주세요.');
        return;
      }
      setUnavailable(false);
      if (current && current.confirmationToken === selected.current?.confirmationToken) {
        selected.current = current;
        callbacks.current.onChange(current);
        setNeedsConfirmation(false);
        setError('');
      } else {
        setNeedsConfirmation(true);
        setError(current ? '배송 정보가 변경됐습니다. 변경된 내용을 확인해주세요.' : '선택한 배송지가 없습니다. 배송지를 다시 선택해주세요.');
        // 최신 후보를 표시해도 고객이 확인하기 전에는 주문에 전달하지 않는다.
        if (!current) {
          setSelectedId('');
          selected.current = null;
        }
      }
    } catch (problem) {
      if (request !== generation.current) {
        return;
      }
      if (problem instanceof UnauthorizedError) {
        callbacks.current.onExpired();
      } else {
        setError('배송지 조회 결과를 확인하지 못했습니다. 다시 확인해주세요.');
      }
    } finally {
      if (request === generation.current) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    if (revision > 0 || session.addressChangePending(member.id)) {
      void refresh();
    }
    function pendingChange() {
      if (session.addressChangePending(member.id)) {
        callbacks.current.onChange(null);
        setError('배송지 변경 결과를 확인해주세요.');
      }
    }
    function storageChange(event: StorageEvent) {
      if (event.key?.startsWith('impati.address-change.' + member.id + '.')) {
        if (session.addressChangeInFlight(member.id)) {
          pendingChange();
        } else {
          void refresh();
        }
      }
    }
    const focus = () => { void refresh(); };
    window.addEventListener('focus', focus);
    window.addEventListener('storage', storageChange);
    window.addEventListener('address-change', pendingChange);
    return () => {
      generation.current++;
      window.removeEventListener('focus', focus);
      window.removeEventListener('storage', storageChange);
      window.removeEventListener('address-change', pendingChange);
    };
  }, [member.id, revision]);

  function choose(address: AddressChoice) {
    selected.current = address;
    setSelectedId(address.id);
    setNeedsConfirmation(false);
    setError('');
    callbacks.current.onChange(session.addressChangePending(member.id) ? null : address);
  }

  const candidate = addresses.find(address => address.id === selectedId);
  return <section className="shipping-selection" aria-label="주문 배송지">
    <div className="address-heading"><h3>배송지 선택</h3><Link to="/addresses">배송지 관리</Link></div>
    {addresses.length === 0 && <p>배송지를 등록한 뒤 주문할 수 있습니다.</p>}
    <fieldset disabled={disabled || loading || unavailable || session.addressChangePending(member.id)}>
      <legend className="sr-only">주문할 배송지</legend>
      {addresses.map(address => <label className="address-choice" key={address.id}>
        <input type="radio" name="shippingAddress" checked={selectedId === address.id}
          onChange={() => choose(address)} />
        <span><strong>{address.alias}{address.defaultAddress ? ' · 기본' : ''}</strong>
          <span>{address.recipient} · {address.phone}</span>
          <span>{address.line1}, {address.city} ({address.postalCode})</span>
        </span>
      </label>)}
      {needsConfirmation && candidate && <button type="button" onClick={() => choose(candidate)}>변경된 배송지 확인</button>}
    </fieldset>
    {error && <p role="alert">{error}</p>}
    <button type="button" className="link-button" disabled={disabled || loading} onClick={() => void refresh()}>
      {loading ? '배송지 확인 중' : '배송지 다시 확인'}
    </button>
  </section>;
}
