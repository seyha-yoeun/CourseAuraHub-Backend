# Centralized logging

Local development logging with Grafana Loki, Grafana and Grafana Alloy. No cloud services.

```text
Spring Boot ──> logs/course-aura-hub.log ──> Alloy ──> Loki ──> Grafana
 (JSON file)      (bind-mounted read-only)   (tails)  (stores)  (localhost:3001)
```

The backend usually runs on the host, so Docker's log stream can't see it. Alloy tails the JSON file
instead, and that file is its only source, so an entry is never collected twice. The console output
keeps its usual readable format.

## Start, stop and restart

The stack uses a Compose profile, so a plain `docker compose up -d` doesn't start it.

| Task | Command |
|---|---|
| Start | `docker compose --profile logging up -d loki alloy grafana` |
| Status | `docker compose --profile logging ps loki alloy grafana` |
| Stop, keeping data | `docker compose --profile logging stop loki alloy grafana` |
| Restart after a config change | `docker compose --profile logging restart loki alloy grafana` |
| Follow stack logs | `docker compose --profile logging logs -f loki alloy grafana` |

Restart the backend once after pulling this change, so it starts writing the log file. The file is
read from the start, so anything written while the stack was down is shipped when it comes back.

## Ports and sign-in

| Service | Address | Notes |
|---|---|---|
| Grafana | http://localhost:3001 | Local machine only. Port 3000 belongs to the frontend |
| Loki | `loki:3100` | Logging network only, no host port |
| Alloy | `127.0.0.1:12345` in its container | No host port |

Grafana signs in with `admin` / `admin` unless `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` are set
when the Grafana volume is first created. `GRAFANA_PORT` changes the published port.

## Files

| Path | Purpose |
|---|---|
| `docker-compose.yaml` | The `loki`, `alloy` and `grafana` services |
| `observability/loki/loki-config.yaml` | Storage and retention |
| `observability/alloy/config.alloy` | File tailing, redaction, labels and timestamps |
| `observability/grafana/provisioning/` | The Loki data source and dashboard loader |
| `observability/grafana/dashboards/course-aura-logs.json` | Starter dashboard |
| `src/main/resources/application.yml` | Log file path, JSON format and rotation |
| `src/main/java/.../common/logging/RequestLoggingFilter.java` | One log line per request, and the request id |

`LOG_FILE_PATH` moves the log file. An empty value turns it off. If you move it, also change the
`./logs` mount on `alloy`.

## Log fields

Labels are `service`, `level`, `job` and `filename`. Everything else is a JSON field read with `| json`:

| Field | Meaning |
|---|---|
| `@timestamp`, `level`, `message`, `logger_name`, `thread_name` | Standard fields |
| `stack_trace` | Full exception, when there is one |
| `requestId` | Shared by every line of a request, and returned as the `X-Request-Id` header |
| `http_method`, `http_path`, `http_status`, `duration_ms` | Request lines only |

Successful requests log at INFO, 4xx at WARN and 5xx at ERROR. Swagger and `/error` aren't logged.
The app has no tracing library, so `requestId` is the only correlation id. A caller's `X-Request-Id`
is kept when it is 8 to 64 letters, digits, `-` or `_`. Otherwise a new one is generated.

## Useful queries

Open **Explore**, choose **Loki** and switch to **Code**, or open **Dashboards > CourseAuraHub >
CourseAuraHub Logs**.

```logql
# Everything from one service
{service="course-aura-hub"}

# Errors
{service="course-aura-hub", level="ERROR"}

# Warnings
{service="course-aura-hub", level="WARN"}

# Exceptions with a stack trace
{service="course-aura-hub"} | json | stack_trace != ""

# One exception type
{service="course-aura-hub"} |= "IllegalStateException"

# Failed requests
{service="course-aura-hub"} | json | http_status >= 500

# Slow requests
{service="course-aura-hub"} | json | duration_ms > 1000

# Requests to one route
{service="course-aura-hub"} | json | http_path =~ "/api/v1/me/cart.*"

# Every line of one request
{service="course-aura-hub"} | json | requestId = "PASTE-THE-ID"

# Errors per minute
sum(count_over_time({service="course-aura-hub", level="ERROR"}[1m]))
```

## Investigate a failed API request

1. Note the time, route and status from the browser's Network tab.
2. Find the request line: `{service="course-aura-hub"} | json | http_status >= 400 | http_path =~ "/api/v1/me/cart.*"`.
3. Expand it and copy `requestId`. If you called the backend directly, it's also the `X-Request-Id`
   response header. The Nuxt proxy doesn't pass that header on, so calls from the browser need this step.
4. Run `{service="course-aura-hub"} | json | requestId = "PASTE-THE-ID"` to see the whole request.
5. Read `message`, then `stack_trace`. A request line with no error before it was refused by security
   or validation, so its status is the answer: 401 or 403 for security, 400 for validation.

## Sensitive data

- Request lines hold the path only: never query strings, headers, cookies or bodies.
- Bind-parameter tracing stays off.
- Alloy also replaces `Bearer ...` values and JWT-shaped strings with `[REDACTED]` before they reach
  Loki. That is a backstop only: `logs/course-aura-hub.log` itself isn't redacted.
- Existing log lines include email addresses, and a database constraint error can include the value
  that clashed. Treat the log file and Grafana as personal data, and don't share them.

## Log retention

| Where | Kept for |
|---|---|
| Loki | 7 days (`retention_period`). Deleted by the compactor, up to about 2 hours later |
| `logs/` on disk | Rotated at 10 MB, 7 days of history, 200 MB in total |

To change Loki's retention, edit `retention_period` and `reject_old_samples_max_age` together.

## Data persistence and clean-up

Data lives in the volumes `courseaurahub-backend_loki_data`, `_alloy_data` and `_grafana_data`, which
survive restarts. `_alloy_data` remembers how far the file has been read, which stops lines being
sent twice.

> **Don't run `docker compose down -v` to clear logs.** It also deletes the Postgres data.

To delete only the logging data:

```bash
docker compose --profile logging rm -sf loki alloy grafana
docker volume rm courseaurahub-backend_loki_data courseaurahub-backend_alloy_data courseaurahub-backend_grafana_data
```

`docker compose --profile logging down` without `-v` is safe for your data, but it stops every service
in the file, including Postgres. A plain `docker compose down` skips the logging services.

## Resource requirements

| Service | Memory limit | Idle use seen |
|---|---|---|
| Loki | 512 MB | About 45 MB |
| Alloy | 256 MB | About 40 MB |
| Grafana | 512 MB | About 250 MB |

Allow about 1.3 GB of memory at most. The images use about 3 GB of disk. Loki's data stays small at
local volumes, and 7 days of retention caps it.

## Troubleshooting

| Symptom | Check |
|---|---|
| Nothing in Grafana | Does `logs/course-aura-hub.log` exist and grow? If not, restart the backend from the repo root, because the path is relative to where it runs |
| The file grows, but no logs appear | `docker compose --profile logging logs alloy`, and widen the time range. Entries older than 7 days are rejected |
| `course-aura-alloy` is unhealthy | Its healthcheck also covers Loki, so check `docker compose --profile logging logs loki` |
| Grafana won't start | It waits for a healthy Alloy, so check Alloy first |
| Grafana restarts or stops | `docker inspect -f '{{.State.OOMKilled}}' course-aura-grafana`, then raise `mem_limit` |
| Port 3001 is in use | Set `GRAFANA_PORT` |
| Sign-in fails after changing the password | The password is set only when the volume is created. Run `docker exec -it course-aura-grafana grafana cli admin reset-admin-password NEW` |
| `network ... has active endpoints` | Logging services are still running. Use `docker compose --profile logging down` |
| `logs/` is owned by root | Docker created it before the backend did. Create it yourself first, or fix its ownership |
