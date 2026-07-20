#!/usr/bin/env node

import { performance } from "node:perf_hooks";

const KNOWN_PROD_HOSTS = ["woxiangchuanaj.top"];
const base = process.env.TARGET_BASE_URL || "";
const tripId = process.env.SSE_TRIP_ID || "";
const viewerToken = process.env.SSE_VIEWER_TOKEN || "";
const confirmed = process.env.I_UNDERSTAND_THIS_IS_NOT_FOR_PROD || "";
const stages = (process.env.CONNECTION_STAGES || "10,25,50,100")
  .split(",")
  .map((value) => Number(value.trim()))
  .filter((value) => Number.isInteger(value) && value > 0);
const stageDurationMs = parseDuration(process.env.STAGE_DURATION || "2m");

guard();

let failed = false;
for (const connections of stages) {
  const result = await runStage(connections);
  printStage(result);
  failed ||= !result.passed;
}

process.exitCode = failed ? 1 : 0;

function guard() {
  if (!base || !tripId || !viewerToken) {
    throw new Error("TARGET_BASE_URL, SSE_TRIP_ID and SSE_VIEWER_TOKEN are required");
  }
  const target = new URL(base);
  if (!["http:", "https:"].includes(target.protocol)) {
    throw new Error("TARGET_BASE_URL must use http or https");
  }
  if (KNOWN_PROD_HOSTS.some((host) => target.hostname === host || target.hostname.endsWith(`.${host}`))) {
    throw new Error(`REFUSING: SSE load testing is forbidden against production host ${target.hostname}`);
  }
  if (confirmed !== "yes") {
    throw new Error("REFUSING: set I_UNDERSTAND_THIS_IS_NOT_FOR_PROD=yes");
  }
  if (stages.length === 0) {
    throw new Error("CONNECTION_STAGES must contain at least one positive integer");
  }
  if (stageDurationMs < 10_000) {
    throw new Error("STAGE_DURATION must be at least 10s");
  }
}

async function runStage(connections) {
  const endpoint = `${base.replace(/\/$/, "")}/api/trips/${encodeURIComponent(tripId)}/driver-location/stream`;
  const startedAt = performance.now();
  const results = await Promise.all(
    Array.from({ length: connections }, (_, index) =>
      consumeStream(endpoint, index, stageDurationMs)
    )
  );
  const firstEventLatencies = results.flatMap((result) =>
    result.firstEventLatencyMs == null ? [] : [result.firstEventLatencyMs]
  );
  const eventGaps = results.flatMap((result) => result.eventGapsMs);
  const connected = results.filter((result) => result.connected).length;
  const abnormalDisconnects = results.filter((result) => result.abnormalDisconnect).length;
  const serverErrors = results.filter((result) => result.status >= 500).length;
  const eventful = results.filter((result) => result.events > 0).length;
  const connectedRate = connected / connections;
  const abnormalRate = abnormalDisconnects / connections;
  const firstEventP95 = percentile(firstEventLatencies, 95);
  const eventGapP95 = percentile(eventGaps, 95);
  const passed =
    connectedRate >= 0.99 &&
    eventful === connected &&
    firstEventP95 < 5_000 &&
    eventGapP95 < 6_000 &&
    abnormalRate < 0.01 &&
    serverErrors === 0;

  return {
    connections,
    elapsedMs: performance.now() - startedAt,
    connected,
    eventful,
    serverErrors,
    abnormalDisconnects,
    connectedRate,
    abnormalRate,
    firstEventP95,
    eventGapP95,
    totalEvents: results.reduce((sum, result) => sum + result.events, 0),
    passed,
  };
}

async function consumeStream(endpoint, index, durationMs) {
  const controller = new AbortController();
  const startedAt = performance.now();
  const timeout = setTimeout(() => controller.abort("stage complete"), durationMs);
  const eventTimes = [];
  let connected = false;
  let status = 0;
  let abnormalDisconnect = false;

  try {
    const response = await fetch(endpoint, {
      headers: {
        Accept: "text/event-stream",
        Authorization: `Bearer ${viewerToken}`,
        "X-Load-Test-Connection": String(index),
      },
      cache: "no-store",
      signal: controller.signal,
    });
    status = response.status;
    const contentType = response.headers.get("content-type") || "";
    connected = response.status === 200 && contentType.includes("text/event-stream");
    if (!connected || !response.body) {
      return result();
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        abnormalDisconnect = performance.now() - startedAt < durationMs * 0.9;
        break;
      }
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");
      let boundary;
      while ((boundary = buffer.indexOf("\n\n")) >= 0) {
        const rawEvent = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        if (rawEvent.split("\n").some((line) => line.startsWith("data:"))) {
          eventTimes.push(performance.now());
        }
      }
    }
  } catch (error) {
    abnormalDisconnect = !controller.signal.aborted;
  } finally {
    clearTimeout(timeout);
  }

  return result();

  function result() {
    return {
      connected,
      status,
      abnormalDisconnect,
      events: eventTimes.length,
      firstEventLatencyMs: eventTimes.length > 0 ? eventTimes[0] - startedAt : null,
      eventGapsMs: eventTimes.slice(1).map((time, eventIndex) => time - eventTimes[eventIndex]),
    };
  }
}

function printStage(result) {
  const format = (value) => Number.isFinite(value) ? `${value.toFixed(0)}ms` : "n/a";
  console.log(
    [
      `SSE ${result.connections} connections: ${result.passed ? "PASS" : "FAIL"}`,
      `connected=${result.connected}/${result.connections}`,
      `eventful=${result.eventful}/${result.connected}`,
      `events=${result.totalEvents}`,
      `first-event-p95=${format(result.firstEventP95)}`,
      `event-gap-p95=${format(result.eventGapP95)}`,
      `abnormal=${result.abnormalDisconnects}`,
      `5xx=${result.serverErrors}`,
      `elapsed=${format(result.elapsedMs)}`,
    ].join(" | ")
  );
}

function percentile(values, percentileNumber) {
  if (values.length === 0) {
    return Number.POSITIVE_INFINITY;
  }
  const sorted = [...values].sort((left, right) => left - right);
  const index = Math.ceil((percentileNumber / 100) * sorted.length) - 1;
  return sorted[Math.max(0, index)];
}

function parseDuration(value) {
  const match = /^(\d+)(ms|s|m)$/.exec(value);
  if (!match) {
    throw new Error("STAGE_DURATION must look like 30s, 2m or 1500ms");
  }
  const amount = Number(match[1]);
  return amount * ({ ms: 1, s: 1_000, m: 60_000 })[match[2]];
}
