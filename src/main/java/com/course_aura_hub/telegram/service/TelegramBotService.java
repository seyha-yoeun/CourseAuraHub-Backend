package com.course_aura_hub.telegram.service;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.certificate.dto.CertificateResponse;
import com.course_aura_hub.certificate.service.CertificateService;
import com.course_aura_hub.curriculum.dto.CourseProgress;
import com.course_aura_hub.dashboard.dto.DashboardStatsResponse;
import com.course_aura_hub.dashboard.service.DashboardService;
import com.course_aura_hub.learner.dto.MyCourseResponse;
import com.course_aura_hub.learner.facade.LearnerFacade;
import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.telegram.client.TelegramClient;
import com.course_aura_hub.telegram.client.TelegramException;
import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.telegram.dto.TelegramUpdate;
import com.course_aura_hub.telegram.model.TelegramLink;
import com.course_aura_hub.telegram.repository.TelegramLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.course_aura_hub.telegram.service.TelegramMessages.escape;

/**
 * Answers messages sent to the bot. Everything shown comes from the account connected to the chat, and the
 * account is reloaded per message, so a disabled account or a removed admin role takes effect at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private static final int MAX_ITEMS = 8;
    private static final int RECENT_ITEMS = 5;

    private final TelegramLinkRepository linkRepository;
    private final TelegramLinkService linkService;
    private final UserRepository userRepository;
    private final LearnerFacade learnerFacade;
    private final CertificateService certificateService;
    private final DashboardService dashboardService;
    private final NotificationService notificationService;
    private final TelegramClient client;
    private final TelegramMessages messages;
    private final TelegramProperties properties;

    public void handle(TelegramUpdate update) {
        TelegramUpdate.Message message = update.message();
        // Groups are refused: everyone in them would read this account's messages
        if (message == null || message.text() == null || message.chat() == null || !"private".equals(message.chat().type())) {
            return;
        }

        long chatId = message.chat().id();
        try {
            reply(chatId, respond(message, chatId));
        } catch (TelegramException ex) {
            log.warn("Telegram reply failed: {}", ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Telegram command failed", ex);
            reply(chatId, "Something went wrong. Please try again in a moment.");
        }
    }

    private String respond(TelegramUpdate.Message message, long chatId) {
        String[] parts = message.text().trim().split("\\s+", 2);
        String command = parts[0].split("@")[0].toLowerCase(Locale.ROOT);

        if (command.equals("/start") && parts.length == 2) {
            return connect(parts[1].trim(), chatId, message.from());
        }

        Optional<TelegramLink> link = linkRepository.findByChatId(chatId);
        Optional<User> account = link.flatMap(found -> userRepository.findById(found.getUserId())).filter(User::isEnabled);
        if (account.isEmpty()) {
            return "👋 <b>Welcome to CourseAuraHub</b>\nOpen <b>Notifications</b> in your account on "
                    + escape(messages.absolute("/account/notifications")) + " and choose <b>Connect Telegram</b>.";
        }

        User user = account.get();
        boolean admin = user.getRole() == Role.ROLE_ADMIN;
        return switch (command) {
            case "/status" -> status(link.get());
            case "/notifications" -> unreadNotifications(user);
            case "/mute" -> setEnabled(user, false);
            case "/unmute" -> setEnabled(user, true);
            case "/disconnect" -> disconnect(user);
            case "/progress" -> admin ? help(true) : progress(user);
            case "/certificates" -> admin ? help(true) : certificates(user);
            case "/stats" -> admin ? stats() : help(false);
            case "/enrollments" -> admin ? recentEnrollments() : help(false);
            default -> help(admin);
        };
    }

    private String connect(String token, long chatId, TelegramUpdate.From from) {
        String telegramName = from == null ? null : from.username() != null ? "@" + from.username() : from.firstName();
        return linkService.connect(token, chatId, telegramName)
                .map(user -> "✅ <b>Telegram connected</b>\nHi " + escape(user.getFirstName()) + ", your notifications will arrive here.\n\n"
                        + help(user.getRole() == Role.ROLE_ADMIN))
                .orElse("⚠️ This link has expired or was already used. Choose <b>Connect Telegram</b> in your account to get a new one.");
    }

    private String help(boolean admin) {
        String role = admin
                ? "/stats - Revenue, students and courses\n/enrollments - Latest enrollments\n"
                : "/progress - Your courses and progress\n/certificates - Certificates you've earned\n";
        return "<b>What I can do</b>\n" + role
                + "/notifications - Your unread notifications\n"
                + "/mute - Pause notifications\n"
                + "/unmute - Resume notifications\n"
                + "/status - Your connection\n"
                + "/disconnect - Disconnect this chat";
    }

    private String status(TelegramLink link) {
        return link.isEnabled()
                ? "🔔 Notifications are on for this chat."
                : "🔕 Notifications are paused. Send /unmute to resume.";
    }

    private String setEnabled(User user, boolean enabled) {
        linkService.setEnabled(user.getId(), enabled);
        return enabled ? "🔔 Notifications are back on." : "🔕 Notifications paused. Send /unmute to resume.";
    }

    private String disconnect(User user) {
        linkService.disconnect(user.getId());
        return "👋 Disconnected. You won't get notifications here any more.";
    }

    private String unreadNotifications(User user) {
        List<NotificationResponse> unread = notificationService.findMine(user.getId(), true, 0, RECENT_ITEMS).items();
        if (unread.isEmpty()) {
            return "✅ You're all caught up.";
        }
        StringBuilder text = new StringBuilder("🔔 <b>Unread notifications</b>");
        unread.forEach(item -> text.append("\n\n<b>").append(escape(item.title())).append("</b>\n").append(escape(item.message())));
        return text.toString();
    }

    private String progress(User user) {
        List<MyCourseResponse> courses = learnerFacade.findMyCourses(user.getEmail());
        if (courses.isEmpty()) {
            return "📚 You aren't enrolled in a course yet. Browse the catalog at " + escape(messages.absolute("/courses"));
        }
        StringBuilder text = new StringBuilder("📚 <b>Your learning</b>");
        courses.stream().limit(MAX_ITEMS).forEach(course -> {
            CourseProgress progress = course.progress();
            text.append("\n\n<b>").append(escape(course.title())).append("</b>\n");
            text.append(progress.completed()
                    ? "✅ Completed"
                    : progress.progressPercent() + "% · " + progress.completedLessons() + " of " + progress.totalLessons() + " lessons");
        });
        return text.toString();
    }

    private String certificates(User user) {
        List<CertificateResponse> earned = certificateService.findMine(user.getEmail());
        if (earned.isEmpty()) {
            return "🏅 No certificates yet. Finish every lesson in a course to earn one.";
        }
        StringBuilder text = new StringBuilder("🏅 <b>Your certificates</b>");
        earned.stream().limit(MAX_ITEMS).forEach(certificate -> text
                .append("\n\n<b>").append(escape(certificate.courseTitle())).append("</b>\n")
                .append(escape(messages.absolute("/certificates/" + certificate.certificateNumber()))));
        return text.toString();
    }

    private String stats() {
        DashboardStatsResponse.Kpis kpis = dashboardService.getStats().kpis();
        return "📊 <b>Platform overview</b>"
                + "\nRevenue: " + money(kpis.totalRevenue())
                + "\nStudents: " + kpis.totalStudents()
                + "\nCourses: " + kpis.publishedCourses() + " published of " + kpis.totalCourses()
                + "\nAverage rating: " + kpis.avgRating();
    }

    private String recentEnrollments() {
        List<DashboardStatsResponse.RecentEnrollment> recent = dashboardService.getStats().recentEnrollments();
        if (recent.isEmpty()) {
            return "🧑‍🎓 No enrollments yet.";
        }
        StringBuilder text = new StringBuilder("🧑‍🎓 <b>Latest enrollments</b>");
        recent.stream().limit(RECENT_ITEMS).forEach(item -> text
                .append("\n\n<b>").append(escape(item.studentName())).append("</b>\n")
                .append(escape(item.courseTitle())).append(" · ").append(item.enrolledAt()));
        return text.toString();
    }

    private String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "%,.2f", amount);
    }

    private void reply(long chatId, String html) {
        try {
            client.sendMessage(chatId, html, null);
        } catch (TelegramException ex) {
            log.warn("Telegram reply failed: {}", ex.getMessage());
        }
    }
}
