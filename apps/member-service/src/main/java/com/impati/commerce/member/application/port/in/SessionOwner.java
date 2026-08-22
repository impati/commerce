package com.impati.commerce.member.application.port.in;

/** 세션이 가리키는 신원. 게이트웨이가 이것만 알면 된다. */
public record SessionOwner(String memberId) {
}
