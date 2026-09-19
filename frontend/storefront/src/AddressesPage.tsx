import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError, UnauthorizedError, addressRequestTimeoutMs } from './api';
import { session } from './session';
import type { AddressChoice, AddressInput, Member } from './types';

export const addressFields = [
  { key: 'alias', label: '별칭', maximum: 64 },
  { key: 'recipient', label: '수령인', maximum: 128 },
  { key: 'phone', label: '전화번호', maximum: 64 },
  { key: 'line1', label: '주소', maximum: 255 },
  { key: 'city', label: '도시', maximum: 128 },
  { key: 'postalCode', label: '우편번호', maximum: 32 }
] as const;

const emptyAddress: AddressInput = {
  alias: '', recipient: '', phone: '', line1: '', city: '', postalCode: '', defaultAddress: false
};

export function AddressesPage() {
  const [member, setMember] = useState<Member | null>(null);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState<AddressInput>(emptyAddress);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const active = useRef(true);
  const operation = useRef(false);
  const generation = useRef(0);
  const clearDraftAfterCheck = useRef(false);
  const loadedMemberId = useRef<string | null>(null);
  const loadedBookVersion = useRef<number | null>(null);
  const editingIdRef = useRef<string | null>(null);

  function clearEditor() {
    editingIdRef.current = null;
    setEditingId(null);
    setDraft(emptyAddress);
  }

  function resetEditorFrom(latest: Member) {
    const edited = latest.addresses.find(address => address.id === editingIdRef.current);
    if (!edited) {
      clearEditor();
      return;
    }
    setDraft({ alias: edited.alias, recipient: edited.recipient, phone: edited.phone,
      line1: edited.line1, city: edited.city, postalCode: edited.postalCode, defaultAddress: false });
  }

  async function reload(expectedMemberId?: string, announceRefresh = false): Promise<boolean> {
    const request = ++generation.current;
    setLoading(true);
    try {
      const latest = await api.me();
      if (!active.current || request !== generation.current) {
        return false;
      }
      if (expectedMemberId && latest.id !== expectedMemberId) {
        session.clear();
        setMember(null);
        clearEditor();
        loadedMemberId.current = null;
        loadedBookVersion.current = null;
        setError('로그인 계정이 변경됐습니다. 쇼핑 화면에서 다시 로그인해주세요.');
        return false;
      }
      const bookChanged = loadedMemberId.current === latest.id
        && loadedBookVersion.current !== null
        && loadedBookVersion.current !== latest.addressBookVersion;
      loadedMemberId.current = latest.id;
      loadedBookVersion.current = latest.addressBookVersion;
      setMember(latest);
      if (clearDraftAfterCheck.current) {
        clearEditor();
        clearDraftAfterCheck.current = false;
      } else if (bookChanged) {
        resetEditorFrom(latest);
        if (announceRefresh) {
          setNotice('다른 곳에서 주소록이 변경되어 작성 중인 입력을 최신 내용으로 초기화했습니다.');
        }
      }
      session.finishAddressCheck(latest.id);
      const pending = session.addressChangePending(latest.id);
      setUnavailable(pending);
      if (pending) {
        setError('다른 요청의 배송지 저장 결과를 확인 중입니다. 완료 후 다시 조회해주세요.');
      }
      return true;
    } catch (problem) {
      if (!active.current || request !== generation.current) {
        return false;
      }
      setUnavailable(true);
      if (problem instanceof UnauthorizedError) {
        session.clear();
        setMember(null);
        clearEditor();
        loadedMemberId.current = null;
        loadedBookVersion.current = null;
        setError('배송지를 관리하려면 로그인해주세요.');
      } else {
        setError('배송지 목록을 확인하지 못했습니다. 다시 조회해주세요.');
      }
      return false;
    } finally {
      if (active.current && request === generation.current) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    active.current = true;
    void reload();
    return () => {
      active.current = false;
      generation.current++;
    };
  }, []);

  useEffect(() => {
    if (!member) {
      return;
    }
    const memberId = member.id;
    function storageChange(event: StorageEvent) {
      if (!event.key?.startsWith('impati.address-change.' + memberId + '.') || operation.current) {
        return;
      }
      setUnavailable(true);
      if (session.addressChangeInFlight(memberId)) {
        setError('다른 요청의 배송지 저장 결과를 확인 중입니다. 완료 후 다시 조회해주세요.');
      } else {
        void reload(memberId, true);
      }
    }
    window.addEventListener('storage', storageChange);
    return () => {
      window.removeEventListener('storage', storageChange);
    };
  }, [member?.id]);

  async function retry() {
    setError('');
    if (await reload(member?.id)) {
      setNotice('최신 배송지 목록을 확인했습니다. 입력과 목록을 확인하고 다시 조작해주세요.');
    }
  }

  async function mutate(command: () => Promise<unknown>, success: string, announceDraftReset = false) {
    if (!member || operation.current || loading || unavailable || session.addressChangePending(member.id)) {
      return;
    }
    const memberId = member.id;
    operation.current = true;
    setBusy(true);
    setError('');
    setNotice('');
    const changeKey = session.beginAddressChange(memberId, addressRequestTimeoutMs);
    const hadDraft = editingIdRef.current !== null
      || addressFields.some(field => draft[field.key].length > 0)
      || draft.defaultAddress;
    let saved = false;
    let message = '';
    try {
      await command();
      saved = true;
      clearDraftAfterCheck.current = true;
      message = success;
    } catch (problem) {
      if (!active.current) {
        return;
      }
      if (problem instanceof UnauthorizedError) {
        session.clear();
        setMember(null);
        clearEditor();
        loadedMemberId.current = null;
        loadedBookVersion.current = null;
        setError('세션이 만료됐습니다. 다시 로그인해주세요.');
        return;
      }
      message = problem instanceof ApiError && problem.status < 500
        ? problem.message
        : '저장 결과를 확인하지 못했습니다. 최신 목록과 입력을 확인하고 다시 조작해주세요.';
    } finally {
      session.markAddressChangeChecking(changeKey);
      // 서버 결과를 조회하기 전에는 다음 관리 요청을 보내지 않는다.
      if (active.current) {
        const checked = await reload(memberId);
        if (saved && checked) {
          clearEditor();
          if (announceDraftReset && hadDraft) {
            message += ' 작성 중인 입력은 최신 주소록에 맞춰 초기화했습니다.';
          }
        }
        setNotice(checked ? message : `${message} 배송지 목록을 다시 조회해주세요.`);
        setBusy(false);
      }
      operation.current = false;
    }
  }

  function edit(address: AddressChoice) {
    editingIdRef.current = address.id;
    setEditingId(address.id);
    setDraft({ alias: address.alias, recipient: address.recipient, phone: address.phone,
      line1: address.line1, city: address.city, postalCode: address.postalCode, defaultAddress: false });
    setNotice('');
    setError('');
  }

  function save(event: FormEvent) {
    event.preventDefault();
    if (!member || operation.current || loading || unavailable || session.addressChangePending(member.id)) {
      return;
    }
    for (const field of addressFields) {
      const value = draft[field.key];
      if (!value.trim() || Array.from(value).length > field.maximum) {
        setError(`${field.label}은(는) 필수이며 ${field.maximum}자 이하여야 합니다.`);
        return;
      }
    }
    const version = member.addressBookVersion;
    if (editingId) {
      void mutate(() => api.updateAddress(editingId, draft, version), '배송지를 수정했습니다.');
    } else {
      void mutate(() => api.addAddress(draft, version), '배송지를 추가했습니다.');
    }
  }

  const locked = busy || loading || unavailable;
  return <main className="orders-layout address-layout">
    <header className="address-heading"><h1>배송지 관리</h1><Link to="/">쇼핑 화면으로</Link></header>
    {notice && <p role="status">{notice}</p>}
    {error && <p role="alert">{error}</p>}
    {loading && <p role="status">배송지 확인 중</p>}
    <button type="button" disabled={busy || loading} onClick={() => void retry()}>배송지 목록 다시 조회</button>
    {!loading && !member && <p><Link to="/">쇼핑 화면에서 로그인</Link></p>}
    {member && <>
      <section aria-label="등록한 배송지" className="address-list">
        {member.addresses.length === 0 && <p>등록한 배송지가 없습니다. 새 배송지를 추가해주세요.</p>}
        {member.addresses.map(address => <article key={address.id} className="panel address-card">
          <h2>{address.alias}{address.defaultAddress ? ' · 기본 배송지' : ''}</h2>
          <p>{address.recipient} · {address.phone}</p>
          <p>{address.line1}, {address.city} ({address.postalCode})</p>
          <div className="address-actions">
            <button type="button" disabled={locked} onClick={() => edit(address)} aria-label={`${address.alias} 수정`}>수정</button>
            <button type="button" disabled={locked} aria-label={`${address.alias} 삭제`}
              onClick={() => void mutate(() => api.removeAddress(address.id, member.addressBookVersion), '배송지를 삭제했습니다.', true)}>삭제</button>
            {!address.defaultAddress && <button type="button" disabled={locked} aria-label={`${address.alias} 기본 지정`}
              onClick={() => void mutate(() => api.setDefaultAddress(address.id, member.addressBookVersion), '기본 배송지를 지정했습니다.', true)}>기본 지정</button>}
          </div>
        </article>)}
      </section>
      <form className="panel address-form" onSubmit={save}>
        <h2>{editingId ? '배송지 수정' : '배송지 추가'}</h2>
        <fieldset disabled={locked}>
          <legend className="sr-only">배송지 입력</legend>
          {addressFields.map(field => <label key={field.key}>
            {field.label}<input required value={draft[field.key]} aria-label={field.label}
              onChange={event => setDraft(current => ({ ...current, [field.key]: event.target.value }))} />
          </label>)}
          {!editingId && <label className="address-checkbox"><input type="checkbox" checked={draft.defaultAddress}
            onChange={event => setDraft(current => ({ ...current, defaultAddress: event.target.checked }))} />기본 배송지로 지정</label>}
          <div className="address-actions"><button type="submit">{busy ? '저장 결과 확인 중' : '배송지 저장'}</button>
            {editingId && <button type="button" onClick={clearEditor}>수정 취소</button>}
          </div>
        </fieldset>
      </form>
    </>}
  </main>;
}
