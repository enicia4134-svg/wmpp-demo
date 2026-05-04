import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    recovery: {
      executor: 'constant-vus',
      vus: 10,
      duration: '5m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const appId = 'systemA';
  const userId = String(950000 + (__VU % 1000));
  const res = http.get(`http://127.0.0.1:8080/api/connect?appId=${appId}&userId=${userId}`);

  check(res, {
    'connect ok': (r) => r.status === 200,
  });

  sleep(2);
}
