# Constructor Engine

This is a proof of concept implementation of a lightweight eingine
that is capable of constructing HoP components from other HoP
components.

## Capabilities

1. Trigger synchronous and asynchronous actions.
1. Wait for the results of asynchronous actions.
1. Handle success and failure responses.
1. Automatically compensate successful actions on failure.
1. Support parallel execution and compensation of independent actions.
1. Support in-memory and JDBC storage types.
1. Visualize processes and process instances including the action
   states.

### Demo and tests

Start the Clojure REPL with the `:test` alias enabled and execute the
tests.
