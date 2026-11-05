package com.course_aura_hub.gift.service;

import com.course_aura_hub.auth.security.TokenHasher;
import com.course_aura_hub.common.email.EmailDeliveryException;
import com.course_aura_hub.common.email.EmailMessage;
import com.course_aura_hub.common.email.EmailSender;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.enrollment.dto.EnrollmentCreateRequest;
import com.course_aura_hub.enrollment.service.EnrollmentService;
import com.course_aura_hub.gift.dto.GiftDtos;
import com.course_aura_hub.gift.model.AccessCode;
import com.course_aura_hub.gift.model.AccessCodeRedemption;
import com.course_aura_hub.gift.repository.AccessCodeRedemptionRepository;
import com.course_aura_hub.gift.repository.AccessCodeRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.order.dto.OrderResponse;
import com.course_aura_hub.order.facade.CheckoutFacade;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.repository.OrderRepository;
import com.course_aura_hub.order.service.GiftOrderOpened;
import com.course_aura_hub.order.service.GiftOrderPaid;
import com.course_aura_hub.order.service.GiftOrderRefunded;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftServiceImpl implements GiftService {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HASH_PURPOSE = "access-code";
    private static final Duration GIFT_VALIDITY = Duration.ofDays(365);
    private static final String PENDING_HINT = "----";

    private final AccessCodeRepository codeRepository;
    private final AccessCodeRedemptionRepository redemptionRepository;
    private final CourseRepository courseRepository;
    private final OrderRepository orderRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final EnrollmentService enrollmentService;
    private final LearnerAccountService learnerAccountService;
    private final NotificationService notificationService;
    private final CheckoutFacade checkoutFacade;
    private final EmailSender emailSender;
    private final TokenHasher tokenHasher;

    @Override
    public OrderResponse sendGift(String email, GiftDtos.GiftRequest request) {
        String recipient = request.recipientEmail().trim().toLowerCase(Locale.ROOT);
        if (recipient.equalsIgnoreCase(email)) {
            throw new BusinessRuleException("Gifts are for someone else. Buy the course for yourself instead");
        }
        return checkoutFacade.checkoutGift(email, request.courseSlug(), request.paymentMethod(), recipient,
                StringUtils.hasText(request.message()) ? request.message().trim() : null);
    }

    /** Records the gift before payment; its code exists only once the order is paid. */
    @EventListener
    @Transactional
    public void onGiftOpened(GiftOrderOpened event) {
        Order order = orderRepository.findById(event.orderId()).orElseThrow();
        codeRepository.save(AccessCode.builder()
                // A placeholder until payment: unique, and no code can ever hash to it
                .codeHash("pending:" + order.getId())
                .codeHint(PENDING_HINT)
                .course(order.getCourse())
                .maxUses(1)
                .uses(0)
                .revoked(true)
                .giftOrderId(order.getId())
                .purchaserStudentId(order.getStudent().getId())
                .recipientEmail(event.recipientEmail())
                .message(event.message())
                .build());
    }

    @EventListener
    @Transactional
    public void onGiftPaid(GiftOrderPaid event) {
        codeRepository.findByGiftOrderId(event.orderId()).ifPresent(code -> {
            String raw = newCode();
            code.setCodeHash(hash(raw));
            code.setCodeHint(raw.substring(raw.length() - 4));
            code.setRevoked(false);
            code.setExpiresAt(Instant.now().plus(GIFT_VALIDITY));
            Order order = orderRepository.findById(event.orderId()).orElseThrow();
            String from = order.getStudent().getName();
            String course = code.getCourse().getTitle();
            String recipient = code.getRecipientEmail();
            String message = code.getMessage();
            afterCommit(() -> sendGiftEmail(recipient, from, course, message, raw));
            notificationService.notifyStudent(order.getStudent().getId(), NotificationType.ENROLLED, "Gift sent",
                    "Your gift of %s is on its way to %s.".formatted(course, recipient), "/gifts");
            log.info("Gift code issued codeId={} order={}", code.getId(), order.getOrderNumber());
        });
    }

    @EventListener
    @Transactional
    public void onGiftRefunded(GiftOrderRefunded event) {
        codeRepository.findByGiftOrderId(event.orderId())
                .filter(code -> code.getUses() == 0)
                .ifPresent(code -> {
                    code.setRevoked(true);
                    log.info("Unused gift code revoked after refund codeId={}", code.getId());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<GiftDtos.SentGift> findSent(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .map(student -> codeRepository.findByPurchaserStudentIdOrderByCreatedAtDescIdDesc(student.getId()).stream()
                        .map(code -> new GiftDtos.SentGift(code.getId(), code.getRecipientEmail(), code.getCourse().getSlug(),
                                code.getCourse().getTitle(), status(code), code.getCreatedAt()))
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional
    public GiftDtos.Redeemed redeem(String email, String rawCode) {
        String normalized = normalize(rawCode);
        // One message for every failure, so codes can't be probed
        BusinessRuleException invalid = new BusinessRuleException("This code is not valid");
        if (normalized == null) {
            throw invalid;
        }
        AccessCode code = codeRepository.findByHashForUpdate(hash(normalized)).orElseThrow(() -> invalid);
        if (code.isRevoked() || (code.getExpiresAt() != null && code.getExpiresAt().isBefore(Instant.now()))
                || code.getUses() >= code.getMaxUses()) {
            throw invalid;
        }
        Course course = code.getCourse();
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleException("This course is not available right now");
        }
        Student student = learnerAccountService.currentStudent(email);
        if (studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new DuplicateResourceException("You already have this course");
        }
        enrollmentService.create(new EnrollmentCreateRequest(student.getId(), course.getId(), BigDecimal.ZERO, null));
        code.setUses(code.getUses() + 1);
        redemptionRepository.save(AccessCodeRedemption.builder()
                .id(new AccessCodeRedemption.Id(code.getId(), student.getId())).build());
        notificationService.notifyStudent(student.getId(), NotificationType.ENROLLED, "You're enrolled",
                "Your code unlocked %s.".formatted(course.getTitle()), "/learn/" + course.getSlug());
        log.info("Access code redeemed codeId={} studentId={}", code.getId(), student.getId());
        return new GiftDtos.Redeemed(course.getSlug(), course.getTitle());
    }

    @Override
    @Transactional
    public GiftDtos.IssuedBatch issue(UUID adminId, GiftDtos.IssueRequest request) {
        Course course = courseRepository.findBySlug(request.courseSlug())
                .orElseThrow(() -> ResourceNotFoundException.of("Course", request.courseSlug()));
        if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Expiry must be in the future");
        }
        String batch = StringUtils.hasText(request.batch()) ? request.batch().trim()
                : "batch-" + Instant.now().toString().substring(0, 10) + "-" + newCode().substring(0, 4).toLowerCase(Locale.ROOT);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < request.quantity(); i++) {
            String raw = newCode();
            codeRepository.save(AccessCode.builder()
                    .codeHash(hash(raw))
                    .codeHint(raw.substring(raw.length() - 4))
                    .course(course)
                    .batch(batch)
                    .maxUses(request.maxUses() == null ? 1 : request.maxUses())
                    .uses(0)
                    .expiresAt(request.expiresAt())
                    .createdBy(adminId)
                    .build());
            codes.add(raw);
        }
        log.info("Issued {} access codes batch={} courseId={}", codes.size(), batch, course.getId());
        return new GiftDtos.IssuedBatch(batch, course.getSlug(), codes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GiftDtos.Code> findCodes(String batch) {
        List<AccessCode> codes = StringUtils.hasText(batch)
                ? codeRepository.findByBatchOrderByIdAsc(batch.trim())
                : codeRepository.findByGiftOrderIdIsNullOrderByCreatedAtDescIdDesc();
        return codes.stream().map(this::toCode).toList();
    }

    @Override
    @Transactional
    public GiftDtos.Code revoke(Integer id) {
        AccessCode code = codeRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Access code", id));
        code.setRevoked(true);
        log.info("Access code revoked codeId={}", id);
        return toCode(code);
    }

    private GiftDtos.Code toCode(AccessCode code) {
        return new GiftDtos.Code(code.getId(), code.getCodeHint(), code.getCourse().getSlug(), code.getCourse().getTitle(),
                code.getBatch(), code.getUses(), code.getMaxUses(), code.getExpiresAt(), code.isRevoked(), status(code),
                code.getCreatedAt());
    }

    private static String status(AccessCode code) {
        if (PENDING_HINT.equals(code.getCodeHint())) return "awaiting_payment";
        if (code.getUses() >= code.getMaxUses()) return code.getGiftOrderId() != null ? "redeemed" : "used_up";
        if (code.isRevoked()) return "revoked";
        if (code.getExpiresAt() != null && code.getExpiresAt().isBefore(Instant.now())) return "expired";
        return "active";
    }

    private void sendGiftEmail(String to, String from, String course, String message, String code) {
        String text = from + " gave you the course \"" + course + "\" on CourseAuraHub.\n\n"
                + (message == null ? "" : "\"" + message + "\"\n\n")
                + "Sign in or create an account, then redeem this code:\n\n" + code + "\n\nThe code works for one year.";
        String html = "<p>" + HtmlUtils.htmlEscape(from) + " gave you the course <strong>" + HtmlUtils.htmlEscape(course)
                + "</strong> on CourseAuraHub.</p>"
                + (message == null ? "" : "<blockquote>" + HtmlUtils.htmlEscape(message) + "</blockquote>")
                + "<p>Sign in or create an account, then redeem this code:</p><p style=\"font-size:22px;font-weight:bold;letter-spacing:2px\">"
                + code + "</p><p>The code works for one year.</p>";
        try {
            emailSender.send(new EmailMessage(to, from + " sent you a course", text, html));
        } catch (EmailDeliveryException ex) {
            log.warn("Gift email not delivered");
        }
    }

    /** 16 random characters in four groups, about 80 bits, so guessing a code is impractical. */
    private static String newCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Upper case, spaces and dashes ignored, and the letters people confuse with digits mapped like Crockford base32. */
    static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String compact = input.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "")
                .replace('O', '0').replace('I', '1').replace('L', '1');
        if (!compact.matches("[0-9A-HJKMNP-TV-Z]{16}")) {
            return null;
        }
        return compact.substring(0, 4) + "-" + compact.substring(4, 8) + "-" + compact.substring(8, 12) + "-" + compact.substring(12);
    }

    private String hash(String normalizedCode) {
        return tokenHasher.hash(HASH_PURPOSE, normalizedCode);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
