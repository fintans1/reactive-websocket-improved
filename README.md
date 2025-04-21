# Improved Reactive WebSocket Implementation

This repository contains improved implementations of reactive WebSocket components that address complexity issues in nested reactive chains and state transitions.

## Key Improvements

### 1. RithmicWebSocketConnectionManager

The improved `RithmicWebSocketConnectionManager` features:

- **Simplified connection flow**: Clear separation between connection establishment and message handling.
- **Better state management**: Using atomic variables for thread-safe state tracking.
- **Cleaner error handling**: Specific error handling paths with descriptive logging.
- **Improved reconnection logic**: Automatic reconnection with backoff.
- **Thread-safe message publishing**: Using Reactor's `Sinks` for reliable message delivery.

### 2. FluxRithmicWebSocketHandler

The refactored `FluxRithmicWebSocketHandler` includes:

- **Declarative message handling**: Register handlers with a fluent API.
- **Type-safe message processing**: Automatic conversion to strongly-typed message classes.
- **Reactive message streams**: Get typed Flux streams for specific message types.
- **Separation of concerns**: Clearly separated message parsing, dispatching, and handling.
- **Robust error handling**: Each processing step has dedicated error handling.

## Original Issues Addressed

The original implementation had several challenges:

1. **Deeply nested reactive chains** making the code difficult to follow and maintain.
2. **Complex state transitions** with unclear lifecycle management.
3. **Mixed concerns** where connection management, message parsing, and business logic were intertwined.
4. **Error propagation** that was difficult to trace and debug.

## Usage Examples

### Setting up the WebSocket Connection

```java
// Configure and autowire the components
@Autowired
private RithmicWebSocketConnectionManager connectionManager;

// Start the connection
public void startConnection() {
    connectionManager.connect()
        .subscribe(
            null,
            error -> log.error("Connection error: {}", error.getMessage()),
            () -> log.info("Connection initiated")
        );
}

// Send a message
public void sendMessage(String message) {
    connectionManager.sendMessage(message)
        .subscribe(
            null,
            error -> log.error("Failed to send message: {}", error.getMessage()),
            () -> log.debug("Message sent successfully")
        );
}
```

### Handling Specific Message Types

```java
// Configure a handler with fluent API in your @Configuration
@Bean
public FluxRithmicWebSocketHandler webSocketHandler(ObjectMapper objectMapper) {
    FluxRithmicWebSocketHandler handler = new FluxRithmicWebSocketHandler(objectMapper);
    
    handler.handleMessages()
        .ofType("marketData", MarketDataMessage.class, this::processMarketData)
        .ofType("trade", TradeMessage.class, this::processTrade)
        .ofType("heartbeat", HeartbeatMessage.class, this::processHeartbeat);
        
    return handler;
}

// Or register handlers manually
handler.registerMessageHandler("orderBook", new MessageHandler<OrderBookMessage>() {
    @Override
    public void handle(OrderBookMessage payload) {
        // Process order book update
    }
    
    @Override
    public Class<OrderBookMessage> getPayloadType() {
        return OrderBookMessage.class;
    }
});
```

### Getting Reactive Streams for Messages

```java
// Get a Flux of specific message types for reactive processing
Flux<TradeMessage> tradeMessages = handler.getMessageFlux("trade", TradeMessage.class);

// Subscribe to the stream
tradeMessages.subscribe(
    trade -> System.out.println("New trade: " + trade.getPrice()),
    error -> log.error("Error in trade stream: {}", error.getMessage())
);

// Apply reactive transformations
tradeMessages
    .filter(trade -> trade.getSize() > 1000)
    .map(this::enrichTradeData)
    .buffer(Duration.ofSeconds(5))
    .subscribe(this::processTradeBatch);
```

## Architecture Benefits

This improved implementation offers several architectural benefits:

1. **Better testability**: Clearer separation of concerns allows for easier unit testing.
2. **Improved maintainability**: Simplified flows are easier to understand and modify.
3. **Enhanced reliability**: More robust error handling and state management.
4. **Performance**: More efficient resource utilization and cleaner backpressure handling.
5. **Extensibility**: The modular design makes it easier to add new features.

## Implementation Patterns

The code demonstrates several reactive programming best practices:

- Using `Sinks` for thread-safe event publishing
- Separating inbound and outbound flows
- Proper resource cleanup with `doFinally`
- Clear error handling with `doOnError` and `onErrorResume`
- Using builder patterns for fluent APIs
- Type-safe message handling with generics
