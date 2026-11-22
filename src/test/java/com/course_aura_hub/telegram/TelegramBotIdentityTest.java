package com.course_aura_hub.telegram;

import com.course_aura_hub.telegram.client.TelegramClient;
import com.course_aura_hub.telegram.client.TelegramException;
import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.telegram.service.TelegramBotIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Connect links must name a real bot, or t.me sends people to telegram.org. */
@ExtendWith(OutputCaptureExtension.class)
class TelegramBotIdentityTest {

    private final TelegramClient client = mock(TelegramClient.class);

    private TelegramBotIdentity identity(String configured) {
        return new TelegramBotIdentity(client, new TelegramProperties("token", configured, null, null, "http://localhost:3000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CourseAuraHubBot", "@CourseAuraHubBot", " CourseAuraHubBot ", "https://t.me/CourseAuraHubBot",
            "t.me/CourseAuraHubBot", "http://telegram.me/CourseAuraHubBot", "https://t.me/CourseAuraHubBot?start=x"})
    void theConfiguredNameIsTidied_whenTelegramCanNotBeAsked(String configured) {
        when(client.getMe()).thenThrow(new TelegramException("getMe failed: ConnectException", false));
        TelegramBotIdentity identity = identity(configured);

        identity.verify();

        assertThat(identity.username()).isEqualTo("CourseAuraHubBot");
    }

    @Test
    void telegramsAnswerWins_andAMismatchIsLogged(CapturedOutput output) {
        when(client.getMe()).thenReturn("CourseAuraHubBot");
        TelegramBotIdentity identity = identity("CourseAuraHub");

        identity.verify();

        assertThat(identity.username()).isEqualTo("CourseAuraHubBot");
        assertThat(output.getOut()).contains("the token belongs to @CourseAuraHubBot");
    }

    @Test
    void aMatchingNameIsNotReported(CapturedOutput output) {
        when(client.getMe()).thenReturn("CourseAuraHubBot");
        TelegramBotIdentity identity = identity("@courseaurahubbot");

        identity.verify();

        assertThat(identity.username()).isEqualTo("CourseAuraHubBot");
        assertThat(output.getOut()).doesNotContain("the token belongs to");
    }

    @Test
    void aNameThatIsNotABotUsernameIsReported_whenTheTokenIsWrong(CapturedOutput output) {
        when(client.getMe()).thenThrow(new TelegramException("getMe returned 401", false, 401));
        TelegramBotIdentity identity = identity("Course Aura Hub");

        identity.verify();

        assertThat(output.getOut()).contains("check TELEGRAM_BOT_TOKEN");
        assertThat(output.getOut()).contains("isn't a bot username");
    }
}
