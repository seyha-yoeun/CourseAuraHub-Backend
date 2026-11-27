package com.course_aura_hub.chat.config;

import com.course_aura_hub.auth.config.CorsProperties;
import com.course_aura_hub.chat.service.ChatSocketTickets;
import com.course_aura_hub.chat.socket.ChatChannelInterceptor;
import com.course_aura_hub.chat.socket.ChatHandshakeHandler;
import com.course_aura_hub.chat.socket.ChatHandshakeInterceptor;
import com.course_aura_hub.chat.socket.ChatSessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP over WebSocket at {@code /ws/chat}. Browsers connect straight to the backend with a single-use ticket;
 * clients subscribe to {@code /user/queue/chat} and send commands to {@code /app/chat.*}. The simple in-memory
 * broker holds this instance's sessions; other instances are reached through {@code ChatDispatcher}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatProperties properties;
    private final CorsProperties corsProperties;
    private final ChatHandshakeInterceptor handshakeInterceptor;
    private final ChatChannelInterceptor channelInterceptor;
    private final ChatSessionRegistry sessionRegistry;
    private TaskScheduler heartbeatScheduler;

    public ChatWebSocketConfig(
            ChatProperties properties,
            CorsProperties corsProperties,
            ChatHandshakeInterceptor handshakeInterceptor,
            ChatChannelInterceptor channelInterceptor,
            ChatSessionRegistry sessionRegistry
    ) {
        this.properties = properties;
        this.corsProperties = corsProperties;
        this.handshakeInterceptor = handshakeInterceptor;
        this.channelInterceptor = channelInterceptor;
        this.sessionRegistry = sessionRegistry;
    }

    @Autowired
    public void setHeartbeatScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler scheduler) {
        this.heartbeatScheduler = scheduler;
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint(ChatSocketTickets.SOCKET_PATH)
                .setHandshakeHandler(new ChatHandshakeHandler())
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        long heartbeat = properties.heartbeat().toMillis();
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{heartbeat, heartbeat})
                .setTaskScheduler(heartbeatScheduler);
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(@NonNull WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(properties.maxFrameBytes())
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(15_000)
                .addDecoratorFactory(sessionRegistry);
    }
}
