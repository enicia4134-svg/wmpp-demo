import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 20,
  duration: '30s',
};

export default function () {
  const res = http.get('http://127.0.0.1:8080/admin/console/overview', {
    headers: {
      'X-Admin-Token': 'change-me',
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
