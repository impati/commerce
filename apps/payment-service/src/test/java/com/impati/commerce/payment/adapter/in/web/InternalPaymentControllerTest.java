package com.impati.commerce.payment.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impati.commerce.common.ApiContracts.AuthorizePaymentRequest;
import com.impati.commerce.common.ApiContracts.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 바깥으로 나가는 표현을 고정한다.
 *
 * <p>유스케이스 결과에는 있고 HTTP 계약에는 없는 값이 있다. 어댑터가 그 경계를 지키는지는
 * 응용 계층 테스트로는 볼 수 없으므로 실제 응답을 확인한다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-web;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class InternalPaymentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 대행사 거래 식별자는 응답에 담기지 않는다.
     *
     * <p>대사에 쓰는 내부 값이며 형제 서비스도 브라우저도 쓸 일이 없다. 예전에는 이것이
     * order-service를 거쳐 브라우저까지 나갔다.
     */
    @Test
    void doesNotExposeTheGatewayTransactionId() throws Exception {
        mockMvc.perform(authorize("ord_web_txn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").doesNotExist())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));
    }

    /** 나머지 필드는 그대로 나간다. 지우려던 것은 하나뿐이다. */
    @Test
    void exposesTheRestOfThePayment() throws Exception {
        mockMvc.perform(authorize("ord_web_fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord_web_fields"))
                .andExpect(jsonPath("$.memberId").value("mem_web"))
                .andExpect(jsonPath("$.amount.amount").value(58_000))
                .andExpect(jsonPath("$.amount.currency").value("KRW"))
                .andExpect(jsonPath("$.method").value("CARD"));
    }

    /** 거절은 402로 나간다. 시스템 오류와 구분된다 (PD-0011-R6). */
    @Test
    void declinedAuthorizationIsPaymentRequired() throws Exception {
        mockMvc.perform(post("/internal/payments/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthorizePaymentRequest(
                                "ord_web_declined", "mem_web", Money.krw(1_000), "card_test_decline"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("payment_declined"));
    }

    private org.springframework.test.web.servlet.RequestBuilder authorize(String orderId) throws Exception {
        return post("/internal/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthorizePaymentRequest(
                        orderId, "mem_web", Money.krw(58_000), "card_test_success")));
    }
}
