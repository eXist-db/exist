# Architecture

## System Diagram

```mermaid
flowchart LR
  subgraph exist-parent["eXist-db Parent"]
    eval-web-socket-endpoint["Eval Web Socket Endpoint"]
    web-socket-endpoint["Web Socket Endpoint"]
  end

  abstract-http-test-service["Abstract Http Test Service"]
  http-vector-provider-service["Http Vector Provider Service"]
  jmx-remote-test-service["Jmx Remote Test Service"]
  login-module-i-t-service["Login Module I T Service"]
  move-resource-test$-check-thread-service["Move Resource Test$ Check Thread Service"]

  eval-web-socket-endpoint -->|HTTPS| abstract-http-test-service
```

## Components

### Services

- **EvalWebSocketEndpoint**: WEBSOCKET /ws/eval, WEBSOCKET onMessage
- **WebSocketEndpoint**: WEBSOCKET /ws, WEBSOCKET /ws

### External Services

- **Abstract Http Test Service**: HTTPS service
- **Http Vector Provider Service**: HTTPS service
- **Jmx Remote Test Service**: HTTPS service
- **Login Module I T Service**: HTTPS service
- **Move Resource Test$ Check Thread Service**: HTTPS service

## Reference

For the complete CALM (Common Architecture Language Model) schema, see [calm-architecture.json](calm-architecture.json).
