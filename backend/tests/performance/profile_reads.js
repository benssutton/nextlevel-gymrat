import http from 'k6/http';
import { check } from 'k6';
import { recordServerTiming, summarize } from './lib/serverTiming.js';
import { NORMAL_SLO } from './lib/thresholds.js';

const BASE = __ENV.BASE_URL || 'https://localhost';

export const options = {
  scenarios: {
    reads: {
      executor: 'constant-vus',
      vus: parseInt(__ENV.VUS || '10', 10),
      duration: __ENV.DURATION || '60s',
    },
  },
  thresholds: { ...NORMAL_SLO },   // report-only attribution; SLOs unchanged
};

export default function () {
  const configRes = http.get(`${BASE}/config/`);
  check(configRes, { 'GET /config/ is 200': (r) => r.status === 200 });
  recordServerTiming(configRes, 'config');
}

export function handleSummary(data) {
  return summarize(data);
}
