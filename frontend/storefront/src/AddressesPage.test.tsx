// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest';
import { beforeEach, afterEach, expect, test, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AddressesPage } from './AddressesPage';
import { api, ApiError, UnauthorizedError } from './api';
import { session } from './session';
import type { AddressChoice, Member } from './types';

const address: AddressChoice = { id: 'a', alias: '집', recipient: '회원', phone: '010', line1: '서울로',
  city: '서울', postalCode: '00000', defaultAddress: true, confirmationToken: 'token' };
const member: Member = { id: 'm', name: '회원', email: 'm@example.test', status: 'ACTIVE',
  addressBookVersion: 4, addresses: [address] };
const fields = { alias: '회사', recipient: '새 수령인', phone: '020', line1: '새 주소', city: '부산', postalCode: '99999' };

beforeEach(() => {
  localStorage.clear();
  vi.spyOn(api, 'me').mockResolvedValue(member);
  vi.spyOn(api, 'addAddress').mockResolvedValue(address);
  vi.spyOn(api, 'updateAddress').mockResolvedValue(member);
  vi.spyOn(api, 'removeAddress').mockResolvedValue(member);
  vi.spyOn(api, 'setDefaultAddress').mockResolvedValue(member);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
async function open() {
  render(<MemoryRouter><AddressesPage /></MemoryRouter>);
  await screen.findByRole('heading', { name: '집 · 기본 배송지' });
}
function fill() {
  for (const [key, label] of Object.entries({ alias: '별칭', recipient: '수령인', phone: '전화번호', line1: '주소', city: '도시', postalCode: '우편번호' })) {
    fireEvent.change(screen.getByRole('textbox', { name: label }), { target: { value: fields[key as keyof typeof fields] } });
  }
}

// [PD-0022-R1, PD-0022-R4, PD-0022-R8] 추가는 조회 버전과 각 필드를 전달하며 확인 완료 후 입력을 지운다.
test('adds an address with the viewed book version and distinct field values', async () => {
  await open();
  fill();
  fireEvent.click(screen.getByRole('button', { name: '배송지 저장' }));
  await screen.findByText('배송지를 추가했습니다.');
  expect(api.addAddress).toHaveBeenCalledWith({ ...fields, defaultAddress: false }, 4);
  expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue('');
  expect(session.addressChangePending(member.id)).toBe(false);
});

// [PD-0022-R1, PD-0022-R4] 편집은 기존 ID와 버전을 사용한다.
test('edits an existing address rather than adding a new one', async () => {
  await open();
  fireEvent.click(screen.getByRole('button', { name: '집 수정' }));
  expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue('집');
  fill();
  fireEvent.click(screen.getByRole('button', { name: '배송지 저장' }));
  await screen.findByText('배송지를 수정했습니다.');
  expect(api.updateAddress).toHaveBeenCalledWith('a', { ...fields, defaultAddress: false }, 4);
  expect(api.addAddress).not.toHaveBeenCalled();
});

// [PD-0022-R3] 마지막 주소 삭제도 가능하며 빈 목록을 보여준다.
test('deletes the last address with the current book version', async () => {
  await open();
  vi.mocked(api.me).mockResolvedValue({ ...member, addressBookVersion: 5, addresses: [] });
  fireEvent.click(screen.getByRole('button', { name: '집 삭제' }));
  await screen.findByText('등록한 배송지가 없습니다. 새 배송지를 추가해주세요.');
  expect(api.removeAddress).toHaveBeenCalledWith('a', 4);
});

// [PD-0022-R2] 다른 기본 주소를 지정하는 명령도 조회 버전을 사용한다.
test('sets another address as default with the viewed version', async () => {
  vi.mocked(api.me).mockResolvedValue({ ...member, addresses: [address, { ...address, id: 'b', alias: '회사', defaultAddress: false }] });
  await open();
  fireEvent.click(screen.getByRole('button', { name: '회사 기본 지정' }));
  await screen.findByText('기본 배송지를 지정했습니다.');
  expect(api.setDefaultAddress).toHaveBeenCalledWith('b', 4);
});

// [PD-0022-R4, PD-0022-R10] 충돌은 최신 목록으로 입력을 초기화하며 자동 재적용하지 않는다.
test('resets the input after a version conflict without retrying the mutation', async () => {
  await open();
  fill();
  vi.mocked(api.addAddress).mockRejectedValue(new ApiError(409, '주소록이 변경됐습니다.', 'address_book_changed'));
  vi.mocked(api.me).mockResolvedValue({ ...member, addressBookVersion: 5, addresses: [{ ...address, alias: '변경된 집' }] });
  fireEvent.click(screen.getByRole('button', { name: '배송지 저장' }));
  await screen.findByText('주소록이 변경됐습니다.');
  expect(screen.getByRole('heading', { name: '변경된 집 · 기본 배송지' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue('');
  expect(api.addAddress).toHaveBeenCalledTimes(1);
});

// [PD-0022-R10] 다른 곳의 주소록 변경은 편집 중인 배송지를 서버 최신 값으로 초기화한다.
test('resets an edited address to the latest values after an external book change', async () => {
  const latest = { ...member, addressBookVersion: 5,
    addresses: [{ ...address, recipient: '다른 탭 수령인', line1: '다른 탭 주소' }] };
  vi.mocked(api.me).mockResolvedValueOnce(member).mockResolvedValueOnce(latest);
  await open();
  fireEvent.click(screen.getByRole('button', { name: '집 수정' }));
  fill();
  window.dispatchEvent(new StorageEvent('storage', { key: `impati.address-change.${member.id}.external` }));
  await screen.findByText('다른 곳에서 주소록이 변경되어 작성 중인 입력을 최신 내용으로 초기화했습니다.');
  expect(screen.getByRole('textbox', { name: '수령인' })).toHaveValue('다른 탭 수령인');
  expect(screen.getByRole('textbox', { name: '주소' })).toHaveValue('다른 탭 주소');
  expect(screen.getByRole('heading', { name: '배송지 수정' })).toBeInTheDocument();
});

// [PD-0022-R10] 편집 대상이 삭제됐으면 편집을 끝내고 새 배송지 입력으로 돌아간다.
test('ends editing when an external book change deletes the edited address', async () => {
  vi.mocked(api.me).mockResolvedValueOnce(member).mockResolvedValueOnce({
    ...member, addressBookVersion: 5, addresses: []
  });
  await open();
  fireEvent.click(screen.getByRole('button', { name: '집 수정' }));
  window.dispatchEvent(new StorageEvent('storage', { key: `impati.address-change.${member.id}.external` }));
  await screen.findByText('다른 곳에서 주소록이 변경되어 작성 중인 입력을 최신 내용으로 초기화했습니다.');
  expect(screen.getByRole('heading', { name: '배송지 추가' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue('');
});

// [PD-0022-R10] 새 배송지 초안도 주소록 갱신 시 버리고 최신 목록을 기준으로 다시 작성한다.
test('clears a new-address draft after an external book change', async () => {
  vi.mocked(api.me).mockResolvedValueOnce(member).mockResolvedValueOnce({
    ...member, addressBookVersion: 5, addresses: [address]
  });
  await open();
  fill();
  window.dispatchEvent(new StorageEvent('storage', { key: `impati.address-change.${member.id}.external` }));
  await screen.findByText('다른 곳에서 주소록이 변경되어 작성 중인 입력을 최신 내용으로 초기화했습니다.');
  expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue('');
});

// [PD-0022-R10] 같은 화면의 다른 관리 조작도 입력을 초기화하고 그 사실을 알린다.
test('clears a draft and explains the reset after setting another default address', async () => {
  const second = { ...address, id: 'b', alias: '회사', defaultAddress: false };
  vi.mocked(api.me).mockResolvedValueOnce({ ...member, addresses: [address, second] }).mockResolvedValueOnce({
    ...member, addressBookVersion: 5,
    addresses: [{ ...address, defaultAddress: false }, { ...second, defaultAddress: true }]
  });
  await open();
  fill();
  fireEvent.click(screen.getByRole('button', { name: '회사 기본 지정' }));
  await screen.findByText('기본 배송지를 지정했습니다. 작성 중인 입력은 최신 주소록에 맞춰 초기화했습니다.');
  expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue('');
});

// [PD-0022-R9] 결과 미확인과 조회 실패 동안 입력을 보존하고 조작을 잠근다.
test('keeps input and blocks commands after an unknown result until the query is recovered', async () => {
  await open();
  fill();
  vi.mocked(api.addAddress).mockRejectedValue(new TypeError('lost response'));
  vi.mocked(api.me).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(member);
  fireEvent.click(screen.getByRole('button', { name: '배송지 저장' }));
  await screen.findByRole('alert');
  expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue('회사');
  expect(screen.getByRole('button', { name: '집 삭제' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '배송지 저장' })).toBeDisabled();
  expect(session.addressChangePending(member.id)).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: '배송지 목록 다시 조회' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '배송지 저장' })).toBeEnabled());
  expect(api.addAddress).toHaveBeenCalledTimes(1);
  expect(session.addressChangePending(member.id)).toBe(false);
});

// [PD-0022-R9] 중복 클릭과 후속 조회 중 다른 조작은 새 요청을 만들지 않는다.
test('locks all commands through the follow-up lookup', async () => {
  await open();
  let resolve!: (value: Member) => void;
  vi.mocked(api.me).mockReturnValue(new Promise<Member>(done => { resolve = done; }));
  fill();
  const save = screen.getByRole('button', { name: '배송지 저장' });
  fireEvent.click(save);
  fireEvent.click(save);
  await waitFor(() => expect(api.me).toHaveBeenCalledTimes(2));
  expect(api.addAddress).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: '집 삭제' })).toBeDisabled();
  await act(async () => { resolve(member); });
  await screen.findByText('배송지를 추가했습니다.');
});

// [PD-0022-R8] 공백만 있는 값과 길이 초과를 화면에서도 거절한다.
test('rejects whitespace-only and overlong form values before sending a mutation', async () => {
  await open();
  fill();
  for (const value of ['   ', 'x'.repeat(65)]) {
    fireEvent.change(screen.getByRole('textbox', { name: '별칭' }), { target: { value } });
    fireEvent.click(screen.getByRole('button', { name: '배송지 저장' }));
    expect(screen.getByRole('alert')).toHaveTextContent('64자 이하여야 합니다.');
  }
  expect(api.addAddress).not.toHaveBeenCalled();
});

// [PD-0022-R1] 인증 조회 실패 시 배송지를 데모 데이터로 대체하지 않는다.
test('requires login after an expired session', async () => {
  vi.mocked(api.me).mockRejectedValue(new UnauthorizedError());
  render(<MemoryRouter><AddressesPage /></MemoryRouter>);
  await screen.findByText('배송지를 관리하려면 로그인해주세요.');
  expect(screen.queryByRole('button', { name: '배송지 저장' })).not.toBeInTheDocument();
});

// [PD-0022-R9] 저장 성공을 알고 있다면 후속 조회 복구 뒤 입력을 지워 중복 추가를 유도하지 않는다.
test('clears a successfully saved input after its failed follow-up lookup is recovered', async () => {
  await open();
  fill();
  vi.mocked(api.me).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(member);
  fireEvent.click(screen.getByRole('button', { name: '배송지 저장' }));
  await screen.findByRole('alert');
  expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue('회사');
  fireEvent.click(screen.getByRole('button', { name: '배송지 목록 다시 조회' }));
  await waitFor(() => expect(screen.getByRole('textbox', { name: '별칭' })).toHaveValue(''));
  expect(api.addAddress).toHaveBeenCalledTimes(1);
});
