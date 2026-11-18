package com.course_aura_hub.referral.service;

import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.membercard.repository.MemberCardRepository;
import com.course_aura_hub.membercard.service.MemberCardService;
import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.model.OrderStatus;
import com.course_aura_hub.order.repository.OrderRepository;
import com.course_aura_hub.order.service.OrderPaid;
import com.course_aura_hub.order.service.OrderRefunded;
import com.course_aura_hub.referral.config.ReferralProperties;
import com.course_aura_hub.referral.dto.ReferralDtos;
import com.course_aura_hub.referral.model.Referral;
import com.course_aura_hub.referral.model.ReferralCode;
import com.course_aura_hub.referral.model.ReferralStatus;
import com.course_aura_hub.referral.repository.ReferralCodeRepository;
import com.course_aura_hub.referral.repository.ReferralRepository;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class ReferralServiceImpl implements ReferralService {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_PAGE_SIZE = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReferralRepository referralRepository;
    private final ReferralCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final OrderRepository orderRepository;
    private final MemberCardRepository cardRepository;
    private final MemberCardService memberCardService;
    private final JdbcTemplate jdbcTemplate;
    private final ReferralProperties properties;
    private final PaymentProperties paymentProperties;
    private final TransactionTemplate newTransaction;

    public ReferralServiceImpl(ReferralRepository referralRepository, ReferralCodeRepository codeRepository,
                               UserRepository userRepository, StudentRepository studentRepository,
                               OrderRepository orderRepository, MemberCardRepository cardRepository,
                               MemberCardService memberCardService, JdbcTemplate jdbcTemplate,
                               ReferralProperties properties, PaymentProperties paymentProperties,
                               PlatformTransactionManager transactionManager) {
        this.referralRepository = referralRepository;
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.orderRepository = orderRepository;
        this.cardRepository = cardRepository;
        this.memberCardService = memberCardService;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.paymentProperties = paymentProperties;
        // Each reward commits on its own, so one failure doesn't undo the others
        this.newTransaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public ReferralDtos.MyReferral mine(UUID userId) {
        ReferralCode code = codeRepository.findById(userId).orElseGet(() -> codeRepository.save(
                ReferralCode.builder().userId(userId).code(newCode()).build()));
        List<Referral> referrals = referralRepository.findByReferrerUserIdOrderByCreatedAtDescIdDesc(userId);
        BigDecimal earned = referrals.stream()
                .filter(referral -> referral.getStatus() == ReferralStatus.REWARDED && referral.getRewardAmount() != null)
                .map(Referral::getRewardAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ReferralDtos.ReferredFriend> friends = referrals.stream()
                .map(referral -> new ReferralDtos.ReferredFriend(
                        userRepository.findById(referral.getRefereeUserId()).map(User::getFirstName).orElse("A friend"),
                        lower(referral.getStatus()),
                        referral.getCreatedAt(),
                        referral.getRewardedAt()))
                .toList();
        return new ReferralDtos.MyReferral(
                code.getCode(),
                properties.linkBaseUrl() + "?ref=" + code.getCode(),
                properties.rewardAmount(),
                paymentProperties.currency(),
                count(referrals, ReferralStatus.SIGNED_UP),
                count(referrals, ReferralStatus.QUALIFIED),
                count(referrals, ReferralStatus.REWARDED),
                earned,
                friends);
    }

    @Override
    @Transactional
    public ReferralDtos.ClaimResponse claim(UUID userId, String rawCode) {
        if (!properties.enabled()) {
            throw new ServiceUnavailableException("Referrals are not available right now");
        }
        ReferralCode code = codeRepository.findByCode(rawCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("That referral code doesn't exist"));
        if (code.getUserId().equals(userId)) {
            throw new BusinessRuleException("You can't use your own referral code");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        if (user.getCreatedAt() != null
                && user.getCreatedAt().isBefore(Instant.now().minus(Duration.ofDays(properties.claimWindowDays())))) {
            throw new BusinessRuleException("Referral codes can only be used in your first "
                    + properties.claimWindowDays() + " days");
        }
        if (referralRepository.existsByRefereeUserId(userId)) {
            throw new DuplicateResourceException("You have already used a referral code");
        }
        if (hasPaidOrder(userId)) {
            throw new BusinessRuleException("Referral codes are for new learners before their first purchase");
        }
        referralRepository.save(Referral.builder()
                .referrerUserId(code.getUserId())
                .refereeUserId(userId)
                .status(ReferralStatus.SIGNED_UP)
                .build());
        String firstName = userRepository.findById(code.getUserId()).map(User::getFirstName).orElse(null);
        log.info("Referral claimed refereeId={} referrerId={}", userId, code.getUserId());
        return new ReferralDtos.ClaimResponse(firstName);
    }

    /** The first paid order qualifies the referral, inside the paying transaction. */
    @EventListener
    @Transactional
    public void onOrderPaid(OrderPaid event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null || order.getAmount().signum() <= 0 || order.getStudent().getUser() == null) {
            return;
        }
        referralRepository.findByRefereeForUpdate(order.getStudent().getUser().getId())
                .filter(referral -> referral.getStatus() == ReferralStatus.SIGNED_UP)
                .ifPresent(referral -> {
                    referral.setStatus(ReferralStatus.QUALIFIED);
                    referral.setOrderId(order.getId());
                    referral.setQualifiedAt(order.getPaidAt() == null ? Instant.now() : order.getPaidAt());
                    log.info("Referral qualified id={} order={}", referral.getId(), order.getOrderNumber());
                });
    }

    /** A refund before the reward undoes the qualification; a later paid order can qualify it again. */
    @EventListener
    @Transactional
    public void onOrderRefunded(OrderRefunded event) {
        referralRepository.findByOrderIdForUpdate(event.orderId())
                .filter(referral -> referral.getStatus() == ReferralStatus.QUALIFIED)
                .ifPresent(referral -> {
                    referral.setStatus(ReferralStatus.SIGNED_UP);
                    referral.setOrderId(null);
                    referral.setQualifiedAt(null);
                    log.info("Referral back to signed up after refund id={}", referral.getId());
                });
    }

    @Override
    public int rewardDue() {
        Instant before = Instant.now().minus(Duration.ofDays(properties.holdDays()));
        int rewarded = 0;
        for (Integer id : referralRepository.findDueIds(before)) {
            try {
                Boolean done = newTransaction.execute(status -> rewardOne(id));
                if (Boolean.TRUE.equals(done)) {
                    rewarded++;
                }
            } catch (RuntimeException ex) {
                log.warn("Referral reward failed id={}: {}", id, ex.getMessage());
            }
        }
        return rewarded;
    }

    /** @return whether the referrer was credited; without a member card the referral waits for the next run */
    private boolean rewardOne(Integer id) {
        Referral referral = referralRepository.findByIdForUpdate(id).orElse(null);
        if (referral == null || referral.getStatus() != ReferralStatus.QUALIFIED) {
            return false;
        }
        boolean stillPaid = referral.getOrderId() != null && orderRepository.findById(referral.getOrderId())
                .map(order -> order.getStatus() == OrderStatus.PAID)
                .orElse(false);
        if (!stillPaid) {
            referral.setStatus(ReferralStatus.SIGNED_UP);
            referral.setOrderId(null);
            referral.setQualifiedAt(null);
            return false;
        }
        Integer cardId = studentRepository.findByUserId(referral.getReferrerUserId())
                .flatMap(student -> cardRepository.findByStudentId(student.getId()))
                .map(card -> card.getId())
                .orElse(null);
        if (cardId == null) {
            return false;
        }
        BigDecimal amount = properties.rewardAmount();
        memberCardService.creditTopUp(cardId, null, amount, "REFERRAL-" + referral.getId(), "Referral reward");
        referral.setStatus(ReferralStatus.REWARDED);
        referral.setRewardAmount(amount);
        referral.setRewardedAt(Instant.now());
        log.info("Referral rewarded id={} amount={}", referral.getId(), amount);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public ReferralDtos.AdminPage findForAdmin(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Referral> referrals = StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())
                ? referralRepository.findByStatus(parseStatus(status), pageable)
                : referralRepository.findAll(pageable);
        return new ReferralDtos.AdminPage(referrals.map(this::toAdmin).getContent(), referrals.getNumber(),
                referrals.getSize(), referrals.getTotalElements(), referrals.getTotalPages());
    }

    private ReferralDtos.AdminReferral toAdmin(Referral referral) {
        User referrer = userRepository.findById(referral.getReferrerUserId()).orElse(null);
        User referee = userRepository.findById(referral.getRefereeUserId()).orElse(null);
        String orderNumber = referral.getOrderId() == null ? null
                : orderRepository.findById(referral.getOrderId()).map(Order::getOrderNumber).orElse(null);
        return new ReferralDtos.AdminReferral(referral.getId(), name(referrer), referrer == null ? null : referrer.getEmail(),
                name(referee), referee == null ? null : referee.getEmail(), lower(referral.getStatus()), orderNumber,
                referral.getRewardAmount(), referral.getCreatedAt(), referral.getQualifiedAt(), referral.getRewardedAt());
    }

    private boolean hasPaidOrder(UUID userId) {
        Integer paid = jdbcTemplate.queryForObject("""
                select count(*) from orders o join students s on s.id = o.student_id
                where s.user_id = ? and o.status in ('PAID', 'REFUNDED') and o.amount > 0
                """, Integer.class, userId);
        return paid != null && paid > 0;
    }

    private String newCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            if (codeRepository.findByCode(code.toString()).isEmpty()) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Could not generate a unique referral code");
    }

    private static long count(List<Referral> referrals, ReferralStatus status) {
        return referrals.stream().filter(referral -> referral.getStatus() == status).count();
    }

    private static String name(User user) {
        return user == null ? null : (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static String lower(ReferralStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static ReferralStatus parseStatus(String value) {
        try {
            return ReferralStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown status: " + value);
        }
    }
}
