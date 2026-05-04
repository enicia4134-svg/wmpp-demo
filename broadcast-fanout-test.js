import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    fanout: {
      executor: 'constant-vus',
      vus: 30,
      duration: '5m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const appId = 'systemA';
  const message = `fanout-${__VU}-${__ITER}-${Date.now()}`;
  const res = http.post(
    `http://127.0.0.1:8081/scheduler/broadcast?appId=${encodeURIComponent(appId)}&message=${encodeURIComponent(message)}`
  );

  check(res, {
    'broadcast accepted': (r) => r.status >= 200 && r.status < 300,
  });

  sleep(5);
}
