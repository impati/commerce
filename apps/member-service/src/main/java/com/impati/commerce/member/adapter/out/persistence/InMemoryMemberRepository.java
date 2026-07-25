package com.impati.commerce.member.adapter.out.persistence;

import com.impati.commerce.member.application.MemberRepository;
import com.impati.commerce.member.domain.MemberModels.Member;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryMemberRepository implements MemberRepository {
    private final Map<String, Member> members = new ConcurrentHashMap<>();
    private final Map<String, String> emailIndex = new ConcurrentHashMap<>();

    @Override
    public void save(Member member) {
        members.put(member.id(), member);
        emailIndex.put(member.email().toLowerCase(), member.id());
    }

    @Override
    public Optional<Member> findById(String memberId) {
        return Optional.ofNullable(members.get(memberId));
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return Optional.ofNullable(emailIndex.get(email.toLowerCase())).map(members::get);
    }

    @Override
    public Collection<Member> findAll() {
        return members.values();
    }
}
