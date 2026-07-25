package com.impati.commerce.member.adapter.in.web;

import com.impati.commerce.common.ApiContracts.AddAddressRequest;
import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.ApiContracts.RegisterMemberRequest;
import com.impati.commerce.member.application.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/members")
public class MemberController {
    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    @PostMapping
    MemberResponse register(@RequestBody RegisterMemberRequest request) {
        return members.register(request.email(), request.name(), request.password());
    }

    @GetMapping
    List<MemberResponse> list() {
        return members.list();
    }

    @GetMapping("/{memberId}")
    MemberResponse get(@PathVariable String memberId) {
        return members.get(memberId);
    }

    @PostMapping("/{memberId}/addresses")
    AddressResponse addAddress(@PathVariable String memberId, @RequestBody AddAddressRequest request) {
        return members.addAddress(
                memberId,
                request.alias(),
                request.recipient(),
                request.phone(),
                request.line1(),
                request.city(),
                request.postalCode(),
                request.defaultAddress()
        );
    }
}

