package com.example.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * An improved implementation of FluxRithmicWebSocketHandler with simplified 
 * reactive flows and clearer separation of concerns.
 */
@Component
public class FluxRithmicWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FluxRithmicWebSocketHandler.class);
    
    private final ObjectMapper objectMapper;
    private final Map<String, MessageHandler<?>> messageTypeHandlers = new ConcurrentHashMap<>();
    
    // Central sink for publishing messages to all subscribers
    private final Sinks.Many<MessageEvent<?>> messageSink;
    
    /**
     * Represents a WebSocket message event
     */
    public static class MessageEvent<T> {
        private final String type;
        private final T payload;
        
        public MessageEvent(String type, T payload) {
            this.type = type;
            this.payload = payload;
        }
        
        public String getType() {
            return type;
        }
        
        public T getPayload() {
            return payload;
        }
    }
    
    /**
     * Interface for handling typed messages
     */
    public interface MessageHandler<T> {
        void handle(T payload);
        Class<T> getPayloadType();
    }

    public FluxRithmicWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();
        
        // Register built-in handlers
        registerDefaultHandlers();
    }

    /**
     * Register default message handlers
     */
    private void registerDefaultHandlers() {
        // Add default handlers if needed
    }

    /**
     * Register a new message type handler
     *
     * @param type Message type identifier
     * @param handler Handler for the message type
     * @param <T> Type of message payload
     */
    public <T> void registerMessageHandler(String type, MessageHandler<T> handler) {
        messageTypeHandlers.put(type, handler);
        log.info("Registered handler for message type: {}", type);
    }
    
    /**
     * Get a flux of message events for a specific type
     *
     * @param type Message type to filter for
     * @param clazz Class of the payload
     * @param <T> Type of message payload
     * @return Flux of message events
     */
    public <T> Flux<T> getMessageFlux(String type, Class<T> clazz) {
        return messageSink.asFlux()
            .filter(event -> event.getType().equals(type))
            .map(event -> {
                try {
                    if (event.getPayload() instanceof Map) {
                        // Convert Map to target class if needed
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) event.getPayload();
                        return objectMapper.convertValue(map, clazz);
                    } else if (clazz.isInstance(event.getPayload())) {
                        return clazz.cast(event.getPayload());
                    } else {
                        log.warn("Payload is not of expected type: {}", clazz.getName());
                        return null;
                    }
                } catch (Exception e) {
                    log.error("Error converting payload to {}: {}", clazz.getName(), e.getMessage());
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket session established: {}", session.getId());
        
        // Process incoming messages
        Mono<Void> input = processIncomingMessages(session);
        
        // No outbound messages from this handler
        return input;
    }
    
    /**
     * Process incoming messages with clear error handling
     */
    private Mono<Void> processIncomingMessages(WebSocketSession session) {
        return session.receive()
            .doOnNext(message -> processMessage(message))
            .doOnError(error -> log.error("Error in WebSocket receive stream: {}", error.getMessage()))
            .doOnComplete(() -> log.info("WebSocket session complete: {}", session.getId()))
            .then();
    }
    
    /**
     * Process a single WebSocket message
     */
    private void processMessage(WebSocketMessage message) {
        try {
            String payload = message.getPayloadAsText();
            log.debug("Received message: {}", payload);
            
            // Parse the message to extract type and payload
            parseAndDispatchMessage(payload);
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
        }
    }
    
    /**
     * Parse message and dispatch to appropriate handler
     */
    @SuppressWarnings("unchecked")
    private void parseAndDispatchMessage(String messageText) {
        try {
            // Parse as generic map first to extract type
            Map<String, Object> messageMap = objectMapper.readValue(messageText, Map.class);
            
            // Extract message type - assuming a standard format with 'type' field
            String type = (String) messageMap.getOrDefault("type", "unknown");
            Object data = messageMap.get("data");
            
            log.debug("Processing message of type: {}", type);
            
            // Find handler for this message type
            MessageHandler<?> handler = messageTypeHandlers.get(type);
            
            if (handler != null) {
                // Convert payload to expected type
                Object typedPayload = objectMapper.convertValue(data, handler.getPayloadType());
                
                // Execute handler
                try {
                    ((MessageHandler<Object>) handler).handle(typedPayload);
                } catch (Exception e) {
                    log.error("Error in message handler for type {}: {}", type, e.getMessage());
                }
            }
            
            // Publish message to sink for reactive consumers
            messageSink.tryEmitNext(new MessageEvent<>(type, data));
            
        } catch (JsonProcessingException e) {
            log.error("Error parsing message: {}", e.getMessage());
        }
    }
    
    /**
     * Create a builder to simplify registration of message handlers
     * 
     * @return A new handler builder
     */
    public HandlerBuilder handleMessages() {
        return new HandlerBuilder(this);
    }
    
    /**
     * Builder to create fluent API for registering message handlers
     */
    public static class HandlerBuilder {
        private final FluxRithmicWebSocketHandler handler;
        
        HandlerBuilder(FluxRithmicWebSocketHandler handler) {
            this.handler = handler;
        }
        
        /**
         * Register a handler for a specific message type
         * 
         * @param type Message type identifier
         * @param payloadClass Class of the payload
         * @param consumer Consumer to process the message
         * @param <T> Type of message payload
         * @return This builder for method chaining
         */
        public <T> HandlerBuilder ofType(String type, Class<T> payloadClass, Consumer<T> consumer) {
            handler.registerMessageHandler(type, new MessageHandler<T>() {
                @Override
                public void handle(T payload) {
                    consumer.accept(payload);
                }
                
                @Override
                public Class<T> getPayloadType() {
                    return payloadClass;
                }
            });
            return this;
        }
    }
}
