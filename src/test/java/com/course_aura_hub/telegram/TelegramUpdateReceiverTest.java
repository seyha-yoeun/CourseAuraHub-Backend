package com.course_aura_hub.telegram;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import com.course_aura_hub.telegram.client.TelegramClient;
import com.course_aura_hub.telegram.client.TelegramException;
import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.telegram.dto.TelegramUpdate;
import com.course_aura_hub.telegram.service.TelegramBotIdentity;
import com.course_aura_hub.telegram.service.TelegramBotService;
import com.course_aura_hub.telegram.service.TelegramUpdateReceiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Polling hands updates to the same handler as the webhook, once each. */
@ExtendWith(OutputCaptureExtension.class)
class TelegramUpdateReceiverTest {

    private final TelegramClient client = mock(TelegramClient.class);
    private final TelegramBotService botService = mock(TelegramBotService.class);
    private final ClusterJobLock jobLock = mock(ClusterJobLock.class);
    private final TelegramBotIdentity identity = mock(TelegramBotIdentity.class);

    @BeforeEach
    void runJobsInline() {
        when(jobLock.runExclusively(any(), any(), any(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(3).run();
            return true;
        });
    }

    private TelegramUpdateReceiver receiver(String webhookUrl) {
        return new TelegramUpdateReceiver(client, botService,
                new TelegramProperties("token", "TestBot", webhookUrl, null, "http://localhost:3000"), jobLock, identity);
    }

    private static TelegramUpdate update(long id) {
        return new TelegramUpdate(id, new TelegramUpdate.Message("/help",
                new TelegramUpdate.Chat(42, "private"), new TelegramUpdate.From("Ann", "ann")));
    }

    @Test
    void handlesEachUpdateInOrder_thenConfirmsTheBatch_andAsksFromTheNextOffset() {
        TelegramUpdate first = update(7);
        TelegramUpdate second = update(8);
        when(client.getUpdates(0)).thenReturn(List.of(first, second));
        when(client.getUpdates(9)).thenReturn(List.of());
        TelegramUpdateReceiver receiver = receiver(null);

        receiver.poll();
        receiver.poll();

        InOrder order = inOrder(client, botService);
        order.verify(client).getUpdates(0);
        order.verify(botService).handle(first);
        order.verify(botService).handle(second);
        // Confirmed before the next poll, so a restart in between doesn't deliver them again
        order.verify(client).confirmUpdates(9);
        order.verify(client).getUpdates(9);
        verify(client, times(1)).confirmUpdates(anyLong());
    }

    @Test
    void nothingToConfirm_whenNoUpdatesArrive() {
        when(client.getUpdates(0)).thenReturn(List.of());

        receiver(null).poll();

        verify(client, never()).confirmUpdates(anyLong());
        verifyNoInteractions(botService);
    }

    @Test
    void doesNotPoll_whenAWebhookIsSet() {
        receiver("https://example.com/api/v1/telegram/webhook").poll();

        verifyNoInteractions(client, botService, jobLock);
    }

    @Test
    void aFailureIsLoggedOnce_withAHint_andRecoveryIsLogged(CapturedOutput output) {
        when(client.getUpdates(0))
                .thenThrow(new TelegramException("getUpdates returned 409", false, 409))
                .thenThrow(new TelegramException("getUpdates returned 409", false, 409))
                .thenReturn(List.of());
        TelegramUpdateReceiver receiver = receiver(null);

        receiver.poll();
        receiver.poll();
        receiver.poll();

        assertThat(output.getOut().split("Telegram polling failed", -1)).hasSize(2);
        assertThat(output.getOut()).contains("getUpdates returned 409 (another program is polling this bot");
        assertThat(output.getOut()).contains("Telegram polling works again");
    }
}
