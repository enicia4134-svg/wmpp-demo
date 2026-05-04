import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 20,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const appId = 'systemA';
  const userId = String(1001 + (__VU % 2) * 1001);
  const message = `direct-${__VU}-${__ITER}`;
  const res = http.post(
    `http://127.0.0.1:8081/scheduler/user?appId=${encodeURIComponent(appId)}&userId=${encodeURIComponent(userId)}&message=${encodeURIComponent(message)}`
  );

  check(res, {
    'user push accepted': (r) => r.status >= 200 && r.status < 300,
  });

  sleep(1);
}
