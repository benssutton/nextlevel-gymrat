import { check } from 'k6';

export function checkStatus200(res) {
  return check(res, { 'status is 200': (r) => r.status === 200 });
}

export function checkConfigList(res) {
  return check(res, {
    'status is 200': (r) => r.status === 200,
    'body is a list of config entries': (r) => {
      try { return Array.isArray(JSON.parse(r.body)); }
      catch { return false; }
    },
  });
}
