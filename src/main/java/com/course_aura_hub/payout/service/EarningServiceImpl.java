package com.course_aura_hub.payout.service;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.repository.OrderRepository;
import com.course_aura_hub.order.service.OrderPaid;
import com.course_aura_hub.order.service.OrderRefunded;
import com.course_aura_hub.payout.config.PayoutProperties;
import com.course_aura_hub.payout.model.CourseRevenueShare;
import com.course_aura_hub.payout.model.EarningStatus;
import com.course_aura_hub.payout.model.EarningType;
import com.course_aura_hub.payout.model.InstructorEarning;
import com.course_aura_hub.payout.repository.CourseRevenueShareRepository;
import com.course_aura_hub.payout.repository.InstructorEarningRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Listens to order events inside the order's own transaction, so an earning commits or rolls back with the payment
 * or refund that caused it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarningServiceImpl implements EarningService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InstructorEarningRepository earningRepository;
    private final CourseRevenueShareRepository shareRepository;
    private final OrderRepository orderRepository;
    private final PayoutProperties properties;

    @EventListener
    @Transactional
    public void onOrderPaid(OrderPaid event) {
        if (!properties.enabled()) {
            return;
        }
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null || order.getAmount().signum() <= 0) {
            return;
        }
        Course course = order.getCourse();
        UUID instructorId = course.getOwnerId();
        // Admin-managed courses have no owner, so the platform keeps the whole sale
        if (instructorId == null || earningRepository.existsByOrderIdAndType(order.getId(), EarningType.SALE)) {
            return;
        }

        BigDecimal percent = shareRepository.findById(course.getId())
                .map(CourseRevenueShare::getInstructorPercent)
                .orElse(properties.instructorSharePercent());
        BigDecimal gross = order.getAmount();
        BigDecimal net = gross.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_EVEN);
        Instant paidAt = order.getPaidAt() == null ? Instant.now() : order.getPaidAt();

        earningRepository.save(InstructorEarning.builder()
                .instructorId(instructorId)
                .order(order)
                .course(course)
                .type(EarningType.SALE)
                .gross(gross)
                .platformFee(gross.subtract(net))
                .net(net)
                .sharePercent(percent)
                .currency(order.getCurrency())
                .status(EarningStatus.PENDING)
                .availableAt(paidAt.plus(Duration.ofDays(properties.holdDays())))
                .build());
        log.info("Recorded instructor earning order={} instructorId={} net={}", order.getOrderNumber(), instructorId, net);
    }

    /** Runs whether or not payouts are enabled: an earning that exists must follow its order. */
    @EventListener
    @Transactional
    public void onOrderRefunded(OrderRefunded event) {
        earningRepository.findByOrderIdAndTypeForUpdate(event.orderId(), EarningType.SALE).ifPresent(sale -> {
            boolean unclaimed = sale.getPayoutId() == null
                    && (sale.getStatus() == EarningStatus.PENDING || sale.getStatus() == EarningStatus.AVAILABLE);
            if (unclaimed) {
                sale.setStatus(EarningStatus.REVERSED);
                log.info("Reversed instructor earning id={} after refund", sale.getId());
                return;
            }
            if (sale.getStatus() == EarningStatus.REVERSED
                    || earningRepository.existsByOrderIdAndType(event.orderId(), EarningType.REFUND)) {
                return;
            }
            // Already claimed or paid: take it back from the next payout instead
            earningRepository.save(InstructorEarning.builder()
                    .instructorId(sale.getInstructorId())
                    .order(sale.getOrder())
                    .course(sale.getCourse())
                    .type(EarningType.REFUND)
                    .gross(sale.getGross().negate())
                    .platformFee(sale.getPlatformFee().negate())
                    .net(sale.getNet().negate())
                    .sharePercent(sale.getSharePercent())
                    .currency(sale.getCurrency())
                    .status(EarningStatus.AVAILABLE)
                    .availableAt(Instant.now())
                    .build());
            log.info("Recorded refund deduction for instructor earning id={}", sale.getId());
        });
    }

    @Override
    @Transactional
    public int releaseDue() {
        return earningRepository.releaseDue(Instant.now());
    }
}
