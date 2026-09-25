#!/usr/bin/env node
/**
 * Stand-in for the two external services the portal talks to.
 *
 *   POST /phiBela/getAmboTransaction   mimics the core banking transaction feed
 *   POST /notify                       mimics the bank's email/notification endpoint
 *   GET  /notify/inbox                 the messages "sent" so far
 *   POST /notify/reset                 clears the inbox
 *   GET  /health                       health check
 *
 * Zero dependencies - run it with plain Node 18+:
 *
 *   node tools/mock-bank-services/server.mjs
 *
 * Environment:
 *   PORT          default 5468 (the port the real feed listens on)
 *   EMPTY_ACCOUNTS  comma separated account numbers that always return no
 *                   transactions, for testing the "skip empty periods" path
 */

import http from 'node:http';

const PORT = Number(process.env.PORT ?? 5468);
const EMPTY_ACCOUNTS = new Set(
  (process.env.EMPTY_ACCOUNTS ?? '9999999999999').split(',').map((s) => s.trim()).filter(Boolean)
);

const inbox = [];

// ---------------------------------------------------------------- date parsing

const MONTHS = {
  jan: 0, feb: 1, mar: 2, apr: 3, may: 4, jun: 5,
  jul: 6, aug: 7, sep: 8, oct: 9, nov: 10, dec: 11,
};

/** Parses the dd-MMM-yy format used by the core banking feed, e.g. 01-Sep-26. */
function parseFeedDate(value) {
  if (!value) return null;
  const match = /^(\d{1,2})-([A-Za-z]{3})-(\d{2}|\d{4})$/.exec(String(value).trim());
  if (!match) {
    // Fall back to ISO, which makes manual curl testing easier.
    const iso = Date.parse(value);
    return Number.isNaN(iso) ? null : new Date(iso);
  }
  const day = Number(match[1]);
  const month = MONTHS[match[2].toLowerCase()];
  let year = Number(match[3]);
  if (match[3].length === 2) year += 2000;
  if (month === undefined) return null;
  return new Date(Date.UTC(year, month, day));
}

const iso = (date) => date.toISOString().slice(0, 10);
const money = (value) => value.toFixed(2);

// ------------------------------------------------------- deterministic random

function* prng(seed) {
  let state = seed >>> 0 || 1;
  while (true) {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    state >>>= 0;
    yield state / 0xffffffff;
  }
}

const hash = (text) => {
  let value = 2166136261;
  for (let i = 0; i < text.length; i++) {
    value ^= text.charCodeAt(i);
    value = Math.imul(value, 16777619);
  }
  return value >>> 0;
};

const NARRATIVES = [
  ['CASH FCY BOUGHT AND SOLD', true],
  ['SERVICE CHARGE FOR CORESPONDENT BANK', true],
  ['INWARD REMITTANCE - EXPORT PROCEEDS', false],
  ['SALARY PAYMENT BATCH', true],
  ['TT OUTWARD PAYMENT', true],
  ['INTEREST CREDIT', false],
  ['CASH DEPOSIT AT BRANCH', false],
  ['CARD SETTLEMENT', true],
];

/**
 * Builds a plausible, deterministic set of transactions for the requested range
 * so the same account and dates always produce the same statement.
 */
function buildTransactions(accountNumber, from, to) {
  const rows = [];
  let balance = 327060.15 + (hash(accountNumber) % 50000) / 100;

  const dayMs = 86400000;
  for (let time = from.getTime(); time <= to.getTime(); time += dayMs) {
    const day = new Date(time);
    const random = prng(hash(accountNumber + iso(day)));

    const count = 1 + Math.floor(random.next().value * 3);
    for (let index = 0; index < count; index++) {
      const [description, isDebit] = NARRATIVES[Math.floor(random.next().value * NARRATIVES.length)];
      const amount = Math.round((20 + random.next().value * 48000) * 100) / 100;

      const beginning = balance;
      balance = isDebit
        ? Math.round((balance - amount) * 100) / 100
        : Math.round((balance + amount) * 100) / 100;

      const sequence = String(rows.length + 1).padStart(4, '0');
      rows.push({
        transactionDate: iso(day),
        trans_reference: `${accountNumber.slice(-4)}${iso(day).replace(/-/g, '')}${sequence} ACDB/TT/${sequence}/26`,
        debit_amt: isDebit ? money(amount) : '0.00',
        credit_amt: isDebit ? '0.00' : money(amount),
        beginning_balance: money(beginning),
        closing_balance: money(balance),
        txnDescription: description,
      });
    }
  }
  return rows;
}

// ------------------------------------------------------------------ http glue

function readBody(request) {
  return new Promise((resolve, reject) => {
    let raw = '';
    request.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 1_000_000) request.destroy();
    });
    request.on('end', () => resolve(raw));
    request.on('error', reject);
  });
}

const send = (response, status, payload, contentType = 'application/json') => {
  const body = typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2);
  response.writeHead(status, {
    'Content-Type': contentType,
    'Content-Length': Buffer.byteLength(body),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
  });
  response.end(body);
};

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host}`);
  const started = Date.now();

  if (request.method === 'OPTIONS') {
    return send(response, 204, '');
  }

  if (url.pathname === '/health') {
    return send(response, 200, { status: 'UP', inbox: inbox.length });
  }

  // --- core banking transaction feed ----------------------------------------
  if (url.pathname === '/phiBela/getAmboTransaction' && request.method === 'POST') {
    const raw = await readBody(request);
    let body = {};
    try {
      body = raw ? JSON.parse(raw) : {};
    } catch {
      return send(response, 400, { statusCode: 400, message: 'Malformed JSON body', data: [] });
    }

    const accountNumber = String(body.accountNumber ?? '').trim();
    const from = parseFeedDate(body.startDate);
    const to = parseFeedDate(body.endDate);

    console.log(`[feed] ${accountNumber} ${body.startDate} -> ${body.endDate}`);

    if (!accountNumber || !from || !to) {
      return send(response, 200, {
        statusCode: 400,
        message: 'accountNumber, startDate and endDate are required (dd-MMM-yy)',
        data: [],
      });
    }
    if (from.getTime() > to.getTime()) {
      return send(response, 200, {
        statusCode: 400,
        message: 'startDate must not be after endDate',
        data: [],
      });
    }

    const data = EMPTY_ACCOUNTS.has(accountNumber) ? [] : buildTransactions(accountNumber, from, to);
    console.log(`[feed] -> ${data.length} transaction(s) in ${Date.now() - started}ms`);

    return send(response, 200, {
      statusCode: 200,
      message: 'All transactions',
      data,
    });
  }

  // --- notification / email endpoint -----------------------------------------
  if (url.pathname === '/notify' && request.method === 'POST') {
    const raw = await readBody(request);
    let body = {};
    try {
      body = raw ? JSON.parse(raw) : {};
    } catch {
      return send(response, 400, { error: 'Malformed JSON body' });
    }

    const entry = {
      receivedAt: new Date().toISOString(),
      to: body.to,
      cc: body.cc,
      bcc: body.bcc,
      subject: body.subject,
      attachments: (body.attachments ?? []).map((attachment) => ({
        fileName: attachment?.fileName,
        contentType: attachment?.contentType,
        bytes: attachment?.contentBase64
          ? Math.floor((attachment.contentBase64.length * 3) / 4)
          : 0,
      })),
      raw: body,
    };
    inbox.push(entry);

    console.log(`[notify] -> ${entry.to} "${entry.subject}" (${JSON.stringify(entry.attachments)})`);
    return send(response, 202, { accepted: true, id: inbox.length });
  }

  if (url.pathname === '/notify/inbox') {
    return send(response, 200, inbox);
  }

  if (url.pathname === '/notify/reset' && request.method === 'POST') {
    inbox.length = 0;
    return send(response, 200, { cleared: true });
  }

  // --- convenience: preview what the feed returns for a plain GET ------------
  if (url.pathname === '/phiBela/getAmboTransaction') {
    return send(response, 200, {
      statusCode: 405,
      message: 'Use POST with {accountNumber, startDate, endDate}',
      data: [],
    });
  }

  return send(response, 404, { error: 'Not found', path: url.pathname });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Mock bank services listening on http://0.0.0.0:${PORT}`);
  console.log(`  transaction feed : POST http://localhost:${PORT}/phiBela/getAmboTransaction`);
  console.log(`  notification     : POST http://localhost:${PORT}/notify`);
  console.log(`  sent messages    : GET  http://localhost:${PORT}/notify/inbox`);
  console.log(`  empty accounts   : ${[...EMPTY_ACCOUNTS].join(', ') || '(none)'}`);
});
