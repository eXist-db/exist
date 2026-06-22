# Architecture

## System Diagram

```mermaid
flowchart LR
  subgraph exist-parent["eXist-db Parent"]
    eval-web-socket-endpoint["Eval Web Socket Endpoint"]
    web-socket-endpoint["Web Socket Endpoint"]
  end

  abstract-class-integration-test-service["Abstract Class Integration Test Service"]
  abstract-http-test-service["Abstract Http Test Service"]
  abstract-instance-integration-test-service["Abstract Instance Integration Test Service"]
  abstract-integration-test-service["Abstract Integration Test Service"]
  attribute-test-service["Attribute Test Service"]
  authenticated-http-client-test-service["Authenticated Http Client Test Service"]
  c-data-intergation-test-service["C Data Intergation Test Service"]
  get-data-test-service["Get Data Test Service"]
  get-header-test-service["Get Header Test Service"]
  get-parameter-test-service["Get Parameter Test Service"]
  http-vector-provider-service["Http Vector Provider Service"]
  jmx-remote-test-service["Jmx Remote Test Service"]
  login-module-i-t-service["Login Module I T Service"]
  move-resource-test$-check-thread-service["Move Resource Test$ Check Thread Service"]
  patch-test-service["Patch Test Service"]
  rest-binaries-test-service["Rest Binaries Test Service"]
  send-request-function-service["Send Request Function Service"]
  stream-binary-test-service["Stream Binary Test Service"]
  web-dav-http-service["Web Dav Http Service"]
  x-m-l-d-b-authenticate-test-service["X M L D B Authenticate Test Service"]

  eval-web-socket-endpoint -->|HTTPS| abstract-http-test-service
  eval-web-socket-endpoint -->|HTTPS| authenticated-http-client-test-service
  web-socket-endpoint -->|HTTPS| rest-binaries-test-service
```

## Components

### Services

- **EvalWebSocketEndpoint**: WEBSOCKET /ws/eval, WEBSOCKET onMessage
- **WebSocketEndpoint**: WEBSOCKET /ws, WEBSOCKET /ws

### External Services

- **Abstract Class Integration Test Service**: HTTPS service
- **Abstract Http Test Service**: HTTPS service
- **Abstract Instance Integration Test Service**: HTTPS service
- **Abstract Integration Test Service**: HTTPS service
- **Attribute Test Service**: HTTPS service
- **Authenticated Http Client Test Service**: HTTPS service
- **C Data Intergation Test Service**: HTTPS service
- **Get Data Test Service**: HTTPS service
- **Get Header Test Service**: HTTPS service
- **Get Parameter Test Service**: HTTPS service
- **Http Vector Provider Service**: HTTPS service
- **Jmx Remote Test Service**: HTTPS service
- **Login Module I T Service**: HTTPS service
- **Move Resource Test$ Check Thread Service**: HTTPS service
- **Patch Test Service**: HTTPS service
- **Rest Binaries Test Service**: HTTPS service
- **Send Request Function Service**: HTTPS service
- **Stream Binary Test Service**: HTTPS service
- **Web Dav Http Service**: HTTPS service
- **X M L D B Authenticate Test Service**: HTTPS service

## Reference

For the complete CALM (Common Architecture Language Model) schema, see [calm-architecture.json](calm-architecture.json).
