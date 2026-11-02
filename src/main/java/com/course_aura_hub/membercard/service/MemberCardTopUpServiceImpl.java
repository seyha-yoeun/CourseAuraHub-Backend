package com.course_aura_hub.membercard.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.file.model.FilePurpose;
import com.course_aura_hub.file.service.FileService;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.membercard.dto.MemberCardReportResponse;
import com.course_aura_hub.membercard.dto.MemberCardTopUpCreateRequest;
import com.course_aura_hub.membercard.dto.MemberCardTopUpPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardTopUpResponse;
import com.course_aura_hub.membercard.model.MemberCard;
import com.course_aura_hub.membercard.model.MemberCardApplicationStatus;
import com.course_aura_hub.membercard.model.MemberCardStatus;
import com.course_aura_hub.membercard.model.MemberCardTopUpRequest;
import com.course_aura_hub.membercard.model.MemberCardTopUpStatus;
import com.course_aura_hub.membercard.model.MemberCardTransactionType;
import com.course_aura_hub.membercard.repository.MemberCardApplicationRepository;
import com.course_aura_hub.membercard.repository.MemberCardRepository;
import com.course_aura_hub.membercard.repository.MemberCardTopUpRepository;
import com.course_aura_hub.membercard.repository.MemberCardTransactionRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.order.config.PaymentProperties;
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
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** A request moves no money. Approval credits the card through the ledger, in one transaction with the status change. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberCardTopUpServiceImpl implements MemberCardTopUpService {

    private static final String LINK = "/member-card";
    private static final String ADMIN_LINK = "/admin/member-cards";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REPORT_DAYS = 366;

    private final MemberCardTopUpRepository topUpRepository;
    private final MemberCardRepository cardRepository;
    private final MemberCardApplicationRepository applicationRepository;
    private final MemberCardTransactionRepository transactionRepository;
    private final MemberCardService cardService;
    private final LearnerAccountService learnerAccountService;
    private final FileService fileService;
    private final NotificationService notificationService;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional
    public MemberCardTopUpResponse request(String email, MemberCardTopUpCreateRequest request) {
        Integer studentId = learnerAccountService.currentStudent(email).getId();
        MemberCard card = cardRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> new BusinessRuleException("You need a member card to request a top-up"));
        if (card.getStatus() != MemberCardStatus.ACTIVE) {
            throw new BusinessRuleException("This member card is suspended");
        }
        if (topUpRepository.existsByCardIdAndStatus(card.getId(), MemberCardTopUpStatus.PENDING)) {
            throw new DuplicateResourceException("You already have a top-up request waiting for review");
        }

        String receipt = StringUtils.hasText(request.receiptUrl()) ? request.receiptUrl().trim() : null;
        if (receipt != null) {
            fileService.requireOwned(receipt, email, FilePurpose.TOP_UP_RECEIPT);
        }
        MemberCardTopUpRequest saved = topUpRepository.save(MemberCardTopUpRequest.builder()
                .card(card)
                .amount(request.amount())
                .note(StringUtils.hasText(request.note()) ? request.note().trim() : null)
                .receiptUrl(receipt)
                .receiptName(receipt == null ? null : request.receiptName())
                .status(MemberCardTopUpStatus.PENDING)
                .build());
        notificationService.notifyAdmins(
                NotificationType.MEMBER_CARD,
                "New top-up request",
                "%s asked for %s %s.".formatted(
                        card.getHolderName(), request.amount().toPlainString(), paymentProperties.currency()),
                ADMIN_LINK
        );
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCardTopUpResponse> findMine(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> cardRepository.findByStudentId(student.getId()))
                .map(card -> topUpRepository.findTop20ByCardIdOrderByCreatedAtDescIdDesc(card.getId())
                        .stream().map(this::toResponse).toList())
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberCardTopUpPageResponse findAll(String status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        Page<MemberCardTopUpRequest> requests = StringUtils.hasText(status)
                ? topUpRepository.findByStatus(parseStatus(status), pageable)
                : topUpRepository.findAll(pageable);
        return new MemberCardTopUpPageResponse(
                requests.map(this::toResponse).getContent(),
                requests.getNumber(),
                requests.getSize(),
                requests.getTotalElements(),
                requests.getTotalPages()
        );
    }

    @Override
    @Transactional
    public MemberCardTopUpResponse approve(Integer requestId, UUID adminId) {
        MemberCardTopUpRequest request = lockPending(requestId);
        close(request, MemberCardTopUpStatus.APPROVED, adminId, null);
        cardService.creditTopUp(
                request.getCard().getId(), adminId, request.getAmount(), "TOPUP-" + request.getId(), "Top-up request approved");
        log.info("Approved top-up request id={} by admin={}", requestId, adminId);
        return toResponse(request);
    }

    @Override
    @Transactional
    public MemberCardTopUpResponse reject(Integer requestId, UUID adminId, String note) {
        MemberCardTopUpRequest request = lockPending(requestId);
        close(request, MemberCardTopUpStatus.REJECTED, adminId, note.trim());
        notificationService.notifyStudent(
                request.getCard().getStudent().getId(),
                NotificationType.MEMBER_CARD,
                "Top-up request declined",
                request.getReviewNote(),
                LINK
        );
        log.info("Rejected top-up request id={} by admin={}", requestId, adminId);
        return toResponse(request);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberCardReportResponse report(int days) {
        int period = Math.max(1, Math.min(days, MAX_REPORT_DAYS));
        Map<MemberCardTransactionType, BigDecimal> totals = new EnumMap<>(MemberCardTransactionType.class);
        transactionRepository.sumByTypeSince(Instant.now().minus(Duration.ofDays(period)))
                .forEach(row -> totals.put(row.getType(), row.getTotal()));
        return new MemberCardReportResponse(
                period,
                paymentProperties.currency(),
                cardRepository.count(),
                cardRepository.countByStatus(MemberCardStatus.ACTIVE),
                cardRepository.sumBalances(),
                applicationRepository.countByStatus(MemberCardApplicationStatus.PENDING),
                topUpRepository.countByStatus(MemberCardTopUpStatus.PENDING),
                totals.getOrDefault(MemberCardTransactionType.TOP_UP, BigDecimal.ZERO),
                totals.getOrDefault(MemberCardTransactionType.DEDUCTION, BigDecimal.ZERO),
                totals.getOrDefault(MemberCardTransactionType.PURCHASE, BigDecimal.ZERO),
                totals.getOrDefault(MemberCardTransactionType.REFUND, BigDecimal.ZERO)
        );
    }

    private MemberCardTopUpRequest lockPending(Integer requestId) {
        MemberCardTopUpRequest request = topUpRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> ResourceNotFoundException.of("Top-up request", requestId));
        if (request.getStatus() != MemberCardTopUpStatus.PENDING) {
            throw new BusinessRuleException("This request was already reviewed");
        }
        return request;
    }

    private void close(MemberCardTopUpRequest request, MemberCardTopUpStatus status, UUID adminId, String note) {
        request.setStatus(status);
        request.setReviewNote(note);
        request.setReviewedBy(adminId);
        request.setReviewedAt(Instant.now());
    }

    private MemberCardTopUpStatus parseStatus(String status) {
        try {
            return MemberCardTopUpStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown top-up status: " + status);
        }
    }

    private MemberCardTopUpResponse toResponse(MemberCardTopUpRequest request) {
        MemberCard card = request.getCard();
        return new MemberCardTopUpResponse(
                request.getId(),
                card.getId(),
                card.getCardNumber(),
                card.getHolderName(),
                card.getStudent().getEmail(),
                request.getAmount(),
                paymentProperties.currency(),
                request.getNote(),
                fileService.playableUrl(request.getReceiptUrl()),
                request.getReceiptName(),
                request.getStatus().name(),
                request.getReviewNote(),
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }
}
