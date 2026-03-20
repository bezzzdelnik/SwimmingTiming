# SwimmingTiming Serial Replay Emulator (VSPE/Virtual COM friendly)

This folder contains a small Node.js 18 script that replays your existing PuTTY serial logs into a **COM port**.

It is intended to work with your app that reads from a real serial port (e.g. `COM5`), while the emulator writes into the other side of a **virtual COM pair** (e.g. `COM6`) created in **VSPE**.

---

## Requirements

1. **Node.js 18** installed.
2. A virtual COM pair in VSPE (or similar):
   - Your application reads from `COM_IN`
   - Emulator writes to `COM_OUT`
3. Your log file (binary/raw bytes) such as:
   - `emul/logs/swimLog.txt`
   - `emul/logs/puttySwimmingpiter.txt`

---

## Install dependencies

From the repository root:

```bash
cd emul
npm install
```

---

## Quick start (replay once)

Example:

```bash
node swim-serial-replay.js --file logs/swimLog.txt --port COM6 --baud 9600 --race-index 0
```

- `--file` : path to the log file (relative to `emul/` also works)
- `--port` : COM port where the emulator writes (typically the VSPE “other end”, e.g. `COM6`)
- `--baud` : baud rate (must match your serial settings)
- `--race-index` : which race to start from inside the log (default `0`)

The script will:
1. Find the start marker used by your Java `DataReader`
2. Split the log into frames and replay them sequentially

---

## “Try real-time” mode (best effort)

If you want the replay to more closely follow the original timing:

```bash
node swim-serial-replay.js --file logs/swimLog.txt --port COM6 --baud 9600 --race-index 0 --try-realtime
```

This is a best-effort feature:
- it tries to estimate time deltas from the payload
- if it can’t reliably estimate, it falls back to `--delay-ms`

---

## Timing controls

Default values:
- `--delay-ms 10`
- `--speed 1.0`

Example: faster replay:

```bash
node swim-serial-replay.js --file logs/swimLog.txt --port COM6 --baud 9600 --race-index 0 --try-realtime --speed 2.0
```

---

## Loop mode (restart replay forever)

```bash
node swim-serial-replay.js --file logs/swimLog.txt --port COM6 --baud 9600 --race-index 0 --loop
```

Stop with `Ctrl+C`.

---

## VSPE wiring reminder

You must set:
- **Your Java app** `portField` = `COM_IN` (example `COM5`)
- **Emulator** `--port` = `COM_OUT` (example `COM6`)

If the app doesn’t receive anything:
- verify VSPE shows both COM ports created
- verify the baud/parity/data bits/stop bits match
- confirm COM settings in your Java UI are the same as emulator arguments

---

## Logs folder

The emulator expects raw log bytes (PuTTY “log output”).
If you convert/export logs to a different format, replay may break.

Use the existing files in `emul/logs/`.

