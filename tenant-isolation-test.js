import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    isolation: {
      executor: 'constant-vus',
      vus: 30,
      duration: '3m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

const tenants = [
  { appId: 'systemA', secret: 'systemA-secret' },
  { appId: 'systemB', secret: 'systemB-secret' },
  { appId: 'systemC', secret: 'systemC-secret' },
];

export default function () {
  const t = tenants[__VU % tenants.length];
  const res = http.get('http://127.0.0.1:8080/tenant/routes', {
    headers: {
      'X-App-Id': t.appId,
      'X-App-Secret-Key': t.secret,
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
