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

The stack uses a Compose profile, so a plain `up -d` doesn't start it. Run these from the backend root:
the Compose file lives in `infrastructure/`, and `--env-file` passes the root `.env`.

| Task | Command |
|---|---|
| Start | `docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging up -d loki alloy grafana` |
| Status | `docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging ps loki alloy grafana` |
| Stop, keeping data | `docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging stop loki alloy grafana` |
| Restart after a config change | `docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging restart loki alloy grafana` |
| Follow stack logs | `docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging logs -f loki alloy grafana` |

Then start the backend as usual (from the backend root) and open http://localhost:3001: the
**CourseAuraHub Logs** dashboard is under **Dashboards > CourseAuraHub**. The log file is read from the
start, so anything written while the stack was down is shipped when it comes back.

## Dashboard and filters

The filters at the top apply to every panel, together with Grafana's time picker (top right):

| Filter | What it does |
|---|---|
| **Service** | One or more `service` label values, or All |
| **Level** | ERROR, WARN, INFO, DEBUG, TRACE: pick one or several, or All |
| **Search** | Case-insensitive text or regular expression on the whole line, such as a `requestId`, a route or an exception name. Leave it empty for everything |

| Panel | Shows |
|---|---|
| Log lines, Errors, Warnings, Failed requests (5xx) | Counts for the selected time range. Errors and Warnings ignore the Level filter |
| Log volume by level | Lines over time, stacked by level |
| Request latency (p95, ms) | 95th percentile of `duration_ms` from request lines |
| Errors, Failed requests (5xx) | The matching lines, newest first. Open one to see `stack_trace` and `requestId` |
| Logs | Every line matching all three filters |

The dashboard is provisioned from a file and read-only in Grafana. To change it, edit
`infrastructure/observability/grafana/dashboards/course-aura-logs.json`; Grafana reloads it within a minute.

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
| `infrastructure/docker-compose.yaml` | The `loki`, `alloy` and `grafana` services |
| `infrastructure/observability/loki/loki-config.yaml` | Storage, retention, flushing on shutdown, and querying unflushed data |
| `infrastructure/observability/alloy/config.alloy` | File tailing, redaction and email masking, labels and timestamps |
| `infrastructure/observability/grafana/provisioning/` | The Loki data source and dashboard loader |
| `infrastructure/observability/grafana/dashboards/course-aura-logs.json` | The CourseAuraHub Logs dashboard |
| `src/main/resources/application.yml` | Log file path, JSON format and rotation |
| `src/main/java/.../common/logging/RequestLoggingFilter.java` | One log line per request, and the request id |

`LOG_FILE_PATH` moves the log file. An empty value turns it off. If you move it, also change the
`./logs` mount on `alloy`.

## Log fields

Labels are `service`, `level`, `job` and `filename` (Loki also adds `service_name`). Everything else is a JSON field read with `| json`:

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

# Failed requests. Match the status as text: most lines have no http_status, and a numeric
# comparison like `http_status >= 500` turns those into errors
{service="course-aura-hub"} | json | http_status =~ "5[0-9]{2}"

# Slow requests (skip lines without a duration first, for the same reason)
{service="course-aura-hub"} | json | duration_ms != "" | duration_ms > 1000

# Requests to one route
{service="course-aura-hub"} | json | http_path =~ "/api/v1/me/cart.*"

# Every line of one request
{service="course-aura-hub"} | json | requestId = "PASTE-THE-ID"

# Errors per minute
sum(count_over_time({service="course-aura-hub", level="ERROR"}[1m]))
```

## Investigate a failed API request

1. Note the time, route and status from the browser's Network tab.
2. Find the request line: `{service="course-aura-hub"} | json | http_status =~ "[45][0-9]{2}" | http_path =~ "/api/v1/me/cart.*"`,
   or type the route into the dashboard's **Search** filter.
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
- Some lines name the user's email (sign-in, password and profile changes). Alloy masks the part before
  the `@`, so Grafana shows `***@example.com`; the log file on disk keeps the full address for debugging.
- A database constraint error can include the value that clashed. Treat the log file and Grafana as
  personal data, and don't share them.

## Log retention

| Where | Kept for |
|---|---|
| Loki | 7 days (`retention_period`). Deleted by the compactor, up to about 2 hours later |
| `logs/` on disk | Rotated at 10 MB, 7 days of history, 200 MB in total |

To change Loki's retention, edit `retention_period` and `reject_old_samples_max_age` together.

## Storage locations

| What | Where | Survives |
|---|---|---|
| Application log file | `logs/course-aura-hub.log` in the backend root (the path is relative to where the backend runs). Rotated files sit next to it as `course-aura-hub.log.YYYY-MM-DD.N.gz` | Everything, until rotation deletes it |
| Loki: chunks, index, write-ahead log, compactor state | Docker volume `course-aura-hub-backend_loki_data`, mounted at `/loki` | Restarts, recreating the container, `down` without `-v` |
| Alloy: how far the file has been read | Docker volume `course-aura-hub-backend_alloy_data` | The same |
| Grafana: users, preferences, sign-in | Docker volume `course-aura-hub-backend_grafana_data`. Dashboards and the data source come from the files above | The same |

Docker Desktop keeps volumes inside its own VM; `docker volume inspect course-aura-hub-backend_loki_data`
shows the path there. Loki writes its in-memory chunks to the volume when it stops, and it queries them
before they're written, so lines received just before a restart, or caught up on after downtime, are
neither lost nor hidden.

## Data persistence and clean-up

The volumes above survive restarts. `_alloy_data` remembers how far the file has been read, which stops
lines being sent twice.

> **Don't run `docker compose -f infrastructure/docker-compose.yaml --env-file .env down -v` to clear logs.** It also deletes the Postgres data.

To delete only the logging data:

```bash
docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging rm -sf loki alloy grafana
docker volume rm course-aura-hub-backend_loki_data course-aura-hub-backend_alloy_data course-aura-hub-backend_grafana_data
```

`docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging down` without `-v` is safe for your data, but it stops every service
in the file, including Postgres. A plain `down` skips the logging services.

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
| The file grows, but no logs appear | `docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging logs alloy`, and widen the time range. Entries older than 7 days are rejected |
| `course-aura-alloy` is unhealthy | Its healthcheck also covers Loki, so check `docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging logs loki` |
| Grafana won't start | It waits for a healthy Alloy, so check Alloy first |
| Grafana restarts or stops | `docker inspect -f '{{.State.OOMKilled}}' course-aura-grafana`, then raise `mem_limit` |
| Port 3001 is in use | Set `GRAFANA_PORT` |
| Sign-in fails after changing the password | The password is set only when the volume is created. Run `docker exec -it course-aura-grafana grafana cli admin reset-admin-password NEW` |
| `network ... has active endpoints` | Logging services are still running. Use `docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging down` |
| `logs/` is owned by root | Docker created it before the backend did. Create it yourself first, or fix its ownership |
