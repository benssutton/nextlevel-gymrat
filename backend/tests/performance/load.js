import http from 'k6/http';
import { group, sleep } from 'k6';
import { checkStatus200, checkConfigList } from './lib/checks.js';
import { NORMAL_SLO, STRICT_SLO } from './lib/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'https://localhost';

export const options = {
  scenarios: {
    browse_config: {
      executor: 'ramping-vus',
      exec: 'browseConfig',
      stages: [
        { duration: '30s', target: 10 },
        { duration: '60s', target: 10 },
        { duration: '30s', target: 0 },
      ],
      tags: { scenario: 'browse_config' },
    },
    health_poll: {
      executor: 'constant-vus',
      exec: 'healthPoll',
      vus: 2,
      duration: '2m',
      tags: { scenario: 'health_poll' },
    },
  },
  thresholds: {
    'http_req_duration{scenario:browse_config}': NORMAL_SLO.http_req_duration,
    'http_req_failed{scenario:browse_config}':   NORMAL_SLO.http_req_failed,
    'http_req_duration{scenario:health_poll}': STRICT_SLO.http_req_duration,
    'http_req_failed{scenario:health_poll}':   STRICT_SLO.http_req_failed,
  },
};

export function browseConfig() {
  group('config', () => {
    checkConfigList(http.get(`${BASE_URL}/config/`));
  });
  sleep(1);
}

export function healthPoll() {
  group('health', () => {
    checkStatus200(http.get(`${BASE_URL}/health/status`));
  });
  sleep(1);
}
