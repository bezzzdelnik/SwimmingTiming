/**
 * Replay SwimmingTiming PuTTY serial logs into a COM port.
 *
 * Usage (Windows):
 *   node emul\\swim-serial-replay.js --file emul\\logs\\swimLog.txt --port COM2 --baud 9600 --race-index 0
 *
 * Notes:
 * - We treat log files as raw bytes (Buffer) to preserve control characters expected by DataReader.
 * - For "try realtime" we optionally read timer-like numbers from the payload and delay by delta.
 */

const fs = require("fs");
const path = require("path");
const { SerialPort } = require("serialport");

const SOH = 0x01; // DataReader.SO H
const EOT = 0x04; // DataReader.EOT

// DataReader.startList in your Java code is "SOH\u001198STXSLH".
// However, your PuTTY logs appear to contain a variant WITHOUT the 0x11 (DC1) byte:
//   SOH + '98' + STX + 'SLH'  (bytes: 0x01 0x39 0x38 0x02 0x53 0x4c 0x48)
// We therefore detect both versions to reliably split races.
const START_LIST_MARKER_V1 = Buffer.from([0x01, 0x11, 0x39, 0x38, 0x02, 0x53, 0x4c, 0x48]); // with 0x11
const START_LIST_MARKER_V2 = Buffer.from([0x01, 0x39, 0x38, 0x02, 0x53, 0x4c, 0x48]); // without 0x11

// startRace / reset markers used by Java DataReader.
// In DataReader, control bytes are replaced into textual placeholders:
// SOH (0x01), DC4 (0x14), STX (0x02), EOT (0x04)
//
// For replay we search for the raw byte sequences.
const START_RACE_MARKER_BYTES_V1 = Buffer.from([
  0x01, 0x11, 0x39, 0x38, 0x02, // SOH DC1 '98' STX
  0x53, 0x54, 0x41, 0x52, 0x54, // 'START'
  0x04 // EOT
]);
const START_RACE_MARKER_BYTES_V2 = Buffer.from([
  0x01, 0x39, 0x38, 0x02, // SOH '98' STX
  0x53, 0x54, 0x41, 0x52, 0x54, // 'START'
  0x04 // EOT
]);

// SOHDC4L0EOT in DataReader means bytes: SOH(0x01) DC4(0x14) 'L'(0x4c) '0'(0x30) EOT(0x04)
const RESET_L0_BYTES = Buffer.from([0x01, 0x14, 0x4c, 0x30, 0x04]);

function parseArgs(argv) {
  const args = {};
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith("--")) continue;
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith("--")) {
      args[key] = next;
      i++;
    } else {
      args[key] = "true";
    }
  }
  return args;
}

function findAll(buf, pattern) {
  const idxs = [];
  let from = 0;
  while (true) {
    const idx = buf.indexOf(pattern, from);
    if (idx === -1) break;
    idxs.push(idx);
    from = idx + 1;
  }
  return idxs;
}

function chunkBySohAndEotBoth(buf) {
  // Create a chunk whenever we've seen BOTH SOH and EOT (order doesn't matter).
  const chunks = [];
  let start = 0;
  let hasSoh = false;
  let hasEot = false;

  for (let i = 0; i < buf.length; i++) {
    const b = buf[i];
    if (b === SOH) hasSoh = true;
    if (b === EOT) hasEot = true;

    if (hasSoh && hasEot) {
      const c = buf.slice(start, i + 1);
      // Ensure chunk contains both (it will, but keep it explicit).
      chunks.push(c);
      start = i + 1;
      hasSoh = false;
      hasEot = false;
    }
  }

  // Filter out empty-ish chunks or chunks without both markers (shouldn't happen).
  return chunks.filter((c) => c.includes(SOH) && c.includes(EOT));
}

function tryExtractTimerSeconds(chunk) {
  // Best-effort: decode payload and search for a numeric seconds value.
  // In your DataReader.readTimer, timer looks like "0.0", "1.23", etc.
  const s = chunk.toString("latin1");

  // Prefer patterns near R02.
  const r = s.indexOf("R02");
  let slice = s;
  if (r >= 0) slice = s.slice(r);

  const m = slice.match(/(\d+\.\d+)/);
  if (!m) return null;
  const v = parseFloat(m[1]);
  if (!Number.isFinite(v)) return null;
  return v;
}

async function main() {
  const args = parseArgs(process.argv);
  const filePath = args.file;
  const portPath = args.port;
  const baudRate = args.baud ? Number(args.baud) : 9600;
  const dataBits = args.dataBits ? Number(args.dataBits) : 8;
  const stopBits = args.stopBits ? Number(args.stopBits) : 1;
  const parity = args.parity ? String(args.parity) : "none";
  const raceIndex = args["race-index"] ? Number(args["race-index"]) : 0;

  const delayMs = args["delay-ms"] ? Number(args["delay-ms"]) : 10;
  const speed = args.speed ? Number(args.speed) : 1.0;
  const tryRealtime = args["try-realtime"] === "true" || args["try-realtime"] === true;
  const verbose = args.verbose === "true" || args.verbose === true;
  const loop = args.loop === "true" || args.loop === true;

  if (!filePath || !portPath) {
    console.error(
      "Usage: node emul/swim-serial-replay.js --file <log.txt> --port COM2 --baud 9600 [--race-index 0] [--try-realtime] [--delay-ms 10] [--speed 1.0]"
    );
    process.exit(1);
  }

  const absFile = path.resolve(filePath);
  const buf = fs.readFileSync(absFile);

  const markersV1 = findAll(buf, START_LIST_MARKER_V1);
  const markersV2 = findAll(buf, START_LIST_MARKER_V2);
  const raceMarkers = markersV1.concat(markersV2).sort((a, b) => a - b);
  if (!raceMarkers.length) {
    console.error("Can't find startList marker in the file; can't split races.");
    console.error("Marker bytes v1:", START_LIST_MARKER_V1);
    console.error("Marker bytes v2:", START_LIST_MARKER_V2);
    process.exit(1);
  }

  if (raceIndex < 0 || raceIndex >= raceMarkers.length) {
    console.error(`race-index out of range. Found ${raceMarkers.length} races.`);
    process.exit(1);
  }

  // Start from the chosen startList marker, but "rewind" to the nearest preceding reset marker,
  // so DataReader успевает сделать resetTable() перед чтением списка пловцов.
  const startListOffset = raceMarkers[raceIndex];
  const cand1 = buf.lastIndexOf(START_RACE_MARKER_BYTES_V1, startListOffset);
  const cand2 = buf.lastIndexOf(START_RACE_MARKER_BYTES_V2, startListOffset);
  const cand3 = buf.lastIndexOf(RESET_L0_BYTES, startListOffset);

  const candidates = [cand1, cand2, cand3].filter((x) => x >= 0);
  const fromOffset = candidates.length ? Math.max(...candidates) : startListOffset;

  const slice = buf.slice(fromOffset);
  const chunks = chunkBySohAndEotBoth(slice);
  console.log(`File: ${absFile}`);
  console.log(`Races found: ${raceMarkers.length}, using race-index: ${raceIndex}`);
  console.log(`Bytes to replay: ${slice.length}, chunks: ${chunks.length}`);

  // Diagnostics: ensure reset markers are present in the sent data.
  const hasResetL0 = slice.includes(RESET_L0_BYTES);
  const hasStartRaceV1 = slice.includes(START_RACE_MARKER_BYTES_V1);
  const hasStartRaceV2 = slice.includes(START_RACE_MARKER_BYTES_V2);
  console.log(
    `Reset markers presence in replay slice: SOHDC4L0EOT=${hasResetL0}, STARTEOTv1=${hasStartRaceV1}, STARTEOTv2=${hasStartRaceV2}`
  );

  const port = new SerialPort({
    path: portPath,
    baudRate,
    dataBits,
    stopBits,
    parity: parity === "none" ? "none" : parity,
    autoOpen: false
  });

  await new Promise((resolve, reject) => port.open((err) => (err ? reject(err) : resolve())));
  console.log(`Serial opened: ${portPath} @ ${baudRate}bps`);

  const runOnce = async () => {
    let lastTimer = null;

    for (let i = 0; i < chunks.length; i++) {
      const chunk = chunks[i];

      await new Promise((resolve, reject) => {
        port.write(chunk, (err) => {
          if (err) reject(err);
          else resolve();
        });
      });
      await new Promise((resolve) => port.drain(() => resolve()));

      let wait = delayMs / speed;
      if (tryRealtime) {
        const t = tryExtractTimerSeconds(chunk);
        if (t != null && lastTimer != null) {
          const dt = t - lastTimer;
          // Only trust small positive deltas (helps avoid random numbers in payload).
          if (dt > 0 && dt < 2.0) {
            wait = (dt * 1000) / speed;
          }
        }
        if (t != null) lastTimer = t;
      }

      if (verbose) {
        console.log(
          `${i + 1}/${chunks.length} write=${chunk.length} wait=${wait.toFixed(1)}ms`
        );
      }

      if (wait > 0) await new Promise((r) => setTimeout(r, wait));
    }
  };

  try {
    if (loop) {
      while (true) {
        console.log("Replay loop started. Ctrl+C to stop.");
        await runOnce();
      }
    } else {
      await runOnce();
    }
  } catch (e) {
    console.error("Replay failed:", e);
  } finally {
    try {
      await new Promise((resolve) => port.close(() => resolve()));
    } catch {}
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

