package com.course_aura_hub.membercard;

import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No payment provider is configured here, so everything paid goes through the card. */
class MemberCardIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String MINE = "/api/v1/me/member-card";
    private static final String ADMIN = "/api/v1/admin/member-cards";
    private static final String SECOND_PAID_COURSE = "spring-boot-microservices";

    @Autowired
    private CourseRepository courseRepository;

    private BigDecimal price(String slug) {
        return courseRepository.findBySlug(slug).orElseThrow().currentPrice().price();
    }

    private ResultActions apply(TestUser user) throws Exception {
        return mockMvc.perform(asJson(user, post(MINE + "/application"), Map.of(
                "fullName", "Jane Learner",
                "phone", "+855 12 345 678",
                "dateOfBirth", "2000-01-15",
                "address", "Phnom Penh")));
    }

    private int applicationId(TestUser user) throws Exception {
        String body = apply(user).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private int issueCard(TestUser learner, TestUser admin) throws Exception {
        mockMvc.perform(as(admin, post(ADMIN + "/applications/" + applicationId(learner) + "/approve")))
                .andExpect(status().isOk());
        String body = mockMvc.perform(as(learner, get(MINE))).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.card.id");
    }

    private ResultActions adjust(TestUser admin, int cardId, String type, BigDecimal amount, String key) throws Exception {
        return mockMvc.perform(asJson(admin, post(ADMIN + "/" + cardId + "/adjustments"), Map.of(
                "type", type, "amount", amount, "note", "Cash at the front desk", "idempotencyKey", key)));
    }

    private void topUp(TestUser admin, int cardId, BigDecimal amount) throws Exception {
        adjust(admin, cardId, "TOP_UP", amount, UUID.randomUUID().toString()).andExpect(status().isCreated());
    }

    private BigDecimal balance(TestUser learner) throws Exception {
        String body = mockMvc.perform(as(learner, get(MINE))).andReturn().getResponse().getContentAsString();
        return new BigDecimal(JsonPath.read(body, "$.data.card.balance").toString());
    }

    private long ledgerRows(int cardId) {
        Long rows = jdbcTemplate.queryForObject(
                "select count(*) from member_card_transactions where card_id = ?", Long.class, cardId);
        return rows == null ? 0 : rows;
    }

    private ResultActions payByCard(TestUser learner, String slug) throws Exception {
        return mockMvc.perform(asJson(learner, post("/api/v1/me/orders"),
                Map.of("courseSlug", slug, "paymentMethod", "MEMBER_CARD")));
    }

    private void expectEnrolled(TestUser user, String slug, boolean enrolled) throws Exception {
        mockMvc.perform(as(user, get("/api/v1/courses/" + slug)))
                .andExpect(jsonPath("$.data.enrolled").value(enrolled));
    }

    private void expectSameAmount(BigDecimal actual, BigDecimal expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    @Test
    void application_canOnlyBePendingOnce() throws Exception {
        TestUser learner = registerUser();

        apply(learner).andExpect(status().isCreated());
        apply(learner).andExpect(status().isConflict());

        mockMvc.perform(as(learner, get(MINE)))
                .andExpect(jsonPath("$.data.card").doesNotExist())
                .andExpect(jsonPath("$.data.application.status").value("PENDING"));
    }

    @Test
    void application_validatesItsFields() throws Exception {
        mockMvc.perform(asJson(registerUser(), post(MINE + "/application"), Map.of(
                        "fullName", "", "phone", "nope", "dateOfBirth", "2999-01-01", "address", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approving_issuesAnEmptyCardOnce() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int application = applicationId(learner);

        mockMvc.perform(as(admin, post(ADMIN + "/applications/" + application + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(as(admin, post(ADMIN + "/applications/" + application + "/approve")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(learner, get(MINE)))
                .andExpect(jsonPath("$.data.card.status").value("ACTIVE"));
        expectSameAmount(balance(learner), BigDecimal.ZERO);
        apply(learner).andExpect(status().isConflict());
    }

    @Test
    void rejecting_needsAReasonAndAllowsANewApplication() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int application = applicationId(learner);

        mockMvc.perform(asJson(admin, post(ADMIN + "/applications/" + application + "/reject"), Map.of("note", "")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, post(ADMIN + "/applications/" + application + "/reject"), Map.of("note", "Wrong phone")))
                .andExpect(status().isOk());

        mockMvc.perform(as(learner, get(MINE)))
                .andExpect(jsonPath("$.data.application.reviewNote").value("Wrong phone"));
        apply(learner).andExpect(status().isCreated());
    }

    @Test
    void adminEndpoints_areAdminOnly() throws Exception {
        TestUser learner = registerUser();
        int application = applicationId(learner);

        mockMvc.perform(as(learner, get(ADMIN))).andExpect(status().isForbidden());
        mockMvc.perform(as(learner, get(ADMIN + "/applications"))).andExpect(status().isForbidden());
        mockMvc.perform(as(learner, post(ADMIN + "/applications/" + application + "/approve")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(MINE)).andExpect(status().isUnauthorized());
    }

    @Test
    void admins_cannotHandleTheirOwnCard() throws Exception {
        TestUser admin = registerAdmin();
        TestUser otherAdmin = registerAdmin();
        int application = applicationId(admin);

        mockMvc.perform(as(admin, post(ADMIN + "/applications/" + application + "/approve")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(otherAdmin, post(ADMIN + "/applications/" + application + "/approve")))
                .andExpect(status().isOk());
        int cardId = JsonPath.read(mockMvc.perform(as(admin, get(MINE))).andReturn().getResponse().getContentAsString(), "$.data.card.id");

        adjust(admin, cardId, "TOP_UP", new BigDecimal("50.00"), "own").andExpect(status().isForbidden());
    }

    @Test
    void adjustment_rejectsInvalidAmountsAndTypes() throws Exception {
        TestUser admin = registerAdmin();
        int cardId = issueCard(registerUser(), admin);

        adjust(admin, cardId, "TOP_UP", BigDecimal.ZERO, "zero").andExpect(status().isBadRequest());
        adjust(admin, cardId, "TOP_UP", new BigDecimal("-5.00"), "negative").andExpect(status().isBadRequest());
        adjust(admin, cardId, "TOP_UP", new BigDecimal("1.001"), "precision").andExpect(status().isBadRequest());
        adjust(admin, cardId, "PURCHASE", BigDecimal.TEN, "type").andExpect(status().isBadRequest());
        assertThat(ledgerRows(cardId)).isZero();
    }

    @Test
    void deduction_cannotOverdraw() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        topUp(admin, cardId, new BigDecimal("10.00"));

        adjust(admin, cardId, "DEDUCTION", new BigDecimal("10.01"), "too-much").andExpect(status().isBadRequest());
        adjust(admin, cardId, "DEDUCTION", new BigDecimal("4.00"), "ok").andExpect(status().isCreated());

        expectSameAmount(balance(learner), new BigDecimal("6.00"));
        assertThat(ledgerRows(cardId)).isEqualTo(2);
    }

    @Test
    void repeatedAdjustment_changesTheBalanceOnce() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);

        adjust(admin, cardId, "TOP_UP", new BigDecimal("25.00"), "retry").andExpect(status().isCreated());
        adjust(admin, cardId, "TOP_UP", new BigDecimal("25.00"), "retry").andExpect(status().isCreated());
        adjust(admin, cardId, "TOP_UP", new BigDecimal("99.00"), "retry").andExpect(status().isConflict());

        expectSameAmount(balance(learner), new BigDecimal("25.00"));
        assertThat(ledgerRows(cardId)).isEqualTo(1);
    }

    @Test
    void parallelDeductions_neverOverdraw() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        topUp(admin, cardId, new BigDecimal("10.00"));

        List<CompletableFuture<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String key = "parallel-" + i;
            attempts.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return adjust(admin, cardId, "DEDUCTION", new BigDecimal("4.00"), key)
                            .andReturn().getResponse().getStatus();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }));
        }

        long succeeded = attempts.stream().map(CompletableFuture::join).filter(code -> code == 201).count();
        assertThat(succeeded).isEqualTo(2);
        expectSameAmount(balance(learner), new BigDecimal("2.00"));
    }

    @Test
    void paying_chargesTheCardAndEnrollsWithoutAProvider() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        BigDecimal price = price(PAID_COURSE);
        topUp(admin, cardId, price.add(BigDecimal.ONE));

        payByCard(learner, PAID_COURSE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.checkoutUrl").doesNotExist());

        expectEnrolled(learner, PAID_COURSE, true);
        expectSameAmount(balance(learner), BigDecimal.ONE);
        mockMvc.perform(as(learner, get(MINE + "/transactions")))
                .andExpect(jsonPath("$.data.items[0].type").value("PURCHASE"));
    }

    @Test
    void payingAgain_doesNotChargeTwice() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        BigDecimal price = price(PAID_COURSE);
        topUp(admin, cardId, price.multiply(BigDecimal.TWO));
        payByCard(learner, PAID_COURSE).andExpect(status().isCreated());

        payByCard(learner, PAID_COURSE).andExpect(status().isConflict());

        expectSameAmount(balance(learner), price);
    }

    @Test
    void parallelPayments_chargeOnce() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        BigDecimal price = price(PAID_COURSE);
        topUp(admin, cardId, price.multiply(BigDecimal.valueOf(5)));

        List<CompletableFuture<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            attempts.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return payByCard(learner, PAID_COURSE).andReturn().getResponse().getStatus();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }));
        }
        attempts.forEach(CompletableFuture::join);

        expectSameAmount(balance(learner), price.multiply(BigDecimal.valueOf(4)));
        expectEnrolled(learner, PAID_COURSE, true);
    }

    @Test
    void lowBalance_leavesEverythingUntouched() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        topUp(admin, cardId, BigDecimal.ONE);

        payByCard(learner, PAID_COURSE).andExpect(status().isBadRequest());

        expectEnrolled(learner, PAID_COURSE, false);
        expectSameAmount(balance(learner), BigDecimal.ONE);
        assertThat(ledgerRows(cardId)).isEqualTo(1);
    }

    @Test
    void paying_needsAnActiveCard() throws Exception {
        TestUser noCard = registerUser();
        payByCard(noCard, PAID_COURSE).andExpect(status().isBadRequest());

        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        topUp(admin, cardId, price(PAID_COURSE));
        mockMvc.perform(asJson(admin, patch(ADMIN + "/" + cardId + "/status"), Map.of("status", "SUSPENDED")))
                .andExpect(status().isOk());

        payByCard(learner, PAID_COURSE).andExpect(status().isBadRequest());
        expectEnrolled(learner, PAID_COURSE, false);
    }

    @Test
    void cartCheckout_paysForEveryCourseOnce() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        BigDecimal total = price(PAID_COURSE).add(price(SECOND_PAID_COURSE));
        topUp(admin, cardId, total);
        mockMvc.perform(as(learner, put("/api/v1/me/cart/" + PAID_COURSE))).andExpect(status().isOk());
        mockMvc.perform(as(learner, put("/api/v1/me/cart/" + SECOND_PAID_COURSE))).andExpect(status().isOk());

        mockMvc.perform(asJson(learner, post("/api/v1/me/cart/checkout"), Map.of("paymentMethod", "MEMBER_CARD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orders[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.orders[1].status").value("PAID"));

        expectEnrolled(learner, PAID_COURSE, true);
        expectEnrolled(learner, SECOND_PAID_COURSE, true);
        expectSameAmount(balance(learner), BigDecimal.ZERO);
    }

    @Test
    void refund_returnsTheMoneyToTheCardOnce() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        BigDecimal price = price(PAID_COURSE);
        topUp(admin, cardId, price);
        String body = payByCard(learner, PAID_COURSE).andReturn().getResponse().getContentAsString();
        String orderNumber = JsonPath.read(body, "$.data.orderNumber");

        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + orderNumber + "/refund"))).andExpect(status().isOk());
        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + orderNumber + "/refund"))).andExpect(status().isBadRequest());

        expectSameAmount(balance(learner), price);
        expectEnrolled(learner, PAID_COURSE, false);
    }

    @Test
    void history_isPrivateToItsOwner() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        topUp(admin, cardId, BigDecimal.TEN);

        mockMvc.perform(as(registerUser(), get(MINE + "/transactions")))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(as(registerUser(), get(ADMIN + "/" + cardId + "/transactions")))
                .andExpect(status().isForbidden());
    }

    private int studentId(TestUser learner) throws Exception {
        String body = apply(learner).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.studentId");
    }

    private ResultActions createCard(TestUser admin, int studentId, BigDecimal openingBalance) throws Exception {
        return mockMvc.perform(asJson(admin, post(ADMIN), Map.of(
                "studentId", studentId, "holderName", "Jane Card", "openingBalance", openingBalance)));
    }

    @Test
    void creating_issuesACardWithItsOpeningBalanceInTheLedger() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int studentId = studentId(learner);

        String body = createCard(admin, studentId, BigDecimal.TEN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.holderName").value("Jane Card"))
                .andReturn().getResponse().getContentAsString();
        int cardId = JsonPath.read(body, "$.data.id");

        expectSameAmount(balance(learner), BigDecimal.TEN);
        assertThat(ledgerRows(cardId)).isEqualTo(1);
        // The application it replaced is closed, not left waiting for a review that can't succeed
        mockMvc.perform(as(learner, get(MINE)))
                .andExpect(jsonPath("$.data.application.status").value("APPROVED"));
        createCard(admin, studentId, BigDecimal.ZERO).andExpect(status().isConflict());
    }

    @Test
    void creating_isForAdminsOnly() throws Exception {
        TestUser learner = registerUser();

        createCard(learner, studentId(learner), BigDecimal.TEN).andExpect(status().isForbidden());
    }

    @Test
    void readingAndRenaming_changeNoMoney() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        topUp(admin, cardId, BigDecimal.TEN);

        mockMvc.perform(asJson(admin, put(ADMIN + "/" + cardId), Map.of("holderName", "  Jane Renamed  ")))
                .andExpect(status().isOk());
        mockMvc.perform(as(admin, get(ADMIN + "/" + cardId)))
                .andExpect(jsonPath("$.data.holderName").value("Jane Renamed"));
        mockMvc.perform(asJson(admin, put(ADMIN + "/" + cardId), Map.of("holderName", "")))
                .andExpect(status().isBadRequest());

        expectSameAmount(balance(learner), BigDecimal.TEN);
    }

    @Test
    void search_findsCardsByHolderNumberOrEmail() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);

        mockMvc.perform(as(admin, get(ADMIN).param("search", learner.email().toUpperCase())))
                .andExpect(jsonPath("$.data.items[0].id").value(cardId));
        mockMvc.perform(as(admin, get(ADMIN).param("search", "no-such-card")))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void deleting_removesAnUnusedCardAndAllowsANewApplication() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);

        mockMvc.perform(as(admin, delete(ADMIN + "/" + cardId))).andExpect(status().isOk());

        mockMvc.perform(as(admin, get(ADMIN + "/" + cardId))).andExpect(status().isNotFound());
        mockMvc.perform(as(learner, get(MINE))).andExpect(jsonPath("$.data.card").doesNotExist());
        apply(learner).andExpect(status().isCreated());
    }

    @Test
    void deleting_isRefusedOnceTheCardHasHistory() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        topUp(admin, cardId, BigDecimal.TEN);

        mockMvc.perform(as(admin, delete(ADMIN + "/" + cardId))).andExpect(status().isBadRequest());

        expectSameAmount(balance(learner), BigDecimal.TEN);
        assertThat(ledgerRows(cardId)).isEqualTo(1);
    }

    private ResultActions requestTopUp(TestUser learner, BigDecimal amount) throws Exception {
        return mockMvc.perform(asJson(learner, post(MINE + "/top-ups"), Map.of("amount", amount, "note", "Paid at the desk")));
    }

    private int topUpId(TestUser learner, BigDecimal amount) throws Exception {
        String body = requestTopUp(learner, amount).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    @Test
    void topUpRequest_movesNoMoneyUntilApprovedAndOnlyOnce() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        int cardId = issueCard(learner, admin);
        int request = topUpId(learner, BigDecimal.TEN);

        requestTopUp(learner, BigDecimal.ONE).andExpect(status().isConflict());
        expectSameAmount(balance(learner), BigDecimal.ZERO);

        mockMvc.perform(as(learner, post(ADMIN + "/top-ups/" + request + "/approve"))).andExpect(status().isForbidden());
        mockMvc.perform(as(admin, post(ADMIN + "/top-ups/" + request + "/approve")))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(as(admin, post(ADMIN + "/top-ups/" + request + "/approve"))).andExpect(status().isBadRequest());

        expectSameAmount(balance(learner), BigDecimal.TEN);
        assertThat(ledgerRows(cardId)).isEqualTo(1);
    }

    @Test
    void topUpRequest_canBeRejectedWithAReason() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        issueCard(learner, admin);
        int request = topUpId(learner, BigDecimal.TEN);

        mockMvc.perform(asJson(admin, post(ADMIN + "/top-ups/" + request + "/reject"), Map.of("note", "No cash received")))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        expectSameAmount(balance(learner), BigDecimal.ZERO);
        mockMvc.perform(as(learner, get(MINE + "/top-ups")))
                .andExpect(jsonPath("$.data[0].reviewNote").value("No cash received"));
        requestTopUp(learner, BigDecimal.ONE).andExpect(status().isCreated());
    }

    @Test
    void topUpRequest_needsAnActiveCardAndYourOwnReceipt() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        requestTopUp(registerUser(), BigDecimal.ONE).andExpect(status().isBadRequest());
        int cardId = issueCard(learner, admin);

        mockMvc.perform(asJson(learner, post(MINE + "/top-ups"),
                        Map.of("amount", BigDecimal.ONE, "receiptUrl", "storage://private/receipts/not-mine.pdf")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asJson(admin, patch(ADMIN + "/" + cardId + "/status"), Map.of("status", "SUSPENDED")))
                .andExpect(status().isOk());
        requestTopUp(learner, BigDecimal.ONE).andExpect(status().isBadRequest());
    }

    @Test
    void adminsCantApproveTheirOwnTopUp() throws Exception {
        TestUser admin = registerAdmin();
        TestUser otherAdmin = registerAdmin();
        issueCard(admin, otherAdmin);
        int request = topUpId(admin, BigDecimal.TEN);

        mockMvc.perform(as(admin, post(ADMIN + "/top-ups/" + request + "/approve"))).andExpect(status().isForbidden());

        expectSameAmount(balance(admin), BigDecimal.ZERO);
    }

    @Test
    void report_addsUpMoneyMovedAndIsForAdmins() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        topUp(admin, issueCard(learner, admin), BigDecimal.TEN);

        mockMvc.perform(as(learner, get(ADMIN + "/report"))).andExpect(status().isForbidden());
        String body = mockMvc.perform(as(admin, get(ADMIN + "/report").param("days", "7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(7))
                .andReturn().getResponse().getContentAsString();

        assertThat(new BigDecimal(JsonPath.read(body, "$.data.topUps").toString())).isGreaterThanOrEqualTo(BigDecimal.TEN);
    }
}
