package com.impati.commerce.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.application.port.out.MemberRepository;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;
import com.impati.commerce.member.domain.MemberModels.PasswordHash;
import com.impati.commerce.test.RequiresDatabase;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@RequiresDatabase
class AddressManagementTest {
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    /** [PD-0022-R4] 같은 버전의 추가 요청을 반복해도 주소가 중복 생성되지 않는다. */
    @Test
    void managesAddressesWithRequiredVersionsAndRejectsDuplicateAddition() throws Exception {
        var member = member("http");
        var body = fields(0);
        mvc.perform(post("/members/me/addresses").header("X-Member-Id", member.id())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("defaultAddress").value(true));
        mvc.perform(post("/members/me/addresses").header("X-Member-Id", member.id())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("address_book_changed"));
    }

    /** [PD-0022-R4] 실제로 경합한 두 저장 중 하나만 성공하며 주소록 변경이 유실되지 않는다. */
    @Test
    void commitsOnlyOneOfTwoConcurrentAddressChanges() throws Exception {
        var original = member("race");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> concurrentAdd(original.id(), "first", ready, start));
            var second = pool.submit(() -> concurrentAdd(original.id(), "second", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var saved = memberRepository.findById(original.id()).orElseThrow();
        assertThat(saved.addressBookVersion()).isEqualTo(1);
        assertThat(saved.addresses()).hasSize(1);
        assertThat(saved.addresses().getFirst().defaultAddress()).isTrue();
    }

    /** [PD-0022-R4] 이메일 인증과 주소록 변경은 서로의 최신 상태를 덮어쓰지 않는다. */
    @Test
    void activationAndAddressChangesDoNotOverwriteEachOther() {
        var original = member("activation");
        var staleActivation = memberRepository.findById(original.id()).orElseThrow();
        var staleAddresses = memberRepository.findById(original.id()).orElseThrow();
        original.addAddress(address("first"));
        memberRepository.save(original);
        staleActivation.activate();
        memberRepository.save(staleActivation);
        staleAddresses.addAddress(address("stale"));
        assertThatThrownBy(() -> memberRepository.save(staleAddresses)).isInstanceOf(DomainException.class);
        var latest = memberRepository.findById(original.id()).orElseThrow();
        assertThat(latest.isActive()).isTrue();
        assertThat(latest.addresses()).hasSize(1);
        // 주소록 저장도 오래된 회원 상태를 쓰지 않아야 한다.
        var beforeActivation = member("reverse");
        var edit = memberRepository.findById(beforeActivation.id()).orElseThrow();
        beforeActivation.activate();
        memberRepository.save(beforeActivation);
        edit.addAddress(address("after"));
        memberRepository.save(edit);
        assertThat(memberRepository.findById(edit.id()).orElseThrow().isActive()).isTrue();
    }

    private boolean concurrentAdd(String memberId, String alias, CountDownLatch ready, CountDownLatch start) throws Exception {
        var copy = memberRepository.findById(memberId).orElseThrow();
        copy.addAddress(address(alias));
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        try {
            memberRepository.save(copy);
            return true;
        } catch (DomainException error) {
            assertThat(error.code()).isEqualTo("address_book_changed");
            return false;
        }
    }

    private Member member(String suffix) {
        var member = new Member("address-" + suffix + "@example.test", "Member", new PasswordHash("hash"));
        memberRepository.save(member);
        return member;
    }

    private Address address(String alias) {
        return new Address(alias, "Member", "010", "Road", "Seoul", "00000", false);
    }

    private Map<String, Object> fields(long version) {
        return Map.of("alias", "home", "recipient", "Member", "phone", "010", "line1", "Road",
                "city", "Seoul", "postalCode", "00000", "defaultAddress", false, "expectedVersion", version);
    }
}
