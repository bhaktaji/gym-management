# ENV_REPORT.md — GymKhata environment preflight

Date: 2026-10-19

## OS / Shell
- OS: Windows 10.0 (Windows 11), arch amd64
- Shell: Windows PowerShell 5.1 (native commands used throughout)

## Toolchain (detected, nothing installed)

| Tool | Version | Location / notes |
|---|---|---|
| java (PATH) | 1.8.0_504 (Temurin JRE) | `C:\Program Files\Eclipse Adoptium\jre-8.0.504.1-hotspot\bin\java.exe` — **below minimum, NOT used for builds** |
| javac (PATH) | 21.0.12.1 | `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\javac.exe` |
| JDK used for build | OpenJDK 21.0.12.1 (Microsoft), LTS | `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\` — meets minimum JDK 17. Selected per-command via `JAVA_HOME` env var; global PATH untouched |
| JAVA_HOME (global) | `C:\Program Files\Eclipse Adoptium\jre-8.0.504.1-hotspot\` | Overridden per-command to JDK 21 for all Maven/build commands |
| Maven | 3.9.16 | `C:\Program Files (x86)\apache-maven-3.9.16\` — present, used (no gradle) |
| Node.js | v24.19.0 | `C:\Program Files\nodejs\node.exe` — meets minimum Node 18; Vite toolchain only |
| npm | 11.17.0 | present |
| git | 2.55.0.windows.5 | present |
| mongod | v9.0.2 | `C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe` — not on PATH; a server is already running as a Windows service (name matches `*mongo*`, Status=Running, StartType=Automatic) and answers on `localhost:27017` (TCP probe: True) |
| mongosh | NOT installed | Not found on disk; DB introspection done via the MongoDB Java driver instead |
| Chrome / Edge | NOT installed | `C:\Program Files\Google\Chrome\Application\chrome.exe` = False; `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` = False → browser-based UI tests SKIPPED |

## MongoDB topology
- A standalone-compatible server answers on `localhost:27017`.
- Replica-set detection: performed via the MongoDB Java driver during backend bring-up (see ASSUMPTIONS.md). No multi-document transactions are used by design.

## Database safety
- Only `gymkhata_dev` and `gymkhata_test_<random>` are used. Tests create/drop their own database. No other database is read or dropped.

## Build stack chosen (compatible with detected toolchain)
- Spring Boot **3.5.16** (latest stable 3.x; requires JDK 17+, runs on JDK 21)
- Spring Web, Spring Security, Spring Data MongoDB, Bean Validation, springdoc-openapi
- OpenPDF (pure-Java PDF), ZXing (QR)
- Frontend: Vite + React + TypeScript, React Router, TanStack Query, react-hook-form + zod, Tailwind CSS, react-i18next (en + hi), vite-plugin-pwa, Recharts
