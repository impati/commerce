package com.impati.commerce.member.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.in.MemberAddress;
import com.impati.commerce.member.application.port.in.MemberDetails;
import com.impati.commerce.member.application.port.in.MemberUseCase;
import com.impati.commerce.member.application.port.in.NewAddress;
import com.impati.commerce.member.application.port.out.MemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 프로필과 배송지.
 *
 * <p>인증은 여기 없다. 가입·이메일 소유 확인은 {@link RegistrationExecutor}, 로그인·세션은
 * {@link SessionExecutor}가 맡는다. 저장소 하나만 쓰는 이 클래스와 달리 그쪽은 토큰·해시·시계·TTL을
 * 필요로 하며, 바뀌는 이유도 다르다 — 이쪽은 배송 요구로, 그쪽은 보안 요구로 바뀐다.
 */
@Component
public class MemberExecutor implements MemberUseCase {
    private final MemberRepository memberRepository;

    public MemberExecutor(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 배송지를 추가한다.
     *
     * <p>필드를 하나씩 받지 않고 요청 객체로 받는다. alias·recipient·phone·line1·city·postalCode는
     * 전부 String이라 위치로 넘기면 두 개가 뒤바뀌어도 컴파일러도 테스트도 잡지 못한다.
     * 같은 종류의 실수를 JDBC 위치 바인딩에서 이미 겪었다.
     */
    @Transactional
    @Override
    public MemberAddress addAddress(String memberId, NewAddress request, long expectedVersion) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> DomainException.notFound("member not found"));
        member.requireAddressBookVersion(expectedVersion);
        var address = MemberMapper.toAddress(request);
        member.addAddress(address);
        memberRepository.save(member);
        return MemberMapper.toDetails(address);
    }

    @Transactional(readOnly = true)
    @Override
    public MemberDetails get(String memberId) {
        return MemberMapper.toDetails(memberRepository.findById(memberId)
                .orElseThrow(() -> DomainException.notFound("member not found")));
    }
}
