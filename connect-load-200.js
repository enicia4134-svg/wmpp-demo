import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
  },
};

export default function () {
  const appId = 'systemA';
  const userId = String(300000 + (__VU % 1000));
  const res = http.get(`http://127.0.0.1:8080/api/connect?appId=${appId}&userId=${userId}`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has wsUrl': (r) => r.json('wsUrl') !== undefined,
    'has sseUrl': (r) => r.json('sseUrl') !== undefined,
  });
  sleep(1);
}
