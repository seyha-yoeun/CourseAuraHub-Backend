package com.course_aura_hub.membercard.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.membercard.dto.MemberCardAdjustmentRequest;
import com.course_aura_hub.membercard.dto.MemberCardApplicationPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardApplicationRequest;
import com.course_aura_hub.membercard.dto.MemberCardApplicationResponse;
import com.course_aura_hub.membercard.dto.MemberCardOverviewResponse;
import com.course_aura_hub.membercard.dto.MemberCardPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardResponse;
import com.course_aura_hub.membercard.dto.MemberCardTransactionPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardTransactionResponse;
import com.course_aura_hub.membercard.model.MemberCard;
import com.course_aura_hub.membercard.model.MemberCardApplication;
import com.course_aura_hub.membercard.model.MemberCardApplicationStatus;
import com.course_aura_hub.membercard.model.MemberCardStatus;
import com.course_aura_hub.membercard.model.MemberCardTransaction;
import com.course_aura_hub.membercard.model.MemberCardTransactionType;
import com.course_aura_hub.membercard.repository.MemberCardApplicationRepository;
import com.course_aura_hub.membercard.repository.MemberCardRepository;
import com.course_aura_hub.membercard.repository.MemberCardTransactionRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentRepository;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Every balance change goes through {@link #record}, under a lock on the card row, and writes a ledger entry.
 * Unique indexes on the ledger are the backstop against a repeated charge, refund or adjustment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberCardServiceImpl implements MemberCardService {

    private static final String LINK = "/member-card";
    private static final int MAX_PAGE_SIZE = 100;

    private final MemberCardRepository cardRepository;
    private final MemberCardApplicationRepository applicationRepository;
    private final MemberCardTransactionRepository transactionRepository;
    private final StudentRepository studentRepository;
    private final LearnerAccountService learnerAccountService;
    private final NotificationService notificationService;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional(readOnly = true)
    public MemberCardOverviewResponse findMine(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .map(student -> new MemberCardOverviewResponse(
                        cardRepository.findByStudentId(student.getId()).map(this::toResponse).orElse(null),
                        applicationRepository.findFirstByStudentIdOrderByCreatedAtDescIdDesc(student.getId())
                                .map(this::toResponse)
                                .orElse(null)))
                .orElseGet(() -> new MemberCardOverviewResponse(null, null));
    }

    @Override
    @Transactional
    public MemberCardApplicationResponse apply(String email, MemberCardApplicationRequest request) {
        // Locking the learner serializes double submits; the partial unique index is the backstop
        Integer studentId = learnerAccountService.currentStudent(email).getId();
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));

        if (cardRepository.existsByStudentId(studentId)) {
            throw new DuplicateResourceException("You already have a member card");
        }
        if (applicationRepository.existsByStudentIdAndStatus(studentId, MemberCardApplicationStatus.PENDING)) {
            throw new DuplicateResourceException("Your application is already being reviewed");
        }

        MemberCardApplication application = applicationRepository.save(MemberCardApplication.builder()
                .student(student)
                .fullName(request.fullName().trim())
                .phone(request.phone().trim())
                .dateOfBirth(request.dateOfBirth())
                .address(request.address().trim())
                .status(MemberCardApplicationStatus.PENDING)
                .build());
        notificationService.notifyAdmins(
                NotificationType.MEMBER_CARD,
                "New member card application",
                application.getFullName() + " applied for a member card.",
                "/admin/member-cards"
        );
        return toResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberCardTransactionPageResponse findMyTransactions(String email, int page, int size) {
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> cardRepository.findByStudentId(student.getId()))
                .map(card -> findTransactions(card.getId(), page, size))
                .orElseGet(() -> new MemberCardTransactionPageResponse(List.of(), 0, size, 0, 0));
    }

    @Override
    @Transactional(readOnly = true)
    public MemberCardApplicationPageResponse findApplications(String status, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        Page<MemberCardApplication> applications = StringUtils.hasText(status)
                ? applicationRepository.findByStatus(parseApplicationStatus(status), pageable)
                : applicationRepository.findAll(pageable);
        return new MemberCardApplicationPageResponse(
                applications.map(this::toResponse).getContent(),
                applications.getNumber(),
                applications.getSize(),
                applications.getTotalElements(),
                applications.getTotalPages()
        );
    }

    @Override
    @Transactional
    public MemberCardApplicationResponse approve(Integer applicationId, UUID adminId) {
        MemberCardApplication application = lockPendingApplication(applicationId, adminId);
        Student student = application.getStudent();
        if (cardRepository.existsByStudentId(student.getId())) {
            throw new DuplicateResourceException("This learner already has a member card");
        }

        close(application, MemberCardApplicationStatus.APPROVED, adminId, null);
        MemberCard card = cardRepository.save(MemberCard.builder()
                .cardNumber(MemberCardNumberGenerator.next())
                .student(student)
                .holderName(application.getFullName())
                .balance(BigDecimal.ZERO)
                .status(MemberCardStatus.ACTIVE)
                .build());
        notificationService.notifyStudent(
                student.getId(),
                NotificationType.MEMBER_CARD,
                "Member card approved",
                "Your member card " + card.getCardNumber() + " is ready.",
                LINK
        );
        log.info("Approved member card application id={} card={}", applicationId, card.getId());
        return toResponse(application);
    }

    @Override
    @Transactional
    public MemberCardApplicationResponse reject(Integer applicationId, UUID adminId, String note) {
        MemberCardApplication application = lockPendingApplication(applicationId, adminId);
        close(application, MemberCardApplicationStatus.REJECTED, adminId, note.trim());
        notificationService.notifyStudent(
                application.getStudent().getId(),
                NotificationType.MEMBER_CARD,
                "Member card application declined",
                application.getReviewNote(),
                LINK
        );
        log.info("Rejected member card application id={}", applicationId);
        return toResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberCardPageResponse findCards(int page, int size) {
        Page<MemberCard> cards = cardRepository.findAll(pageRequest(page, size));
        return new MemberCardPageResponse(
                cards.map(this::toResponse).getContent(),
                cards.getNumber(),
                cards.getSize(),
                cards.getTotalElements(),
                cards.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MemberCardTransactionPageResponse findTransactions(Integer cardId, int page, int size) {
        if (!cardRepository.existsById(cardId)) {
            throw ResourceNotFoundException.of("Member card", cardId);
        }
        Page<MemberCardTransaction> transactions = transactionRepository.findByCardId(cardId, pageRequest(page, size));
        return new MemberCardTransactionPageResponse(
                transactions.map(this::toResponse).getContent(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages()
        );
    }

    @Override
    @Transactional
    public MemberCardResponse setStatus(Integer cardId, UUID adminId, String status) {
        MemberCard card = lockCard(cardId);
        requireNotOwnCard(card.getStudent(), adminId);
        card.setStatus(parseCardStatus(status));
        log.info("Set member card id={} status={} by admin={}", cardId, card.getStatus(), adminId);
        return toResponse(card);
    }

    @Override
    @Transactional
    public MemberCardTransactionResponse adjust(Integer cardId, UUID adminId, MemberCardAdjustmentRequest request) {
        MemberCardTransactionType type = parseAdjustmentType(request.type());
        MemberCard card = lockCard(cardId);
        requireNotOwnCard(card.getStudent(), adminId);

        String key = request.idempotencyKey().trim();
        var repeated = transactionRepository.findByCardIdAndIdempotencyKey(cardId, key);
        if (repeated.isPresent()) {
            MemberCardTransaction original = repeated.get();
            if (original.getType() != type || original.getAmount().compareTo(request.amount()) != 0) {
                throw new DuplicateResourceException("This idempotency key was used for a different adjustment");
            }
            return toResponse(original);
        }

        boolean topUp = type == MemberCardTransactionType.TOP_UP;
        MemberCardTransaction transaction = record(card, type, topUp ? request.amount() : request.amount().negate(),
                null, key, request.note().trim(), adminId);
        notificationService.notifyStudent(
                card.getStudent().getId(),
                NotificationType.MEMBER_CARD,
                topUp ? "Member card topped up" : "Member card balance reduced",
                "%s %s. Your balance is now %s.".formatted(
                        request.amount().toPlainString(), paymentProperties.currency(),
                        transaction.getBalanceAfter().toPlainString()),
                LINK
        );
        log.info("Adjusted member card id={} type={} by admin={}", cardId, type, adminId);
        return toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireUsableCard(String email) {
        MemberCard card = learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> cardRepository.findByStudentId(student.getId()))
                .orElseThrow(() -> new BusinessRuleException("You need a member card to pay this way"));
        requireActive(card);
    }

    @Override
    @Transactional
    public void charge(Integer studentId, BigDecimal amount, String reference, String note) {
        MemberCard card = cardRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> new BusinessRuleException("You need a member card to pay this way"));
        requireActive(card);
        if (transactionRepository.existsByTypeAndReference(MemberCardTransactionType.PURCHASE, reference)) {
            log.warn("Ignoring repeated charge for reference={}", reference);
            return;
        }
        record(card, MemberCardTransactionType.PURCHASE, amount.negate(), reference, null, note, null);
    }

    @Override
    @Transactional
    public void refund(Integer studentId, BigDecimal amount, String reference, String note) {
        MemberCard card = cardRepository.findByStudentIdForUpdate(studentId)
                .orElseThrow(() -> new BusinessRuleException("The learner's member card no longer exists"));
        if (transactionRepository.existsByTypeAndReference(MemberCardTransactionType.REFUND, reference)) {
            log.warn("Ignoring repeated refund for reference={}", reference);
            return;
        }
        record(card, MemberCardTransactionType.REFUND, amount, reference, null, note, null);
    }

    /** @param change positive adds to the balance, negative takes from it */
    private MemberCardTransaction record(
            MemberCard card,
            MemberCardTransactionType type,
            BigDecimal change,
            String reference,
            String idempotencyKey,
            String note,
            UUID performedBy
    ) {
        BigDecimal balance = card.getBalance().add(change);
        if (balance.signum() < 0) {
            throw new BusinessRuleException("The member card balance is too low");
        }
        card.setBalance(balance);
        return transactionRepository.save(MemberCardTransaction.builder()
                .card(card)
                .type(type)
                .amount(change.abs())
                .balanceAfter(balance)
                .reference(reference)
                .idempotencyKey(idempotencyKey)
                .note(note)
                .performedBy(performedBy)
                .build());
    }

    private MemberCardApplication lockPendingApplication(Integer applicationId, UUID adminId) {
        MemberCardApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Member card application", applicationId));
        requireNotOwnCard(application.getStudent(), adminId);
        if (application.getStatus() != MemberCardApplicationStatus.PENDING) {
            throw new BusinessRuleException("This application was already reviewed");
        }
        return application;
    }

    private void close(MemberCardApplication application, MemberCardApplicationStatus status, UUID adminId, String note) {
        application.setStatus(status);
        application.setReviewNote(note);
        application.setReviewedBy(adminId);
        application.setReviewedAt(Instant.now());
    }

    private MemberCard lockCard(Integer cardId) {
        return cardRepository.findByIdForUpdate(cardId)
                .orElseThrow(() -> ResourceNotFoundException.of("Member card", cardId));
    }

    private void requireActive(MemberCard card) {
        if (card.getStatus() != MemberCardStatus.ACTIVE) {
            throw new BusinessRuleException("This member card is suspended");
        }
    }

    /** A balance is money, so no admin may approve or fund their own card. */
    private void requireNotOwnCard(Student student, UUID adminId) {
        if (student.getUser() != null && Objects.equals(student.getUser().getId(), adminId)) {
            throw new ForbiddenActionException("Another admin must handle your own member card");
        }
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
    }

    private MemberCardApplicationStatus parseApplicationStatus(String status) {
        try {
            return MemberCardApplicationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown application status: " + status);
        }
    }

    private MemberCardStatus parseCardStatus(String status) {
        try {
            return MemberCardStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown card status: " + status);
        }
    }

    private MemberCardTransactionType parseAdjustmentType(String type) {
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (MemberCardTransactionType.TOP_UP.name().equals(normalized)) {
            return MemberCardTransactionType.TOP_UP;
        }
        if (MemberCardTransactionType.DEDUCTION.name().equals(normalized)) {
            return MemberCardTransactionType.DEDUCTION;
        }
        throw new BusinessRuleException("Adjustment type must be TOP_UP or DEDUCTION");
    }

    private MemberCardApplicationResponse toResponse(MemberCardApplication application) {
        return new MemberCardApplicationResponse(
                application.getId(),
                application.getStudent().getId(),
                application.getStudent().getEmail(),
                application.getFullName(),
                application.getPhone(),
                application.getDateOfBirth(),
                application.getAddress(),
                application.getStatus().name(),
                application.getReviewNote(),
                application.getCreatedAt(),
                application.getReviewedAt()
        );
    }

    private MemberCardResponse toResponse(MemberCard card) {
        return new MemberCardResponse(
                card.getId(),
                card.getStudent().getId(),
                card.getStudent().getEmail(),
                card.getCardNumber(),
                card.getHolderName(),
                card.getBalance(),
                paymentProperties.currency(),
                card.getStatus().name(),
                card.getCreatedAt()
        );
    }

    private MemberCardTransactionResponse toResponse(MemberCardTransaction transaction) {
        return new MemberCardTransactionResponse(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReference(),
                transaction.getNote(),
                transaction.getCreatedAt()
        );
    }
}
