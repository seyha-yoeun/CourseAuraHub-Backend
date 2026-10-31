package com.course_aura_hub.order.service;

import com.course_aura_hub.cart.dto.CheckoutResponse;
import com.course_aura_hub.cart.model.CartItem;
import com.course_aura_hub.cart.repository.CartItemRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.coupon.service.CouponQuote;
import com.course_aura_hub.coupon.service.CouponService;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.enrollment.dto.EnrollmentCreateRequest;
import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import com.course_aura_hub.enrollment.service.EnrollmentService;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.order.dto.AdminOrderPageResponse;
import com.course_aura_hub.order.dto.AdminOrderResponse;
import com.course_aura_hub.order.dto.OrderResponse;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.model.OrderGroup;
import com.course_aura_hub.order.model.OrderStatus;
import com.course_aura_hub.order.payment.PaymentEvent;
import com.course_aura_hub.order.payment.PaymentGateway;
import com.course_aura_hub.order.payment.PaymentSession;
import com.course_aura_hub.order.repository.OrderGroupRepository;
import com.course_aura_hub.order.repository.OrderRepository;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String GROUP_PREFIX = "GRP-";
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final LearnerAccountService learnerAccountService;
    private final CouponService couponService;
    private final OrderGroupRepository orderGroupRepository;
    private final CartItemRepository cartItemRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Order openOrder(String email, String courseSlug, String couponCode, String currency, String provider) {
        // Locking the learner serializes parallel checkouts; the partial unique index is the backstop
        Integer studentId = learnerAccountService.currentStudent(email).getId();
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));

        Course course = courseRepository.findBySlug(courseSlug)
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));

        if (course.currentPrice().price().signum() <= 0) {
            throw new BusinessRuleException("This course is free; enroll directly instead");
        }
        if (studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new DuplicateResourceException("You are already enrolled in this course");
        }

        Optional<Order> open = orderRepository
                .findByStudentIdAndCourseIdAndStatus(student.getId(), course.getId(), OrderStatus.PENDING);
        CouponQuote quote = couponService.applyToOrder(
                couponCode, course, student.getId(), open.map(Order::getId).orElse(null));
        if (quote.finalAmount().signum() > 0 && provider == null) {
            throw new ServiceUnavailableException("Paid checkout is not available yet");
        }

        if (open.isPresent()) {
            Order order = open.get();
            boolean sameCoupon = Objects.equals(
                    order.getCoupon() == null ? null : order.getCoupon().getId(),
                    quote.coupon() == null ? null : quote.coupon().getId());
            // A grouped order shares its payment with others, so it is never reused alone
            if (sameCoupon
                    && order.getGroup() == null
                    && order.getAmount().compareTo(quote.finalAmount()) == 0
                    && order.getCurrency().equals(currency)) {
                return order;
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setCheckoutUrl(null);
            orderRepository.flush();
        }

        Order order = Order.builder()
                .orderNumber(OrderNumberGenerator.next())
                .student(student)
                .course(course)
                .coupon(quote.coupon())
                .listAmount(quote.listAmount())
                .discountAmount(quote.discountAmount())
                .amount(quote.finalAmount())
                .currency(currency)
                .status(OrderStatus.PENDING)
                .provider(provider)
                .build();
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public OrderGroup openGroup(String email, String couponCode, String currency, String provider) {
        // Locking the learner serializes parallel checkouts, as in openOrder
        Integer studentId = learnerAccountService.currentStudent(email).getId();
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));

        List<Course> courses = cartItemRepository.findByStudentIdOrderByAddedAtDescIdDesc(studentId).stream()
                .map(CartItem::getCourse)
                .filter(course -> course.getStatus() == CourseStatus.PUBLISHED)
                .filter(course -> !studentCourseRepository.existsByStudentIdAndCourseId(studentId, course.getId()))
                .toList();
        if (courses.isEmpty()) {
            throw new BusinessRuleException("Your cart is empty");
        }

        courses.forEach(course -> orderRepository
                .findByStudentIdAndCourseIdAndStatus(studentId, course.getId(), OrderStatus.PENDING)
                .ifPresent(this::cancelWithGroup));
        orderRepository.flush();

        Course couponCourse = StringUtils.hasText(couponCode) ? couponService.chooseCourse(couponCode, courses) : null;
        OrderGroup group = orderGroupRepository.save(OrderGroup.builder()
                .groupNumber(GROUP_PREFIX + OrderNumberGenerator.next())
                .amount(BigDecimal.ZERO)
                .currency(currency)
                .build());

        BigDecimal total = BigDecimal.ZERO;
        for (Course course : courses) {
            CouponQuote quote = course == couponCourse
                    ? couponService.applyToOrder(couponCode, course, studentId, null)
                    : CouponQuote.none(course);
            total = total.add(quote.finalAmount());
            orderRepository.save(Order.builder()
                    .orderNumber(OrderNumberGenerator.next())
                    .student(student)
                    .course(course)
                    .coupon(quote.coupon())
                    .group(group)
                    .listAmount(quote.listAmount())
                    .discountAmount(quote.discountAmount())
                    .amount(quote.finalAmount())
                    .currency(currency)
                    .status(OrderStatus.PENDING)
                    .provider(provider)
                    .build());
        }
        if (total.signum() > 0 && provider == null) {
            throw new ServiceUnavailableException("Paid checkout is not available yet");
        }
        group.setAmount(total);
        return group;
    }

    @Override
    @Transactional
    public CheckoutResponse completeFreeGroup(String groupNumber) {
        OrderGroup group = findGroup(groupNumber);
        if (group.getAmount().signum() != 0) {
            throw new BusinessRuleException("This order requires payment");
        }
        List<Order> orders = orderRepository.findByGroupIdForUpdate(group.getId());
        orders.stream().filter(order -> order.getStatus() == OrderStatus.PENDING).forEach(this::settle);
        return toCheckoutResponse(group, orders);
    }

    @Override
    @Transactional
    public CheckoutResponse attachGroupSession(String groupNumber, PaymentSession session) {
        OrderGroup group = findGroup(groupNumber);
        List<Order> orders = orderRepository.findByGroupIdForUpdate(group.getId());
        orders.forEach(order -> {
            // A webhook may already have settled the group while the gateway call was in flight
            order.setProviderReference(session.reference());
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setCheckoutUrl(session.checkoutUrl());
            }
        });
        return toCheckoutResponse(group, orders);
    }

    @Override
    @Transactional
    public void markGroupFailed(String groupNumber) {
        orderRepository.findByGroupIdForUpdate(findGroup(groupNumber).getId()).stream()
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .forEach(order -> {
                    order.setStatus(OrderStatus.FAILED);
                    order.setCheckoutUrl(null);
                });
    }

    private OrderGroup findGroup(String groupNumber) {
        return orderGroupRepository.findByGroupNumber(groupNumber)
                .orElseThrow(() -> ResourceNotFoundException.of("Order group", groupNumber));
    }

    /** A payment covers its whole group, so no order in it can be cancelled alone. */
    private void cancelWithGroup(Order order) {
        List<Order> orders = order.getGroup() == null
                ? List.of(order)
                : orderRepository.findByGroupIdForUpdate(order.getGroup().getId());
        orders.stream()
                .filter(candidate -> candidate.getStatus() == OrderStatus.PENDING)
                .forEach(candidate -> {
                    candidate.setStatus(OrderStatus.CANCELLED);
                    candidate.setCheckoutUrl(null);
                });
    }

    private CheckoutResponse toCheckoutResponse(OrderGroup group, List<Order> orders) {
        return new CheckoutResponse(
                group.getGroupNumber(),
                group.getAmount(),
                group.getCurrency(),
                orders.stream().map(Order::getCheckoutUrl).filter(Objects::nonNull).findFirst().orElse(null),
                orders.stream().map(this::toResponse).toList()
        );
    }

    @Override
    @Transactional
    public OrderResponse completeFreeOrder(String orderNumber) {
        Order order = lock(orderNumber);
        if (order.getAmount().signum() != 0) {
            throw new BusinessRuleException("This order requires payment");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            settle(order);
        }
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse attachSession(String orderNumber, PaymentSession session) {
        Order order = lock(orderNumber);
        // A webhook may already have settled the order while the gateway call was in flight
        order.setProviderReference(session.reference());
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setCheckoutUrl(session.checkoutUrl());
        }
        return toResponse(order);
    }

    @Override
    @Transactional
    public void markFailed(String orderNumber) {
        Order order = lock(orderNumber);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.FAILED);
            order.setCheckoutUrl(null);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findMine(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .map(student -> orderRepository.findByStudentIdOrderByCreatedAtDescIdDesc(student.getId())
                        .stream()
                        .map(this::toResponse)
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findMine(String email, String orderNumber) {
        return toResponse(findOwned(email, orderNumber));
    }

    @Override
    @Transactional
    public OrderResponse cancelMine(String email, String orderNumber) {
        findOwned(email, orderNumber);
        Order order = lock(orderNumber);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("Only a pending order can be cancelled");
        }
        cancelWithGroup(order);
        return toResponse(order);
    }

    @Override
    @Transactional
    public void applyPaymentEvent(PaymentEvent event) {
        if (event.orderNumber().startsWith(GROUP_PREFIX)) {
            applyGroupPaymentEvent(event);
            return;
        }
        Order order = lock(event.orderNumber());

        if (event.outcome() == PaymentEvent.Outcome.FAILED) {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.FAILED);
                order.setCheckoutUrl(null);
                notifyLearner(order, NotificationType.PAYMENT_FAILED, "Payment failed",
                        "Your payment for " + order.getCourse().getTitle() + " didn't go through. You haven't been enrolled.");
            }
            return;
        }

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.REFUNDED) {
            log.debug("Ignoring repeated payment event for order={}", order.getOrderNumber());
            return;
        }
        if (order.getAmount().compareTo(event.amount()) != 0 || !order.getCurrency().equals(event.currency())) {
            log.error(
                    "Payment mismatch for order={}: expected {} {}, got {} {}",
                    order.getOrderNumber(), order.getAmount(), order.getCurrency(), event.amount(), event.currency()
            );
            throw new BusinessRuleException("Payment does not match the order");
        }

        // Money was taken, so a late payment on a cancelled or failed order still counts
        settle(order);
    }

    private void applyGroupPaymentEvent(PaymentEvent event) {
        OrderGroup group = findGroup(event.orderNumber());
        List<Order> orders = orderRepository.findByGroupIdForUpdate(group.getId());

        if (event.outcome() == PaymentEvent.Outcome.FAILED) {
            orders.stream().filter(order -> order.getStatus() == OrderStatus.PENDING).forEach(order -> {
                order.setStatus(OrderStatus.FAILED);
                order.setCheckoutUrl(null);
                notifyLearner(order, NotificationType.PAYMENT_FAILED, "Payment failed",
                        "Your payment for " + order.getCourse().getTitle() + " didn't go through. You haven't been enrolled.");
            });
            return;
        }

        if (group.getAmount().compareTo(event.amount()) != 0 || !group.getCurrency().equals(event.currency())) {
            log.error(
                    "Payment mismatch for group={}: expected {} {}, got {} {}",
                    group.getGroupNumber(), group.getAmount(), group.getCurrency(), event.amount(), event.currency()
            );
            throw new BusinessRuleException("Payment does not match the order");
        }

        // Providers redeliver webhooks, and money taken still counts for a cancelled or failed order
        orders.stream()
                .filter(order -> order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.REFUNDED)
                .forEach(this::settle);
    }

    private void settle(Order order) {
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        order.setCheckoutUrl(null);

        Integer studentId = order.getStudent().getId();
        Integer courseId = order.getCourse().getId();
        if (studentCourseRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            log.warn("Order={} paid but learner is already enrolled; review for refund", order.getOrderNumber());
            return;
        }
        enrollmentService.create(new EnrollmentCreateRequest(studentId, courseId, order.getAmount(), null));
        log.info("Order={} paid, enrolled studentId={} courseId={}", order.getOrderNumber(), studentId, courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderPageResponse findAll(String status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        Page<Order> orders = StringUtils.hasText(status)
                ? orderRepository.findByStatus(parseStatus(status), pageable)
                : orderRepository.findAll(pageable);

        return new AdminOrderPageResponse(
                orders.map(this::toAdminResponse).getContent(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages()
        );
    }

    @Override
    @Transactional
    public OrderResponse refund(String orderNumber, PaymentGateway gateway) {
        Order order = lock(orderNumber);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessRuleException("Only a paid order can be refunded");
        }
        if (order.getAmount().signum() == 0) {
            throw new BusinessRuleException("This order was free, so there is nothing to refund");
        }

        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundedAt(Instant.now());
        enrollmentRepository.findByStudentIdAndCourseId(order.getStudent().getId(), order.getCourse().getId())
                .ifPresent(enrollment -> enrollmentService.delete(enrollment.getId()));
        notifyLearner(order, NotificationType.ORDER_REFUNDED, "Order refunded",
                "Your order for " + order.getCourse().getTitle() + " was refunded.");
        orderRepository.flush();

        // Last on purpose: a failed refund rolls all of the above back
        gateway.refund(order);
        log.info("Refunded order={}", orderNumber);
        return toResponse(order);
    }

    private void notifyLearner(Order order, NotificationType type, String title, String message) {
        notificationService.notifyStudent(
                order.getStudent().getId(), type, title, message, "/courses/" + order.getCourse().getSlug());
    }

    private Order lock(String orderNumber) {
        return orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderNumber));
    }

    // Someone else's order reads as missing, so order numbers can't be probed
    private Order findOwned(String email, String orderNumber) {
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> orderRepository.findByOrderNumber(orderNumber)
                        .filter(order -> order.getStudent().getId().equals(student.getId())))
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderNumber));
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown order status: " + status);
        }
    }

    private OrderResponse toResponse(Order order) {
        Course course = order.getCourse();
        return new OrderResponse(
                order.getOrderNumber(),
                course.getSlug(),
                course.getTitle(),
                course.getThumbnail(),
                order.getListAmount(),
                order.getDiscountAmount(),
                order.getCoupon() == null ? null : order.getCoupon().getCode(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getCheckoutUrl(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getRefundedAt()
        );
    }

    private AdminOrderResponse toAdminResponse(Order order) {
        return new AdminOrderResponse(
                toResponse(order),
                order.getStudent().getId(),
                order.getStudent().getEmail(),
                order.getProvider(),
                order.getProviderReference()
        );
    }
}
