package com.course_aura_hub.refund.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.curriculum.repository.LessonProgressRepository;
import com.course_aura_hub.gift.repository.AccessCodeRepository;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.model.OrderStatus;
import com.course_aura_hub.order.repository.OrderRepository;
import com.course_aura_hub.refund.config.RefundPolicyProperties;
import com.course_aura_hub.refund.dto.RefundDtos;
import com.course_aura_hub.refund.model.RefundRequest;
import com.course_aura_hub.refund.model.RefundRequestStatus;
import com.course_aura_hub.refund.repository.RefundRequestRepository;
import com.course_aura_hub.student.model.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundRequestServiceImpl implements RefundRequestService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RefundRequestRepository requestRepository;
    private final OrderRepository orderRepository;
    private final LearnerAccountService learnerAccountService;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final NotificationService notificationService;
    private final RefundPolicyProperties policy;
    private final AccessCodeRepository accessCodeRepository;

    @Override
    @Transactional(readOnly = true)
    public RefundDtos.Eligibility eligibility(String email, String orderNumber) {
        Student student = learnerAccountService.findCurrentStudent(email)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderNumber));
        return evaluate(findOwned(student, orderNumber), student);
    }

    @Override
    @Transactional
    public Created create(String email, String orderNumber, String reason) {
        Student student = learnerAccountService.findCurrentStudent(email)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderNumber));
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
                .filter(found -> found.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderNumber));
        RefundDtos.Eligibility eligibility = evaluate(order, student);
        if (!eligibility.canRequest()) {
            if (requestRepository.existsByOrderIdAndStatusIn(order.getId(), List.of(RefundRequestStatus.PENDING))) {
                throw new DuplicateResourceException("You already asked for a refund on this order");
            }
            throw new BusinessRuleException(String.join(" ", eligibility.reasons()));
        }

        RefundRequest saved = requestRepository.save(RefundRequest.builder()
                .order(order)
                .studentId(student.getId())
                .reason(reason.trim())
                .status(RefundRequestStatus.PENDING)
                .progressPercent(eligibility.progressPercent())
                .build());
        if (!eligibility.automatic()) {
            notificationService.notifyAdmins(NotificationType.REFUND_REQUEST, "Refund requested",
                    student.getName() + " asked for a refund on " + order.getCourse().getTitle() + ".",
                    "/admin/refund-requests");
        }
        log.info("Refund requested id={} order={} automatic={}", saved.getId(), orderNumber, eligibility.automatic());
        return new Created(saved.getId(), orderNumber, eligibility.automatic());
    }

    @Override
    @Transactional
    public String requirePending(Integer id) {
        return requirePendingRequest(id).getOrder().getOrderNumber();
    }

    @Override
    @Transactional
    public RefundDtos.RefundRequestResponse markApproved(Integer id, UUID reviewer, String note, boolean automatic) {
        RefundRequest request = requirePendingRequest(id);
        request.setStatus(RefundRequestStatus.APPROVED);
        request.setAutoApproved(automatic);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(Instant.now());
        request.setDecisionNote(blankToNull(note));
        log.info("Refund request approved id={} automatic={}", id, automatic);
        return RefundDtos.RefundRequestResponse.from(request);
    }

    @Override
    @Transactional
    public RefundDtos.RefundRequestResponse keepPending(Integer id, String note) {
        RefundRequest request = requirePendingRequest(id);
        request.setDecisionNote(note);
        notificationService.notifyAdmins(NotificationType.REFUND_REQUEST, "Refund needs a decision",
                "An automatic refund on " + request.getOrder().getCourse().getTitle() + " could not run.",
                "/admin/refund-requests");
        return RefundDtos.RefundRequestResponse.from(request);
    }

    @Override
    @Transactional
    public RefundDtos.RefundRequestResponse reject(Integer id, UUID reviewer, String note) {
        if (!StringUtils.hasText(note)) {
            throw new BusinessRuleException("Tell the learner why the refund was declined");
        }
        RefundRequest request = requirePendingRequest(id);
        request.setStatus(RefundRequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(Instant.now());
        request.setDecisionNote(note.trim());
        notificationService.notifyStudent(request.getStudentId(), NotificationType.REFUND_REQUEST, "Refund request declined",
                "Your refund request for " + request.getOrder().getCourse().getTitle() + " was declined: " + note.trim(),
                "/orders");
        log.info("Refund request rejected id={}", id);
        return RefundDtos.RefundRequestResponse.from(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDtos.RefundRequestResponse> findMine(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .map(student -> requestRepository.findByStudentIdOrderByCreatedAtDescIdDesc(student.getId()).stream()
                        .map(RefundDtos.RefundRequestResponse::from).toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public RefundDtos.AdminPage findForAdmin(String status, int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        Page<RefundRequest> result = !StringUtils.hasText(status) || "all".equalsIgnoreCase(status)
                ? requestRepository.findAllByOrderByCreatedAtDescIdDesc(request)
                : requestRepository.findByStatusOrderByCreatedAtAscIdAsc(parseStatus(status), request);
        return new RefundDtos.AdminPage(
                result.map(item -> new RefundDtos.AdminRefundRequestResponse(
                        RefundDtos.RefundRequestResponse.from(item),
                        item.getOrder().getStudent().getName(),
                        item.getOrder().getStudent().getEmail(),
                        item.getOrder().getProvider(),
                        item.getOrder().getPaidAt())).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** The policy check: within the window and not too far into the course means an automatic refund. */
    private RefundDtos.Eligibility evaluate(Order order, Student student) {
        List<String> reasons = new ArrayList<>();
        int progress = progressPercent(student.getId(), order.getCourse().getId());
        Instant paidAt = order.getPaidAt() == null ? order.getCreatedAt() : order.getPaidAt();
        Instant windowEnds = paidAt.plus(Duration.ofDays(policy.windowDays()));

        boolean canRequest = true;
        if (!policy.enabled()) {
            canRequest = false;
            reasons.add("Refund requests are switched off. Contact support.");
        } else if (order.getStatus() != OrderStatus.PAID) {
            canRequest = false;
            reasons.add("Only a paid order can be refunded.");
        } else if (order.getAmount().signum() == 0) {
            canRequest = false;
            reasons.add("This order was free, so there is nothing to refund.");
        } else if (requestRepository.existsByOrderIdAndStatusIn(order.getId(),
                List.of(RefundRequestStatus.PENDING, RefundRequestStatus.REJECTED))) {
            canRequest = false;
            reasons.add("A refund was already requested for this order.");
        } else if (order.isGift() && accessCodeRepository.findByGiftOrderId(order.getId())
                .filter(code -> code.getUses() > 0).isPresent()) {
            // The buyer's own progress says nothing here, and refunding would leave the recipient enrolled for free
            canRequest = false;
            reasons.add("This gift has already been redeemed, so it can't be refunded.");
        }

        if (canRequest) {
            if (Instant.now().isAfter(windowEnds)) {
                reasons.add("It is more than " + policy.windowDays() + " days since you paid, so an admin will review it.");
            }
            if (progress > policy.maxProgressPercent()) {
                reasons.add("You have completed more than " + policy.maxProgressPercent() + "% of the course, so an admin will review it.");
            }
            if (!policy.autoApprove()) {
                reasons.add("Every refund is reviewed by an admin.");
            }
        }
        return new RefundDtos.Eligibility(canRequest, canRequest && reasons.isEmpty(), progress, windowEnds, reasons);
    }

    private int progressPercent(Integer studentId, Integer courseId) {
        long total = lessonRepository.countByCourseId(courseId);
        return total == 0 ? 0 : (int) Math.round(100.0 * progressRepository.countCompleted(studentId, courseId) / total);
    }

    private Order findOwned(Student student, String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .filter(order -> order.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderNumber));
    }

    private RefundRequest requirePendingRequest(Integer id) {
        RefundRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Refund request", id));
        if (request.getStatus() != RefundRequestStatus.PENDING) {
            throw new BusinessRuleException("This refund request was already decided");
        }
        return request;
    }

    private static RefundRequestStatus parseStatus(String value) {
        try {
            return RefundRequestStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("status must be pending, approved, rejected or all");
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
