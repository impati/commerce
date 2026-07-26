package com.impati.commerce.member.application;

import com.impati.commerce.common.ApiContracts.AddAddressRequest;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 프로필과 배송지.
 *
 * <p>인증은 여기 없다. 가입·이메일 소유 확인은 {@link RegistrationService}, 로그인·세션은
 * {@link SessionService}가 맡는다. 저장소 하나만 쓰는 이 클래스와 달리 그쪽은 토큰·해시·시계·TTL을
 * 필요로 하며, 바뀌는 이유도 다르다 — 이쪽은 배송 요구로, 그쪽은 보안 요구로 바뀐다.
 */
@Service
public class MemberService {
    private final MemberRepository members;

    public MemberService(MemberRepository members) {
        this.members = members;
    }

    /**
     * 배송지를 추가한다.
     *
     * <p>필드를 하나씩 받지 않고 요청 객체로 받는다. alias·recipient·phone·line1·city·postalCode는
     * 전부 String이라 위치로 넘기면 두 개가 뒤바뀌어도 컴파일러도 테스트도 잡지 못한다.
     * 같은 종류의 실수를 JDBC 위치 바인딩에서 이미 겪었다.
     */
    @Transactional
    public AddressResponse addAddress(String memberId, AddAddressRequest request) {
        var member = members.findById(memberId)
                .orElseThrow(() -> DomainException.notFound("member not found"));
        var address = MemberMapper.toAddress(request);
        member.addAddress(address);
        members.save(member);
        return MemberMapper.toResponse(address);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(String memberId) {
        return MemberMapper.toResponse(members.findById(memberId)
                .orElseThrow(() -> DomainException.notFound("member not found")));
    }
}
