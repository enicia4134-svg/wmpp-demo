import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 20,
  duration: '30s',
};

export default function () {
  const res = http.get('http://127.0.0.1:8080/tenant/routes', {
    headers: {
      'X-App-Id': 'systemA',
      'X-App-Secret-Key': 'systemA-secret',
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
