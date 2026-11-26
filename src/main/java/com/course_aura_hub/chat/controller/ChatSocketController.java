package com.course_aura_hub.chat.controller;

import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.service.ChatDispatcher;
import com.course_aura_hub.chat.service.ChatService;
import com.course_aura_hub.chat.service.ChatSocketTickets;
import com.course_aura_hub.chat.socket.ChatPrincipal;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * STOMP commands under {@code /app/chat.*}. Replies (acks and errors) go only to the session that sent the
 * command, on {@code /user/queue/chat}; everything else arrives there as events from {@code ChatDispatcher}.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;
    private final ChatSocketTickets tickets;

    @MessageMapping("chat.send")
    @SendToUser(destinations = ChatDispatcher.USER_QUEUE, broadcast = false)
    public ChatDtos.Event send(@Payload ChatDtos.SocketSend command, Principal principal) {
        try {
            ChatDtos.Message message = chatService.send(actor(principal), command.conversationId(),
                    new ChatDtos.SendMessageRequest(command.clientMessageId(), command.body(), command.attachmentIds()));
            Map<String, Object> data = new HashMap<>();
            data.put("clientMessageId", command.clientMessageId());
            data.put("message", message);
            return new ChatDtos.Event("ack", data);
        } catch (RuntimeException ex) {
            ChatDtos.Event error = error(ex);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) error.data();
            data.put("clientMessageId", command.clientMessageId());
            data.put("conversationId", command.conversationId());
            return error;
        }
    }

    @MessageMapping("chat.read")
    public void read(@Payload ChatDtos.SocketCursor command, Principal principal) {
        chatService.markRead(actor(principal), command.conversationId(), command.messageId());
    }

    @MessageMapping("chat.delivered")
    public void delivered(@Payload ChatDtos.SocketCursor command, Principal principal) {
        chatService.markDelivered(actor(principal), command.conversationId(), command.messageId());
    }

    @MessageMapping("chat.typing")
    public void typing(@Payload ChatDtos.SocketTyping command, Principal principal) {
        chatService.typing(actor(principal), command.conversationId());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ChatDispatcher.USER_QUEUE, broadcast = false)
    public ChatDtos.Event handle(Exception ex) {
        return error(ex);
    }

    private Actor actor(Principal principal) {
        if (!(principal instanceof ChatPrincipal chat)) {
            throw new ForbiddenActionException("Not authenticated");
        }
        return tickets.actor(chat.userId());
    }

    private static ChatDtos.Event error(Throwable ex) {
        Throwable cause = ex;
        while (!isDomain(cause) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String code;
        String message;
        if (cause instanceof ResourceNotFoundException) {
            code = "not_found";
            message = cause.getMessage();
        } else if (cause instanceof ForbiddenActionException) {
            code = "forbidden";
            message = cause.getMessage();
        } else if (cause instanceof BusinessRuleException) {
            code = "invalid";
            message = cause.getMessage();
        } else if (cause instanceof DuplicateResourceException) {
            code = "conflict";
            message = cause.getMessage();
        } else {
            log.warn("Chat command failed", ex);
            code = "error";
            message = "Something went wrong. Try again.";
        }
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("message", message);
        return new ChatDtos.Event("error", data);
    }

    private static boolean isDomain(Throwable ex) {
        return ex instanceof ResourceNotFoundException || ex instanceof ForbiddenActionException
                || ex instanceof BusinessRuleException || ex instanceof DuplicateResourceException;
    }
}
