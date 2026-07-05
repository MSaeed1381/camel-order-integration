# Screenshots

A visual walkthrough of the Order Integration System — route structure, runtime behavior, and data-flow results.

## Route visualization (Hawtio)

The [Hawtio](https://hawt.io/) web console, connected to the running app through the Camel CLI (`camel hawtio camel-1`).

**Route diagram** — the four routes rendered as live node graphs.

![Route diagram](screenshots/hawtio-route-diagram.png)

**Routes & statistics** — per-route state and message counters (completed / failed / total).

![Routes and statistics](screenshots/hawtio-routes-statistics.png)

**Endpoints** — every endpoint URI registered by the routes.

![Endpoints](screenshots/hawtio-endpoints.png)

**Components** — the Camel components in use (`bean`, `direct`, `file`, `timer`).

![Components](screenshots/hawtio-components.png)

## Runtime & monitoring

**Console output** — the periodic status report together with processing logs.

![Console output](screenshots/console-output.png)

**Error handling** — an incomplete order rejected with `InvalidOrderException` and routed to `data/error`.

![Error logs](screenshots/error-logs.png)

**Test results** — the JUnit 5 suite (6 tests) passing.

![Test results](screenshots/test-results.png)

**CLI monitoring** — `camel top` and `camel get route` from the terminal.

![Camel CLI monitoring](screenshots/camel-cli-monitoring.png)

## Data-flow result

**Order transformation** — the same order across stages: `.done` (input), `audit` (raw copy), and `out` (timestamp appended).

![Order transformation](screenshots/sample-order-transformation.png)

**Data folders** — how files are distributed after a run (`out` / `error` / `audit`).

![Data folders](screenshots/data-folders-result.png)
