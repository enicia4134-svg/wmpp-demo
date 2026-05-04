import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 500,
      stages: [
        { target: 0, duration: '5s' },
        { target: 500, duration: '5s' },
        { target: 500, duration: '30s' },
        { target: 0, duration: '5s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<3000'],
  },
};

export default function () {
  const appId = 'systemA';
  const userId = String(900000 + (__VU % 1000));
  const res = http.get(`http://127.0.0.1:8080/api/connect?appId=${appId}&userId=${userId}`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has wsUrl': (r) => r.json('wsUrl') !== undefined,
    'has sseUrl': (r) => r.json('sseUrl') !== undefined,
  });
}
