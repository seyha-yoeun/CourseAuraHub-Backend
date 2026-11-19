package com.course_aura_hub.subscription.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.enrollment.dto.EnrollmentCreateRequest;
import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import com.course_aura_hub.enrollment.service.EnrollmentService;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.membercard.service.MemberCardService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.order.payment.PaymentEvent;
import com.course_aura_hub.order.payment.PaymentMethod;
import com.course_aura_hub.order.payment.PaymentSession;
import com.course_aura_hub.prerequisite.service.PrerequisiteService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.course_aura_hub.student.repository.StudentRepository;
import com.course_aura_hub.subscription.config.SubscriptionProperties;
import com.course_aura_hub.subscription.dto.SubscriptionDtos;
import com.course_aura_hub.subscription.model.BillingInterval;
import com.course_aura_hub.subscription.model.Subscription;
import com.course_aura_hub.subscription.model.SubscriptionPayment;
import com.course_aura_hub.subscription.model.SubscriptionPaymentStatus;
import com.course_aura_hub.subscription.model.SubscriptionPlan;
import com.course_aura_hub.subscription.model.SubscriptionStatus;
import com.course_aura_hub.subscription.repository.SubscriptionPaymentRepository;
import com.course_aura_hub.subscription.repository.SubscriptionPlanRepository;
import com.course_aura_hub.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    public static final String PAYMENT_PREFIX = "SUB-";
    private static final Set<SubscriptionStatus> LIVE =
            Set.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_PAGE_SIZE = 100;
    private static final String LINK = "/subscription";

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final LearnerAccountService learnerAccountService;
    private final StudentRepository studentRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollmentRepository;
    private final PrerequisiteService prerequisiteService;
    private final MemberCardService memberCardService;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;
    private final SubscriptionProperties properties;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDtos.Plan> activePlans() {
        return planRepository.findByActiveTrueOrderByPriceAsc().stream().map(this::toPlan).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDtos.Plan> allPlans() {
        return planRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toPlan).toList();
    }

    @Override
    @Transactional
    public SubscriptionDtos.Plan createPlan(SubscriptionDtos.PlanRequest request) {
        if (planRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("A plan with this code already exists");
        }
        SubscriptionPlan plan = SubscriptionPlan.builder().code(request.code()).build();
        applyPlan(plan, request);
        return toPlan(planRepository.save(plan));
    }

    @Override
    @Transactional
    public SubscriptionDtos.Plan updatePlan(Integer id, SubscriptionDtos.PlanRequest request) {
        SubscriptionPlan plan = planRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Plan", id));
        applyPlan(plan, request);
        return toPlan(plan);
    }

    @Override
    @Transactional
    public SubscriptionDtos.OpenPayment open(String email, String planCode, String provider) {
        if (!properties.enabled()) {
            throw new ServiceUnavailableException("Subscriptions are not available right now");
        }
        // Locking the learner serializes parallel sign-ups; the partial unique index is the backstop
        Integer studentId = learnerAccountService.currentStudent(email).getId();
        studentRepository.findByIdForUpdate(studentId);
        SubscriptionPlan plan = planRepository.findByCode(planCode.trim())
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> ResourceNotFoundException.of("Plan", planCode));

        Optional<Subscription> live = subscriptionRepository.findFirstByStudentIdAndStatusIn(studentId, LIVE);
        if (live.isPresent()) {
            Subscription existing = live.get();
            if (existing.getStatus() != SubscriptionStatus.PENDING) {
                throw new DuplicateResourceException("You already have a subscription");
            }
            // An unpaid sign-up is replaced by the new one
            cancelPending(existing);
            subscriptionRepository.flush();
        }

        Instant start = Instant.now();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .studentId(studentId)
                .plan(plan)
                .status(SubscriptionStatus.PENDING)
                .provider(provider)
                .build());
        SubscriptionPayment payment = paymentRepository.save(newPayment(subscription, start));
        return toOpen(payment);
    }

    @Override
    @Transactional
    public void payWithCard(String paymentNumber) {
        SubscriptionPayment payment = lockPayment(paymentNumber);
        if (payment.getStatus() == SubscriptionPaymentStatus.PAID) {
            return;
        }
        if (payment.getStatus() != SubscriptionPaymentStatus.PENDING) {
            throw new BusinessRuleException("This payment can no longer be made");
        }
        memberCardService.charge(payment.getSubscription().getStudentId(), payment.getAmount(),
                payment.getPaymentNumber(), "Subscription: " + payment.getSubscription().getPlan().getName());
        settle(payment);
    }

    @Override
    @Transactional
    public void attachSession(String paymentNumber, PaymentSession session) {
        SubscriptionPayment payment = lockPayment(paymentNumber);
        payment.setProviderReference(session.reference());
        // A webhook may already have settled it while the provider call was in flight
        if (payment.getStatus() == SubscriptionPaymentStatus.PENDING) {
            payment.setCheckoutUrl(session.checkoutUrl());
        }
    }

    @Override
    @Transactional
    public void markFailed(String paymentNumber) {
        SubscriptionPayment payment = lockPayment(paymentNumber);
        if (payment.getStatus() == SubscriptionPaymentStatus.PENDING) {
            payment.setStatus(SubscriptionPaymentStatus.FAILED);
            payment.setCheckoutUrl(null);
            Subscription subscription = payment.getSubscription();
            if (subscription.getStatus() == SubscriptionStatus.PENDING) {
                subscription.setStatus(SubscriptionStatus.CANCELLED);
                subscription.setCancelledAt(Instant.now());
            }
        }
    }

    @Override
    @Transactional
    public void applyPaymentEvent(PaymentEvent event) {
        SubscriptionPayment payment = lockPayment(event.orderNumber());
        if (event.outcome() == PaymentEvent.Outcome.FAILED) {
            if (payment.getStatus() == SubscriptionPaymentStatus.PENDING) {
                payment.setStatus(SubscriptionPaymentStatus.FAILED);
                payment.setCheckoutUrl(null);
                notifyLearner(payment.getSubscription(), "Subscription payment failed",
                        "Your payment for " + payment.getSubscription().getPlan().getName() + " didn't go through.");
            }
            return;
        }
        if (payment.getStatus() == SubscriptionPaymentStatus.PAID) {
            return;
        }
        if (payment.getAmount().compareTo(event.amount()) != 0 || !payment.getCurrency().equals(event.currency())) {
            log.error("Payment mismatch for subscription payment={}", payment.getPaymentNumber());
            throw new BusinessRuleException("Payment does not match the subscription");
        }
        // Money was taken, so a late payment still counts
        settle(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDtos.MySubscription mine(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> subscriptionRepository.findFirstByStudentIdOrderByCreatedAtDescIdDesc(student.getId()))
                .map(this::toMine)
                .orElse(null);
    }

    @Override
    @Transactional
    public SubscriptionDtos.MySubscription cancel(String email) {
        Subscription subscription = lockLive(email);
        if (subscription.getStatus() == SubscriptionStatus.PENDING) {
            cancelPending(subscription);
        } else {
            subscription.setCancelAtPeriodEnd(true);
            subscription.setCancelledAt(Instant.now());
        }
        return toMine(subscription);
    }

    @Override
    @Transactional
    public SubscriptionDtos.MySubscription resume(String email) {
        Subscription subscription = lockLive(email);
        if (!subscription.grantsAccess()) {
            throw new BusinessRuleException("Only an active subscription can be resumed");
        }
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(null);
        return toMine(subscription);
    }

    @Override
    @Transactional
    public void enroll(String email, String courseSlug) {
        Student student = learnerAccountService.currentStudent(email);
        Subscription subscription = subscriptionRepository.findFirstByStudentIdAndStatusIn(student.getId(), LIVE)
                .filter(Subscription::grantsAccess)
                .orElseThrow(() -> new BusinessRuleException("You need an active subscription"));
        Course course = courseRepository.findBySlug(courseSlug.trim())
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        if (studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new DuplicateResourceException("You are already enrolled in this course");
        }
        prerequisiteService.requireMet(student.getId(), course.getId());
        // Free to the learner: no revenue and no spending, unlike a purchase
        enrollmentService.create(new EnrollmentCreateRequest(student.getId(), course.getId(), BigDecimal.ZERO, null));
        jdbcTemplate.update("""
                insert into subscription_enrollments (subscription_id, student_id, course_id) values (?, ?, ?)
                on conflict (student_id, course_id) do update set subscription_id = excluded.subscription_id
                """, subscription.getId(), student.getId(), course.getId());
        log.info("Subscriber enrolled studentId={} courseId={} subscriptionId={}", student.getId(), course.getId(),
                subscription.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDtos.AdminPage findForAdmin(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Subscription> subscriptions = StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())
                ? subscriptionRepository.findByStatus(parseStatus(status), pageable)
                : subscriptionRepository.findAll(pageable);
        return new SubscriptionDtos.AdminPage(subscriptions.map(this::toAdmin).getContent(), subscriptions.getNumber(),
                subscriptions.getSize(), subscriptions.getTotalElements(), subscriptions.getTotalPages());
    }

    @Override
    @Transactional
    public int expireDue() {
        Instant now = Instant.now();
        int ended = 0;
        for (Integer id : subscriptionRepository.findIdsEndedBefore(SubscriptionStatus.PAST_DUE,
                now.minus(Duration.ofDays(properties.graceDays())))) {
            ended += end(id, "Your subscription has ended because the renewal wasn't paid.") ? 1 : 0;
        }
        for (Integer id : subscriptionRepository.findIdsEndedBefore(SubscriptionStatus.ACTIVE, now)) {
            Subscription subscription = subscriptionRepository.findById(id).orElse(null);
            if (subscription != null && subscription.isCancelAtPeriodEnd()) {
                ended += end(id, "Your subscription has ended, as you asked.") ? 1 : 0;
            }
        }
        for (Integer id : subscriptionRepository.findIdsCreatedBefore(SubscriptionStatus.PENDING,
                now.minus(Duration.ofHours(properties.pendingTimeoutHours())))) {
            subscriptionRepository.findByIdForUpdate(id)
                    .filter(subscription -> subscription.getStatus() == SubscriptionStatus.PENDING)
                    .ifPresent(this::cancelPending);
        }
        return ended;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> findDueRenewals() {
        return subscriptionRepository.findIdsEndedBefore(SubscriptionStatus.ACTIVE, Instant.now()).stream()
                .filter(id -> subscriptionRepository.findById(id).map(found -> !found.isCancelAtPeriodEnd()).orElse(false))
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionDtos.OpenPayment openRenewal(Integer subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.ACTIVE
                || subscription.isCancelAtPeriodEnd() || subscription.getCurrentPeriodEnd().isAfter(Instant.now())) {
            return null;
        }
        // Access continues through the grace period while the renewal is paid
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        SubscriptionPayment payment = paymentRepository.save(newPayment(subscription, subscription.getCurrentPeriodEnd()));
        return toOpen(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDtos.OpenPayment findOpenPayment(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> subscriptionRepository.findFirstByStudentIdAndStatusIn(student.getId(), LIVE))
                .flatMap(subscription -> paymentRepository
                        .findBySubscriptionIdAndStatus(subscription.getId(), SubscriptionPaymentStatus.PENDING).stream()
                        .findFirst())
                .map(this::toOpen)
                .orElse(null);
    }

    @Override
    @Transactional
    public void notifyPaymentNeeded(String paymentNumber) {
        SubscriptionPayment payment = lockPayment(paymentNumber);
        if (payment.getStatus() == SubscriptionPaymentStatus.PENDING) {
            notifyLearner(payment.getSubscription(), "Subscription renewal due",
                    "Pay %s %s within %d days to keep your access.".formatted(payment.getAmount().toPlainString(),
                            payment.getCurrency(), properties.graceDays()));
        }
    }

    /** Payment received: the subscription is active for the payment's period. */
    private void settle(SubscriptionPayment payment) {
        payment.setStatus(SubscriptionPaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setCheckoutUrl(null);
        Subscription subscription = payment.getSubscription();
        if (!LIVE.contains(subscription.getStatus())) {
            log.warn("Subscription payment={} paid after its subscription ended; review for refund",
                    payment.getPaymentNumber());
            return;
        }
        boolean first = subscription.getStatus() == SubscriptionStatus.PENDING;
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(payment.getPeriodStart());
        subscription.setCurrentPeriodEnd(payment.getPeriodEnd());
        notifyLearner(subscription, first ? "Subscription active" : "Subscription renewed",
                "%s is active until %s (UTC).".formatted(subscription.getPlan().getName(),
                        payment.getPeriodEnd().toString().substring(0, 10)));
        log.info("Subscription payment settled payment={} subscriptionId={}", payment.getPaymentNumber(), subscription.getId());
    }

    /** Ends a subscription and removes the enrollments it gave. Progress is kept for a later return. */
    private boolean end(Integer id, String message) {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(id).orElse(null);
        if (subscription == null || !subscription.grantsAccess()) {
            return false;
        }
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        paymentRepository.findBySubscriptionIdAndStatus(id, SubscriptionPaymentStatus.PENDING).forEach(payment -> {
            payment.setStatus(SubscriptionPaymentStatus.CANCELLED);
            payment.setCheckoutUrl(null);
        });
        List<Integer> courseIds = jdbcTemplate.queryForList(
                "select course_id from subscription_enrollments where subscription_id = ?", Integer.class, id);
        for (Integer courseId : courseIds) {
            enrollmentRepository.findByStudentIdAndCourseId(subscription.getStudentId(), courseId)
                    .ifPresent(enrollment -> enrollmentService.delete(enrollment.getId()));
        }
        jdbcTemplate.update("delete from subscription_enrollments where subscription_id = ?", id);
        notifyLearner(subscription, "Subscription ended", message);
        log.info("Subscription ended id={} coursesRemoved={}", id, courseIds.size());
        return true;
    }

    private void cancelPending(Subscription subscription) {
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        paymentRepository.findBySubscriptionIdAndStatus(subscription.getId(), SubscriptionPaymentStatus.PENDING)
                .forEach(payment -> {
                    payment.setStatus(SubscriptionPaymentStatus.CANCELLED);
                    payment.setCheckoutUrl(null);
                });
    }

    private SubscriptionPayment newPayment(Subscription subscription, Instant start) {
        SubscriptionPlan plan = subscription.getPlan();
        ZonedDateTime from = start.atZone(ZoneOffset.UTC);
        Instant end = (plan.getBillingInterval() == BillingInterval.YEAR ? from.plusYears(1) : from.plusMonths(1)).toInstant();
        return SubscriptionPayment.builder()
                .paymentNumber(PAYMENT_PREFIX + newNumber())
                .subscription(subscription)
                .amount(plan.getPrice())
                .currency(paymentProperties.currency())
                .status(SubscriptionPaymentStatus.PENDING)
                .periodStart(start)
                .periodEnd(end)
                .provider(subscription.getProvider())
                .build();
    }

    private Subscription lockLive(String email) {
        Integer studentId = learnerAccountService.findCurrentStudent(email).map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("You have no subscription"));
        Integer id = subscriptionRepository.findFirstByStudentIdAndStatusIn(studentId, LIVE).map(Subscription::getId)
                .orElseThrow(() -> new ResourceNotFoundException("You have no subscription"));
        return subscriptionRepository.findByIdForUpdate(id).orElseThrow();
    }

    private SubscriptionPayment lockPayment(String number) {
        return paymentRepository.findByNumberForUpdate(number)
                .orElseThrow(() -> ResourceNotFoundException.of("Subscription payment", number));
    }

    private void notifyLearner(Subscription subscription, String title, String message) {
        notificationService.notifyStudent(subscription.getStudentId(), NotificationType.SUBSCRIPTION, title, message, LINK);
    }

    private void applyPlan(SubscriptionPlan plan, SubscriptionDtos.PlanRequest request) {
        try {
            plan.setBillingInterval(BillingInterval.valueOf(request.billingInterval().trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("billingInterval must be month or year");
        }
        plan.setName(request.name().trim());
        plan.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        plan.setPrice(request.price());
        if (request.active() != null) {
            plan.setActive(request.active());
        }
    }

    private SubscriptionDtos.Plan toPlan(SubscriptionPlan plan) {
        return new SubscriptionDtos.Plan(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(),
                plan.getPrice(), paymentProperties.currency(), plan.getBillingInterval().name().toLowerCase(Locale.ROOT),
                plan.isActive());
    }

    private SubscriptionDtos.MySubscription toMine(Subscription subscription) {
        List<SubscriptionDtos.Payment> payments = paymentRepository
                .findTop12BySubscriptionIdOrderByCreatedAtDescIdDesc(subscription.getId()).stream()
                .map(payment -> new SubscriptionDtos.Payment(payment.getPaymentNumber(), payment.getAmount(),
                        payment.getCurrency(), payment.getStatus().name().toLowerCase(Locale.ROOT), payment.getPeriodStart(),
                        payment.getPeriodEnd(), payment.getCheckoutUrl(), payment.getCreatedAt(), payment.getPaidAt()))
                .toList();
        String checkoutUrl = payments.stream().filter(payment -> "pending".equals(payment.status()))
                .map(SubscriptionDtos.Payment::checkoutUrl).filter(url -> url != null).findFirst().orElse(null);
        Integer courses = jdbcTemplate.queryForObject(
                "select count(*) from subscription_enrollments where subscription_id = ?", Integer.class, subscription.getId());
        return new SubscriptionDtos.MySubscription(subscription.getId(), toPlan(subscription.getPlan()),
                subscription.getStatus().name().toLowerCase(Locale.ROOT), subscription.grantsAccess(),
                subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(),
                PaymentMethod.MEMBER_CARD_PROVIDER.equals(subscription.getProvider()) ? "member_card" : "online",
                checkoutUrl, courses == null ? 0 : courses, payments);
    }

    private SubscriptionDtos.AdminSubscription toAdmin(Subscription subscription) {
        Student student = studentRepository.findById(subscription.getStudentId()).orElse(null);
        return new SubscriptionDtos.AdminSubscription(subscription.getId(), subscription.getStudentId(),
                student == null ? null : student.getName(), student == null ? null : student.getEmail(),
                subscription.getPlan().getCode(), subscription.getStatus().name().toLowerCase(Locale.ROOT),
                subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(), subscription.getCreatedAt());
    }

    private SubscriptionDtos.OpenPayment toOpen(SubscriptionPayment payment) {
        return new SubscriptionDtos.OpenPayment(payment.getSubscription().getId(), payment.getPaymentNumber(),
                payment.getAmount(), payment.getCurrency(), payment.getProvider());
    }

    private static SubscriptionStatus parseStatus(String value) {
        try {
            return SubscriptionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown status: " + value);
        }
    }

    private static String newNumber() {
        char[] out = new char[16];
        for (int i = 0; i < out.length; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
