package com.course_aura_hub.refund.facade;

import com.course_aura_hub.order.facade.CheckoutFacade;
import com.course_aura_hub.refund.dto.RefundDtos;
import com.course_aura_hub.refund.service.RefundRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Runs a refund request around the existing refund path. Each step has its own transaction, so a refund that fails
 * (for example with no payment provider configured) can't roll back the request: it stays in the admin queue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestFacade {

    private final RefundRequestService requestService;
    private final CheckoutFacade checkoutFacade;

    public RefundDtos.RefundRequestResponse request(String email, String orderNumber, String reason) {
        RefundRequestService.Created created = requestService.create(email, orderNumber, reason);
        if (!created.automatic()) {
            return requestService.findMine(email).stream()
                    .filter(item -> item.id().equals(created.id())).findFirst().orElseThrow();
        }
        try {
            checkoutFacade.refund(orderNumber);
        } catch (RuntimeException ex) {
            log.warn("Automatic refund failed for order={}: {}", orderNumber, ex.getMessage());
            return requestService.keepPending(created.id(), "The automatic refund could not run, so an admin will review it.");
        }
        return requestService.markApproved(created.id(), null, "Approved automatically under the refund policy.", true);
    }

    public RefundDtos.RefundRequestResponse approve(Integer id, UUID adminId, String note) {
        String orderNumber = requestService.requirePending(id);
        // Refunds the order, removes the enrollment, refunds a member card and notifies the learner
        checkoutFacade.refund(orderNumber);
        return requestService.markApproved(id, adminId, note, false);
    }

    public RefundDtos.RefundRequestResponse reject(Integer id, UUID adminId, String note) {
        return requestService.reject(id, adminId, note);
    }
}
