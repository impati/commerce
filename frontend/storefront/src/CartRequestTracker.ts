type CartRequest = {
  sequence: number;
  session: number;
  memberId: string | null;
};

/** 이전 조회와 이전 로그인 상태에서 시작한 응답을 구분한다. */
export class CartRequestTracker {
  private sequence = 0;
  private session = 0;
  private memberId: string | null = null;

  changeMember(memberId: string | null) {
    this.memberId = memberId;
    this.session++;
    this.sequence++;
  }

  invalidateQueries() {
    this.sequence++;
  }

  begin(): CartRequest {
    return { sequence: ++this.sequence, session: this.session, memberId: this.memberId };
  }

  sameSession(request: CartRequest): boolean {
    return request.session === this.session;
  }

  isCurrent(request: CartRequest): boolean {
    return this.sameSession(request) && request.sequence === this.sequence;
  }
}
