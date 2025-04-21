package com.example.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An improved implementation of the RithmicWebSocketConnectionManager
 * with simplified reactive flows and better state management.
 */
@Component
public class RithmicWebSocketConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(RithmicWebSocketConnectionManager.class);
    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(5);

    private final WebSocketClient client;
    private final URI url;
    private final WebSocketHandler handler;
    
    // Use Sinks for thread-safe message publishing
    private final Sinks.Many<String> outboundMessageSink;
    
    // Connection state tracking
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicReference<WebSocketSession> session = new AtomicReference<>();

    public RithmicWebSocketConnectionManager(WebSocketClient client, URI url, WebSocketHandler handler) {
        this.client = client;
        this.url = url;
        this.handler = handler;
        this.outboundMessageSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    /**
     * Establishes a WebSocket connection.
     * Connection is attempted only if not already connected or connecting.
     * 
     * @return A Mono that completes when the connection is established
     */
    public Mono<Void> connect() {
        if (isConnected.get()) {
            return Mono.empty(); // Already connected
        }
        
        if (isConnecting.compareAndSet(false, true)) {
            log.info("Connecting to WebSocket at {}", url);
            
            return establishConnection()
                .doOnError(e -> {
                    log.error("Failed to connect to WebSocket: {}", e.getMessage());
                    isConnecting.set(false);
                    scheduleReconnect();
                })
                .doOnSuccess(v -> isConnecting.set(false));
        }
        
        return Mono.empty(); // Connection attempt in progress
    }

    /**
     * Handles the core connection logic with clear separation of concerns
     */
    private Mono<Void> establishConnection() {
        return client.execute(url, session -> {
            // Store the session for potential later use
            this.session.set(session);
            isConnected.set(true);
            
            log.info("WebSocket connection established");
            
            // Create a publisher for outbound messages
            Flux<WebSocketMessage> outboundFlux = outboundMessageSink.asFlux()
                .map(session::textMessage)
                .doOnError(e -> log.error("Error in outbound message stream: {}", e.getMessage()));
            
            // Process incoming messages using the handler
            Mono<Void> inbound = session.receive()
                .doOnNext(this::handleIncomingMessage)
                .then();
            
            // Send outbound messages
            Mono<Void> outbound = session.send(outboundFlux);
            
            // Combine inbound and outbound streams
            return Mono.zip(inbound, outbound)
                .doOnError(e -> log.error("WebSocket session error: {}", e.getMessage()))
                .doFinally(signal -> {
                    log.info("WebSocket session terminated with signal: {}", signal);
                    isConnected.set(false);
                    scheduleReconnect();
                })
                .then();
        })
        .onErrorResume(e -> {
            log.error("Error establishing WebSocket connection: {}", e.getMessage());
            return Mono.error(e);
        });
    }
    
    /**
     * Process an incoming WebSocket message
     */
    private void handleIncomingMessage(WebSocketMessage message) {
        try {
            String payload = message.getPayloadAsText();
            log.debug("Received WebSocket message: {}", payload);
            // Further message processing logic can be added here
        } catch (Exception e) {
            log.error("Error processing incoming message: {}", e.getMessage());
        }
    }

    /**
     * Send a message over the WebSocket connection
     * 
     * @param message The message to send
     * @return A Mono that completes when the message is queued for sending
     */
    public Mono<Void> sendMessage(String message) {
        if (!isConnected.get()) {
            log.warn("Cannot send message - WebSocket not connected");
            return Mono.error(new IllegalStateException("WebSocket not connected"));
        }
        
        // Emit the message to the sink
        Sinks.EmitResult result = outboundMessageSink.tryEmitNext(message);
        
        if (result.isSuccess()) {
            return Mono.empty();
        } else {
            return Mono.error(new RuntimeException("Failed to emit message: " + result));
        }
    }

    /**
     * Schedule a reconnection attempt after delay
     */
    private void scheduleReconnect() {
        if (!isConnected.get() && !isConnecting.get()) {
            log.info("Scheduling reconnection attempt in {} seconds", RECONNECT_DELAY.getSeconds());
            
            Mono.delay(RECONNECT_DELAY)
                .flatMap(l -> connect())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    null,
                    e -> log.error("Error in reconnection attempt: {}", e.getMessage()),
                    () -> log.debug("Reconnection attempt scheduled")
                );
        }
    }

    /**
     * Close the WebSocket connection
     * 
     * @return A Mono that completes when the connection is closed
     */
    public Mono<Void> disconnect() {
        WebSocketSession currentSession = session.get();
        if (currentSession != null && isConnected.get()) {
            log.info("Closing WebSocket connection");
            isConnected.set(false);
            return currentSession.close();
        }
        return Mono.empty();
    }

    /**
     * Check if the WebSocket is currently connected
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return isConnected.get();
    }
}
