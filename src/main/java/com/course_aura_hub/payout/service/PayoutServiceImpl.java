package com.course_aura_hub.payout.service;

import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.payout.config.PayoutProperties;
import com.course_aura_hub.payout.dto.PayoutDtos;
import com.course_aura_hub.payout.model.CourseRevenueShare;
import com.course_aura_hub.payout.model.EarningStatus;
import com.course_aura_hub.payout.model.InstructorEarning;
import com.course_aura_hub.payout.model.PayoutRequest;
import com.course_aura_hub.payout.model.PayoutStatus;
import com.course_aura_hub.payout.repository.CourseRevenueShareRepository;
import com.course_aura_hub.payout.repository.InstructorEarningRepository;
import com.course_aura_hub.payout.repository.PayoutRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<PayoutStatus> OPEN = Set.of(PayoutStatus.REQUESTED, PayoutStatus.APPROVED);
    private static final String INSTRUCTOR_LINK = "/instructor/earnings";
    private static final String ADMIN_LINK = "/admin/payouts";

    private final InstructorEarningRepository earningRepository;
    private final PayoutRequestRepository payoutRepository;
    private final CourseRevenueShareRepository shareRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PayoutProperties properties;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional(readOnly = true)
    public PayoutDtos.EarningsPage findEarnings(Actor actor, String status, int page, int size) {
        UUID instructorId = actor.userId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<InstructorEarning> earnings = StringUtils.hasText(status)
                ? earningRepository.findByInstructorIdAndStatus(instructorId, parse(EarningStatus.class, status), pageable)
                : earningRepository.findByInstructorId(instructorId, pageable);

        BigDecimal available = earningRepository.sumUnclaimed(instructorId);
        boolean canRequest = properties.enabled()
                && available.compareTo(properties.minimumAmount()) >= 0
                && !payoutRepository.existsByInstructorIdAndStatusIn(instructorId, OPEN);
        PayoutDtos.EarningsSummary summary = new PayoutDtos.EarningsSummary(
                paymentProperties.currency(),
                earningRepository.sumNet(instructorId, EarningStatus.PENDING),
                available,
                earningRepository.sumClaimed(instructorId),
                earningRepository.sumNet(instructorId, EarningStatus.PAID),
                properties.minimumAmount(),
                canRequest
        );
        return new PayoutDtos.EarningsPage(summary, earnings.map(PayoutDtos.EarningResponse::from).getContent(),
                earnings.getNumber(), earnings.getSize(), earnings.getTotalElements(), earnings.getTotalPages());
    }

    @Override
    @Transactional
    public PayoutDtos.PayoutResponse requestPayout(Actor actor, PayoutDtos.CreatePayoutRequest request) {
        if (!properties.enabled()) {
            throw new ServiceUnavailableException("Payouts are not available right now");
        }
        UUID instructorId = actor.userId();
        if (payoutRepository.existsByInstructorIdAndStatusIn(instructorId, OPEN)) {
            throw new DuplicateResourceException("You already have a payout in progress");
        }

        // Locked: a parallel request waits here, then finds these rows claimed
        List<InstructorEarning> claimable = earningRepository.findClaimableForUpdate(instructorId);
        BigDecimal total = claimable.stream().map(InstructorEarning::getNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(properties.minimumAmount()) < 0) {
            throw new BusinessRuleException("You need at least " + properties.minimumAmount() + " "
                    + paymentProperties.currency() + " available to request a payout");
        }
        Set<String> currencies = claimable.stream().map(InstructorEarning::getCurrency).collect(Collectors.toSet());
        if (currencies.size() != 1) {
            throw new BusinessRuleException("Earnings in several currencies can't be paid out together; contact support");
        }

        PayoutRequest payout = payoutRepository.save(PayoutRequest.builder()
                .instructorId(instructorId)
                .amount(total)
                .currency(currencies.iterator().next())
                .method(request.method().trim())
                .accountDetails(request.accountDetails().trim())
                .note(StringUtils.hasText(request.note()) ? request.note().trim() : null)
                .status(PayoutStatus.REQUESTED)
                .build());
        claimable.forEach(earning -> earning.setPayoutId(payout.getId()));

        notificationService.notifyAdmins(NotificationType.PAYOUT, "Payout requested",
                "An instructor asked to be paid %s %s.".formatted(total, payout.getCurrency()), ADMIN_LINK);
        log.info("Payout requested id={} instructorId={} amount={}", payout.getId(), instructorId, total);
        return PayoutDtos.PayoutResponse.from(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutDtos.PayoutResponse> findMyPayouts(Actor actor) {
        return payoutRepository.findByInstructorIdOrderByCreatedAtDescIdDesc(actor.userId()).stream()
                .map(PayoutDtos.PayoutResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutDtos.AdminPage findForAdmin(String status, int page, int size) {
        String filter = StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : "requested";
        boolean all = "all".equals(filter);
        // The queue reads oldest first; history newest first
        Sort sort = "requested".equals(filter) || "approved".equals(filter)
                ? Sort.by(Sort.Direction.ASC, "createdAt", "id")
                : Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), sort);
        Page<PayoutRequest> payouts = all
                ? payoutRepository.findAll(pageable)
                : payoutRepository.findByStatus(parse(PayoutStatus.class, filter), pageable);
        return new PayoutDtos.AdminPage(payouts.map(this::toAdminResponse).getContent(), payouts.getNumber(),
                payouts.getSize(), payouts.getTotalElements(), payouts.getTotalPages());
    }

    @Override
    @Transactional
    public PayoutDtos.PayoutResponse approve(Integer id, UUID adminId, String note) {
        PayoutRequest payout = lockForReview(id, adminId);
        if (payout.getStatus() != PayoutStatus.REQUESTED) {
            throw new BusinessRuleException("Only a requested payout can be approved");
        }
        payout.setStatus(PayoutStatus.APPROVED);
        review(payout, adminId, note);
        notifyInstructor(payout, "Payout approved",
                "Your payout of %s %s was approved and will be sent soon.".formatted(payout.getAmount(), payout.getCurrency()));
        return PayoutDtos.PayoutResponse.from(payout);
    }

    @Override
    @Transactional
    public PayoutDtos.PayoutResponse reject(Integer id, UUID adminId, String note) {
        if (!StringUtils.hasText(note)) {
            throw new BusinessRuleException("Tell the instructor why the payout was declined");
        }
        PayoutRequest payout = lockForReview(id, adminId);
        if (!OPEN.contains(payout.getStatus())) {
            throw new BusinessRuleException("This payout has already been decided");
        }
        payout.setStatus(PayoutStatus.REJECTED);
        review(payout, adminId, note);
        earningRepository.findByPayoutId(payout.getId()).forEach(earning -> earning.setPayoutId(null));
        notifyInstructor(payout, "Payout declined", "Your payout request was declined: " + note.trim());
        return PayoutDtos.PayoutResponse.from(payout);
    }

    @Override
    @Transactional
    public PayoutDtos.PayoutResponse markPaid(Integer id, UUID adminId, String paymentReference, String note) {
        PayoutRequest payout = lockForReview(id, adminId);
        if (payout.getStatus() != PayoutStatus.APPROVED) {
            throw new BusinessRuleException("Approve the payout before marking it paid");
        }
        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidAt(Instant.now());
        payout.setPaymentReference(StringUtils.hasText(paymentReference) ? paymentReference.trim() : null);
        if (StringUtils.hasText(note)) {
            payout.setDecisionNote(note.trim());
        }
        earningRepository.findByPayoutId(payout.getId()).forEach(earning -> earning.setStatus(EarningStatus.PAID));
        notifyInstructor(payout, "Payout sent",
                "We sent your payout of %s %s.".formatted(payout.getAmount(), payout.getCurrency()));
        log.info("Payout paid id={} by adminId={}", payout.getId(), adminId);
        return PayoutDtos.PayoutResponse.from(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutDtos.RevenueShareResponse findRevenueShare(String courseSlug) {
        Course course = findCourse(courseSlug);
        return toShareResponse(course, shareRepository.findById(course.getId()).orElse(null));
    }

    @Override
    @Transactional
    public PayoutDtos.RevenueShareResponse setRevenueShare(String courseSlug, BigDecimal instructorPercent) {
        Course course = findCourse(courseSlug);
        if (instructorPercent == null) {
            shareRepository.deleteById(course.getId());
            return toShareResponse(course, null);
        }
        CourseRevenueShare share = shareRepository.findById(course.getId())
                .orElseGet(() -> CourseRevenueShare.builder().courseId(course.getId()).build());
        share.setInstructorPercent(instructorPercent);
        // Only future sales use the new share; recorded earnings keep the share they were made with
        return toShareResponse(course, shareRepository.save(share));
    }

    private PayoutRequest lockForReview(Integer id, UUID adminId) {
        PayoutRequest payout = payoutRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Payout", id));
        if (payout.getInstructorId().equals(adminId)) {
            throw new ForbiddenActionException("Another admin must review your own payout");
        }
        return payout;
    }

    private static void review(PayoutRequest payout, UUID adminId, String note) {
        payout.setReviewedBy(adminId);
        payout.setReviewedAt(Instant.now());
        payout.setDecisionNote(StringUtils.hasText(note) ? note.trim() : null);
    }

    private void notifyInstructor(PayoutRequest payout, String title, String message) {
        notificationService.notifyUsers(List.of(payout.getInstructorId()), NotificationType.PAYOUT, title, message,
                INSTRUCTOR_LINK);
    }

    private PayoutDtos.AdminPayoutResponse toAdminResponse(PayoutRequest payout) {
        User instructor = userRepository.findById(payout.getInstructorId()).orElse(null);
        return new PayoutDtos.AdminPayoutResponse(
                PayoutDtos.PayoutResponse.from(payout),
                payout.getInstructorId().toString(),
                instructor == null ? null : (instructor.getFirstName() + " " + instructor.getLastName()).trim(),
                instructor == null ? null : instructor.getEmail(),
                earningRepository.findByPayoutId(payout.getId()).size()
        );
    }

    private Course findCourse(String slug) {
        return courseRepository.findBySlug(slug).orElseThrow(() -> ResourceNotFoundException.of("Course", slug));
    }

    private PayoutDtos.RevenueShareResponse toShareResponse(Course course, CourseRevenueShare share) {
        BigDecimal fallback = properties.instructorSharePercent();
        return new PayoutDtos.RevenueShareResponse(course.getSlug(),
                share == null ? fallback : share.getInstructorPercent(), share != null, fallback);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown status: " + value);
        }
    }
}
