package com.course_aura_hub.chat;

import com.course_aura_hub.chat.model.ChatMessage;
import com.course_aura_hub.chat.model.MessageKind;
import com.course_aura_hub.chat.service.ChatViews;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** List previews and notifications show message text without its formatting marks. */
class ChatPreviewTest {

    private static String preview(String body) {
        return ChatViews.preview(ChatMessage.builder().kind(MessageKind.TEXT).body(body).build(), 0);
    }

    @Test
    void formattingMarks_areLeftOut() {
        assertThat(preview("Run **npm install** then _restart_ and ~~wait~~ check `my_file.ts`"))
                .isEqualTo("Run npm install then restart and wait check my_file.ts");
        assertThat(preview("Fix:\n```js\nconst a = 1\n```")).isEqualTo("Fix: const a = 1");
    }

    @Test
    void ordinaryUnderscoresAndStars_stay() {
        assertThat(preview("snake_case_name and 2*3*4")).isEqualTo("snake_case_name and 2*3*4");
    }

    @Test
    void onlyMarks_readAsAFile() {
        assertThat(ChatViews.preview(ChatMessage.builder().kind(MessageKind.TEXT).body("``````").build(), 1))
                .isEqualTo("Sent a file");
    }
}
