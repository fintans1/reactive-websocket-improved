package com.example.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import java.net.URI;

/**
 * Configuration class for WebSocket components with example setup
 */
@Configuration
public class WebSocketConfig {

    /**
     * Example of configuring the WebSocket client
     */
    @Bean
    public WebSocketClient webSocketClient() {
        return new ReactorNettyWebSocketClient();
    }

    /**
     * Example of configuring the WebSocket handler
     */
    @Bean
    public FluxRithmicWebSocketHandler webSocketHandler(ObjectMapper objectMapper) {
        FluxRithmicWebSocketHandler handler = new FluxRithmicWebSocketHandler(objectMapper);
        
        // Example of registering message handlers with fluent API
        handler.handleMessages()
            .ofType("update", UpdateMessage.class, this::handleUpdateMessage)
            .ofType("ping", PingMessage.class, this::handlePingMessage);
            
        return handler;
    }
    
    /**
     * Example of configuring the WebSocket connection manager
     */
    @Bean
    public RithmicWebSocketConnectionManager webSocketConnectionManager(
            WebSocketClient client, 
            FluxRithmicWebSocketHandler handler) {
        
        // Example URL - would be configured from properties in a real application
        URI webSocketUri = URI.create("ws://example.com/websocket");
        
        return new RithmicWebSocketConnectionManager(client, webSocketUri, handler);
    }
    
    // Example message types
    
    public static class UpdateMessage {
        private String data;
        
        public String getData() {
            return data;
        }
        
        public void setData(String data) {
            this.data = data;
        }
    }
    
    public static class PingMessage {
        private long timestamp;
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
    
    // Example message handlers
    
    private void handleUpdateMessage(UpdateMessage message) {
        // Process update messages
        System.out.println("Received update: " + message.getData());
    }
    
    private void handlePingMessage(PingMessage message) {
        // Process ping messages
        System.out.println("Received ping with timestamp: " + message.getTimestamp());
    }
}
