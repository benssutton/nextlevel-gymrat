import http from 'k6/http';
import { sleep } from 'k6';
import { checkStatus200, checkConfigList } from './lib/checks.js';
import { STRICT_SLO } from './lib/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'https://localhost';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: { ...STRICT_SLO },
};

export default function () {
  checkStatus200(http.get(`${BASE_URL}/`,              { tags: { endpoint: 'root' } }));
  checkStatus200(http.get(`${BASE_URL}/health/status`, { tags: { endpoint: 'health' } }));
  checkConfigList(http.get(`${BASE_URL}/config/`,      { tags: { endpoint: 'config' } }));
  sleep(1);
}
