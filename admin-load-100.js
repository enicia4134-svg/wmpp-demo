import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '15s', target: 50 },
    { duration: '45s', target: 100 },
    { duration: '15s', target: 0 },
  ],
};

export default function () {
  const res = http.get('http://127.0.0.1:8080/admin/console/overview', {
    headers: { 'X-Admin-Token': 'change-me' },
  });

  check(res, { 'status is 200': (r) => r.status === 200 });
}
