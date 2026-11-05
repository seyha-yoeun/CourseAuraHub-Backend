package com.course_aura_hub.organization.service;

import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.auth.security.SecureTokenGenerator;
import com.course_aura_hub.auth.security.TokenHasher;
import com.course_aura_hub.certificate.model.CourseCertificate;
import com.course_aura_hub.certificate.repository.CourseCertificateRepository;
import com.course_aura_hub.common.email.EmailDeliveryException;
import com.course_aura_hub.common.email.EmailMessage;
import com.course_aura_hub.common.email.EmailSender;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.curriculum.repository.LessonProgressRepository;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.enrollment.dto.EnrollmentCreateRequest;
import com.course_aura_hub.enrollment.service.EnrollmentService;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.organization.dto.OrganizationDtos;
import com.course_aura_hub.organization.model.Organization;
import com.course_aura_hub.organization.model.OrganizationInvite;
import com.course_aura_hub.organization.model.OrganizationMember;
import com.course_aura_hub.organization.model.OrganizationRole;
import com.course_aura_hub.organization.model.OrganizationSeat;
import com.course_aura_hub.organization.model.SeatAssignment;
import com.course_aura_hub.organization.repository.OrganizationInviteRepository;
import com.course_aura_hub.organization.repository.OrganizationMemberRepository;
import com.course_aura_hub.organization.repository.OrganizationRepository;
import com.course_aura_hub.organization.repository.OrganizationSeatRepository;
import com.course_aura_hub.organization.repository.SeatAssignmentRepository;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrganizationServiceImpl implements OrganizationService {

    private static final Duration INVITE_VALIDITY = Duration.ofDays(14);
    private static final String TOKEN_PURPOSE = "organization-invite";

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationSeatRepository seatRepository;
    private final SeatAssignmentRepository assignmentRepository;
    private final OrganizationInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentService enrollmentService;
    private final LearnerAccountService learnerAccountService;
    private final StudentCourseRepository studentCourseRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final CourseCertificateRepository certificateRepository;
    private final NotificationService notificationService;
    private final EmailSender emailSender;
    private final SecureTokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final String inviteUrl;

    public OrganizationServiceImpl(
            OrganizationRepository organizationRepository, OrganizationMemberRepository memberRepository,
            OrganizationSeatRepository seatRepository, SeatAssignmentRepository assignmentRepository,
            OrganizationInviteRepository inviteRepository, UserRepository userRepository, CourseRepository courseRepository,
            EnrollmentService enrollmentService, LearnerAccountService learnerAccountService,
            StudentCourseRepository studentCourseRepository, LessonRepository lessonRepository,
            LessonProgressRepository progressRepository, CourseCertificateRepository certificateRepository,
            NotificationService notificationService, EmailSender emailSender, SecureTokenGenerator tokenGenerator,
            TokenHasher tokenHasher,
            @Value("${app.organizations.invite-url:http://localhost:3000/organizations/join}") String inviteUrl
    ) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.seatRepository = seatRepository;
        this.assignmentRepository = assignmentRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentService = enrollmentService;
        this.learnerAccountService = learnerAccountService;
        this.studentCourseRepository = studentCourseRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.certificateRepository = certificateRepository;
        this.notificationService = notificationService;
        this.emailSender = emailSender;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.inviteUrl = inviteUrl;
    }

    @Override
    @Transactional
    public OrganizationDtos.Detail create(OrganizationDtos.CreateRequest request, UUID adminId) {
        User owner = userRepository.findByEmail(request.ownerEmail().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessRuleException("The owner needs an account first"));
        Organization organization = organizationRepository.save(Organization.builder()
                .slug(uniqueSlug(request.name()))
                .name(request.name().trim())
                .createdBy(adminId)
                .build());
        memberRepository.save(OrganizationMember.builder()
                .id(new OrganizationMember.Id(organization.getId(), owner.getId()))
                .role(OrganizationRole.OWNER)
                .build());
        log.info("Organization created id={} slug={}", organization.getId(), organization.getSlug());
        return detail(organization, OrganizationRole.OWNER, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationDtos.Summary> findAll() {
        return organizationRepository.findAllByOrderByNameAsc().stream()
                .map(organization -> summary(organization, null))
                .toList();
    }

    @Override
    @Transactional
    public OrganizationDtos.Seat setSeats(String slug, OrganizationDtos.SeatRequest request) {
        Organization organization = requireOrganization(slug);
        Course course = courseRepository.findBySlug(request.courseSlug())
                .orElseThrow(() -> ResourceNotFoundException.of("Course", request.courseSlug()));
        OrganizationSeat seat = seatRepository.findForUpdate(organization.getId(), course.getId())
                .orElseGet(() -> OrganizationSeat.builder().organizationId(organization.getId()).course(course).total(0).build());
        long used = seat.getId() == null ? 0 : assignmentRepository.countByIdSeatId(seat.getId());
        if (request.total() < used) {
            throw new BusinessRuleException(used + " seats are already in use");
        }
        seat.setTotal(request.total());
        seatRepository.save(seat);
        log.info("Organization seats set organizationId={} courseId={} total={}", organization.getId(), course.getId(), request.total());
        return new OrganizationDtos.Seat(course.getSlug(), course.getTitle(), seat.getTotal(), (int) used);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationDtos.Summary> findMine(UUID userId) {
        List<OrganizationMember> memberships = memberRepository.findByIdUserId(userId);
        Map<Integer, Organization> organizations = organizationRepository.findAllById(
                        memberships.stream().map(member -> member.getId().getOrganizationId()).toList())
                .stream().collect(Collectors.toMap(Organization::getId, Function.identity()));
        return memberships.stream()
                .map(member -> summary(organizations.get(member.getId().getOrganizationId()), member.getRole()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationDtos.Detail find(Actor actor, String slug) {
        Organization organization = requireOrganization(slug);
        OrganizationRole role = requireMembership(organization, actor).getRole();
        return detail(organization, role, role.canManage() || actor.admin());
    }

    @Override
    @Transactional
    public OrganizationDtos.Invite invite(Actor actor, String slug, OrganizationDtos.InviteRequest request) {
        Organization organization = requireOrganization(slug);
        OrganizationRole inviter = requireManager(organization, actor);
        OrganizationRole role = StringUtils.hasText(request.role()) ? parseRole(request.role()) : OrganizationRole.MEMBER;
        if (role == OrganizationRole.OWNER || (role == OrganizationRole.MANAGER && inviter != OrganizationRole.OWNER && !actor.admin())) {
            throw new ForbiddenActionException("Only the owner can invite managers");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String token = tokenGenerator.urlSafeToken(32);
        OrganizationInvite invite = inviteRepository.save(OrganizationInvite.builder()
                .organizationId(organization.getId())
                .email(email)
                .role(role)
                .tokenHash(tokenHasher.hash(TOKEN_PURPOSE, token))
                .invitedBy(actor.userId())
                .expiresAt(Instant.now().plus(INVITE_VALIDITY))
                .build());
        String link = UriComponentsBuilder.fromUriString(inviteUrl).queryParam("token", token).build().toUriString();
        afterCommit(() -> sendInvite(email, organization.getName(), link));
        log.info("Organization invite created organizationId={} inviteId={}", organization.getId(), invite.getId());
        return new OrganizationDtos.Invite(invite.getId(), email, role.name().toLowerCase(Locale.ROOT), invite.getExpiresAt(), link);
    }

    @Override
    @Transactional
    public OrganizationDtos.Summary accept(Actor actor, String token) {
        OrganizationInvite invite = inviteRepository.findByTokenHash(tokenHasher.hash(TOKEN_PURPOSE, token.trim()))
                .filter(found -> found.getAcceptedAt() == null && found.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleException("This invitation is invalid or has expired"));
        if (!invite.getEmail().equalsIgnoreCase(actor.email())) {
            throw new ForbiddenActionException("This invitation was sent to a different email address");
        }
        Organization organization = organizationRepository.findById(invite.getOrganizationId()).orElseThrow();
        OrganizationMember.Id id = new OrganizationMember.Id(organization.getId(), actor.userId());
        OrganizationMember member = memberRepository.findById(id)
                .orElseGet(() -> memberRepository.save(OrganizationMember.builder().id(id).role(invite.getRole()).build()));
        invite.setAcceptedAt(Instant.now());
        log.info("Organization invite accepted organizationId={} userId={}", organization.getId(), actor.userId());
        return summary(organization, member.getRole());
    }

    @Override
    @Transactional
    public OrganizationDtos.Seat assignSeat(Actor actor, String slug, String courseSlug, UUID memberId) {
        Organization organization = requireOrganization(slug);
        requireManager(organization, actor);
        memberRepository.findById(new OrganizationMember.Id(organization.getId(), memberId))
                .orElseThrow(() -> new BusinessRuleException("Only members of this organization can get a seat"));
        Course course = courseRepository.findBySlug(courseSlug).orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        OrganizationSeat seat = seatRepository.findForUpdate(organization.getId(), course.getId())
                .orElseThrow(() -> new BusinessRuleException("This organization has no seats for " + course.getTitle()));
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleException("Seats can only be used on published courses");
        }
        long used = assignmentRepository.countByIdSeatId(seat.getId());
        if (used >= seat.getTotal()) {
            throw new BusinessRuleException("All seats for this course are in use");
        }
        User user = userRepository.findById(memberId).orElseThrow(() -> ResourceNotFoundException.of("User", memberId));
        Student student = learnerAccountService.currentStudent(user.getEmail());
        if (studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new DuplicateResourceException("This member already has access to the course");
        }
        enrollmentService.create(new EnrollmentCreateRequest(student.getId(), course.getId(), BigDecimal.ZERO, null));
        assignmentRepository.save(SeatAssignment.builder()
                .id(new SeatAssignment.Id(seat.getId(), memberId))
                .assignedBy(actor.userId())
                .build());
        notificationService.notifyUsers(List.of(memberId), NotificationType.ENROLLED, "You're enrolled",
                "%s gave you access to %s.".formatted(organization.getName(), course.getTitle()), "/learn/" + course.getSlug());
        log.info("Seat assigned organizationId={} courseId={} userId={}", organization.getId(), course.getId(), memberId);
        return new OrganizationDtos.Seat(course.getSlug(), course.getTitle(), seat.getTotal(), (int) used + 1);
    }

    @Override
    @Transactional
    public void removeMember(Actor actor, String slug, UUID memberId) {
        Organization organization = requireOrganization(slug);
        OrganizationRole remover = requireManager(organization, actor);
        OrganizationMember member = memberRepository.findById(new OrganizationMember.Id(organization.getId(), memberId))
                .orElseThrow(() -> ResourceNotFoundException.of("Member", memberId));
        if (member.getRole() == OrganizationRole.OWNER) {
            throw new BusinessRuleException("The owner can't be removed");
        }
        if (member.getRole() == OrganizationRole.MANAGER && remover != OrganizationRole.OWNER && !actor.admin()) {
            throw new ForbiddenActionException("Only the owner can remove a manager");
        }
        // Their enrollments stay, so their used seats stay used
        memberRepository.delete(member);
        log.info("Organization member removed organizationId={} userId={}", organization.getId(), memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationDtos.MemberProgress> progress(Actor actor, String slug) {
        Organization organization = requireOrganization(slug);
        requireManager(organization, actor);
        List<OrganizationSeat> seats = seatRepository.findByOrganizationIdOrderByIdAsc(organization.getId());
        Map<Integer, OrganizationSeat> seatsById = seats.stream().collect(Collectors.toMap(OrganizationSeat::getId, Function.identity()));
        Map<UUID, List<OrganizationSeat>> seatsByUser = assignmentRepository.findByIdSeatIdIn(seatsById.keySet()).stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getId().getUserId(),
                        Collectors.mapping(assignment -> seatsById.get(assignment.getId().getSeatId()), Collectors.toList())));

        List<OrganizationDtos.MemberProgress> result = new ArrayList<>();
        for (OrganizationMember member : memberRepository.findByIdOrganizationIdOrderByJoinedAtAsc(organization.getId())) {
            User user = userRepository.findById(member.getId().getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            Optional<Student> student = learnerAccountService.findCurrentStudent(user.getEmail());
            List<OrganizationDtos.CourseProgress> courses = seatsByUser.getOrDefault(user.getId(), List.of()).stream()
                    .map(seat -> courseProgress(seat.getCourse(), student.orElse(null)))
                    .toList();
            result.add(new OrganizationDtos.MemberProgress(user.getId(), fullName(user),
                    member.getRole().name().toLowerCase(Locale.ROOT), courses));
        }
        return result;
    }

    private OrganizationDtos.CourseProgress courseProgress(Course course, Student student) {
        int total = (int) lessonRepository.countByCourseId(course.getId());
        if (student == null) {
            return new OrganizationDtos.CourseProgress(course.getSlug(), course.getTitle(), 0, total, false, null);
        }
        int done = (int) progressRepository.countCompleted(student.getId(), course.getId());
        String certificate = certificateRepository.findByStudentIdAndCourseId(student.getId(), course.getId())
                .map(CourseCertificate::getCertificateNumber).orElse(null);
        boolean completed = studentCourseRepository.findByStudentIdAndCourseId(student.getId(), course.getId())
                .map(link -> link.isCompleted()).orElse(false);
        return new OrganizationDtos.CourseProgress(course.getSlug(), course.getTitle(), done, total, completed, certificate);
    }

    private OrganizationDtos.Detail detail(Organization organization, OrganizationRole role, boolean showMembers) {
        List<OrganizationDtos.Seat> seats = seatRepository.findByOrganizationIdOrderByIdAsc(organization.getId()).stream()
                .map(seat -> new OrganizationDtos.Seat(seat.getCourse().getSlug(), seat.getCourse().getTitle(), seat.getTotal(),
                        (int) assignmentRepository.countByIdSeatId(seat.getId())))
                .toList();
        List<OrganizationDtos.Member> members = !showMembers ? List.of()
                : memberRepository.findByIdOrganizationIdOrderByJoinedAtAsc(organization.getId()).stream()
                .map(member -> userRepository.findById(member.getId().getUserId())
                        .map(user -> new OrganizationDtos.Member(user.getId(), fullName(user), user.getEmail(),
                                member.getRole().name().toLowerCase(Locale.ROOT), member.getJoinedAt()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new OrganizationDtos.Detail(organization.getSlug(), organization.getName(),
                role == null ? null : role.name().toLowerCase(Locale.ROOT), seats, members);
    }

    private OrganizationDtos.Summary summary(Organization organization, OrganizationRole role) {
        return new OrganizationDtos.Summary(organization.getSlug(), organization.getName(),
                role == null ? null : role.name().toLowerCase(Locale.ROOT),
                (int) memberRepository.countByIdOrganizationId(organization.getId()));
    }

    /** Admins act as owners of every organization. */
    private OrganizationMember requireMembership(Organization organization, Actor actor) {
        return memberRepository.findById(new OrganizationMember.Id(organization.getId(), actor.userId()))
                .or(() -> actor.admin()
                        ? Optional.of(OrganizationMember.builder().role(OrganizationRole.OWNER).build())
                        : Optional.empty())
                // Non-members can't tell an organization exists
                .orElseThrow(() -> ResourceNotFoundException.of("Organization", organization.getSlug()));
    }

    private OrganizationRole requireManager(Organization organization, Actor actor) {
        OrganizationRole role = requireMembership(organization, actor).getRole();
        if (!role.canManage()) {
            throw new ForbiddenActionException("Only the organization's managers can do this");
        }
        return role;
    }

    private Organization requireOrganization(String slug) {
        return organizationRepository.findBySlug(slug).orElseThrow(() -> ResourceNotFoundException.of("Organization", slug));
    }

    private void sendInvite(String email, String organizationName, String link) {
        String text = "You've been invited to join " + organizationName + " on CourseAuraHub.\n\n"
                + "Sign in or create an account with this email address, then open this link to join:\n" + link
                + "\n\nThe invitation expires in 14 days.";
        String html = "<p>You've been invited to join <strong>" + org.springframework.web.util.HtmlUtils.htmlEscape(organizationName)
                + "</strong> on CourseAuraHub.</p><p>Sign in or create an account with this email address, then "
                + "<a href=\"" + org.springframework.web.util.HtmlUtils.htmlEscape(link) + "\">join the organization</a>.</p>"
                + "<p>The invitation expires in 14 days.</p>";
        try {
            emailSender.send(new EmailMessage(email, "Join " + organizationName + " on CourseAuraHub", text, html));
        } catch (EmailDeliveryException ex) {
            log.warn("Organization invite email not delivered");
        }
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

    private String uniqueSlug(String name) {
        String base = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-").replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(base)) {
            base = "organization";
        }
        String candidate = base;
        int suffix = 2;
        while (organizationRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static OrganizationRole parseRole(String value) {
        try {
            return OrganizationRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Role must be member or manager");
        }
    }

    private static String fullName(User user) {
        return ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
    }
}
