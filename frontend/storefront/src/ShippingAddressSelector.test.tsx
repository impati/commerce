// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ShippingAddressSelector } from './ShippingAddressSelector';
import { api, UnauthorizedError } from './api';
import { session } from './session';
import type { AddressChoice, Member } from './types';

const home: AddressChoice = {
  id: 'home', alias: '집', recipient: '회원', phone: '010', line1: '서울로', city: '서울',
  postalCode: '00000', defaultAddress: true, confirmationToken: 'home-token'
};
const office: AddressChoice = { ...home, id: 'office', alias: '회사', defaultAddress: false, confirmationToken: 'office-token' };
const member: Member = { id: 'm', name: '회원', email: 'm@example.test', status: 'ACTIVE',
  addressBookVersion: 2, addresses: [home, office] };
const changed = vi.fn();
const expired = vi.fn();

beforeEach(() => {
  localStorage.clear();
  changed.mockReset();
  expired.mockReset();
  vi.spyOn(api, 'me').mockResolvedValue(member);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
function open(current = member) {
  return render(<MemoryRouter><ShippingAddressSelector member={current} revision={0} disabled={false}
    onChange={changed} onExpired={expired} /></MemoryRouter>);
}

// [PD-0022-R5] 기본 주소로 시작하고 다른 배송지를 직접 선택할 수 있다.
test('selects the default address initially and permits another explicit choice', () => {
  open();
  expect(changed).toHaveBeenLastCalledWith(home);
  fireEvent.click(screen.getByRole('radio', { name: /회사/ }));
  expect(changed).toHaveBeenLastCalledWith(office);
  expect(screen.getByRole('radio', { name: /회사/ })).toBeChecked();
});

// [PD-0022-R6] 기본 지정과 별칭 변경은 기존 선택을 바꾸거나 재확인을 요구하지 않는다.
test('preserves the chosen address across alias and default designation changes', async () => {
  const renamed = { ...home, alias: '우리 집', defaultAddress: false };
  vi.mocked(api.me).mockResolvedValue({ ...member, addressBookVersion: 3,
    addresses: [renamed, { ...office, defaultAddress: true }] });
  open();
  fireEvent.click(screen.getByRole('button', { name: '배송지 다시 확인' }));
  await waitFor(() => expect(changed).toHaveBeenLastCalledWith(renamed));
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.getByRole('radio', { name: /우리 집/ })).toBeChecked();
});

// [PD-0022-R6] 새 값을 조회한 것만으로 확인 완료로 취급하지 않는다.
test('requires explicit acknowledgement of changed delivery information', async () => {
  const latest = { ...home, line1: '새 주소', confirmationToken: 'new-token' };
  vi.mocked(api.me).mockResolvedValue({ ...member, addresses: [latest, office] });
  open();
  fireEvent.click(screen.getByRole('button', { name: '배송지 다시 확인' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('배송 정보가 변경됐습니다.');
  expect(changed).toHaveBeenLastCalledWith(null);
  expect(screen.getByRole('radio', { name: /새 주소/ })).toBeChecked();
  fireEvent.click(screen.getByRole('button', { name: '변경된 배송지 확인' }));
  expect(changed).toHaveBeenLastCalledWith(latest);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

// [PD-0022-R6] 삭제된 주소 대신 남은 주소를 묵시적으로 선택하지 않는다.
test('requires a new choice when the selected address has been deleted', async () => {
  vi.mocked(api.me).mockResolvedValue({ ...member, addresses: [{ ...office, defaultAddress: true }] });
  open();
  fireEvent.click(screen.getByRole('button', { name: '배송지 다시 확인' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('배송지를 다시 선택해주세요.');
  expect(screen.getByRole('radio', { name: /회사/ })).not.toBeChecked();
  expect(changed).toHaveBeenLastCalledWith(null);
  fireEvent.click(screen.getByRole('radio', { name: /회사/ }));
  expect(changed).toHaveBeenLastCalledWith(expect.objectContaining({ id: 'office' }));
});

// [PD-0022-R9] 조회가 실패하면 이전 목록을 클릭해서 주문 제한을 풀 수 없다.
test('keeps checkout and choices blocked until a failed lookup is recovered', async () => {
  vi.mocked(api.me).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(member);
  open();
  fireEvent.click(screen.getByRole('button', { name: '배송지 다시 확인' }));
  await screen.findByRole('alert');
  expect(changed).toHaveBeenLastCalledWith(null);
  expect(screen.getByRole('radio', { name: /회사/ })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '배송지 다시 확인' }));
  await waitFor(() => expect(changed).toHaveBeenLastCalledWith(home));
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

// [PD-0022-R9] 이전 관리 화면에서 확인되지 않은 변경이 있으면 먼저 조회한다.
test('queries an unresolved address change before allowing checkout', async () => {
  const changeKey = session.beginAddressChange(member.id);
  session.markAddressChangeChecking(changeKey);
  open();
  expect(changed).toHaveBeenCalledWith(null);
  await waitFor(() => expect(changed).toHaveBeenLastCalledWith(home));
  expect(session.addressChangePending(member.id)).toBe(false);
});

// [PD-0022-R1] 다른 계정 또는 만료 세션의 주소를 현재 주문에 전달하지 않는다.
test.each(['account', 'expired'])('invalidates identity on %s during an address refresh', async mode => {
  if (mode === 'account') {
    vi.mocked(api.me).mockResolvedValue({ ...member, id: 'other' });
  } else {
    vi.mocked(api.me).mockRejectedValue(new UnauthorizedError());
  }
  open();
  fireEvent.click(screen.getByRole('button', { name: '배송지 다시 확인' }));
  await waitFor(() => expect(expired).toHaveBeenCalledTimes(1));
  expect(changed).toHaveBeenLastCalledWith(null);
});

// [PD-0022-R6] 응답 순서가 바뀌어도 최신 재조회 결과를 유지한다.
test('ignores a delayed response from an older refresh', async () => {
  let resolveOld!: (value: Member) => void;
  const old = new Promise<Member>(resolve => { resolveOld = resolve; });
  const latest = { ...home, line1: '새 주소', confirmationToken: 'new-token' };
  vi.mocked(api.me).mockReturnValueOnce(old).mockResolvedValueOnce({ ...member, addresses: [latest] });
  open();
  fireEvent.click(screen.getByRole('button', { name: '배송지 다시 확인' }));
  fireEvent(window, new Event('focus'));
  await screen.findByRole('alert');
  await act(async () => { resolveOld(member); });
  expect(changed).toHaveBeenLastCalledWith(null);
  expect(screen.getByRole('radio', { name: /새 주소/ })).toBeChecked();
});

// [PD-0022-R9] 목록을 읽어도 다른 탭의 응답 대기 중인 저장까지 확인 완료로 취급하지 않는다.
test('keeps checkout blocked while another address write is still in flight', async () => {
  const first = session.beginAddressChange(member.id);
  const second = session.beginAddressChange(member.id);
  session.markAddressChangeChecking(first);
  open();
  await screen.findByRole('alert');
  expect(changed).toHaveBeenLastCalledWith(null);
  expect(session.addressChangePending(member.id)).toBe(true);
  expect(screen.getByRole('radio', { name: /회사/ })).toBeDisabled();
  session.markAddressChangeChecking(second);
  fireEvent.click(screen.getByRole('button', { name: '배송지 다시 확인' }));
  await waitFor(() => expect(changed).toHaveBeenLastCalledWith(home));
  expect(session.addressChangePending(member.id)).toBe(false);
});
