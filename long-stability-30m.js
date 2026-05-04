import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';

export const options = {
  scenarios: {
    stable: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const appId = 'systemA';
  const userId = String(910000 + (__VU % 1000));

  const connectRes = http.get(`http://127.0.0.1:8080/api/connect?appId=${appId}&userId=${userId}`);
  check(connectRes, { 'connect ok': (r) => r.status === 200 });

  const endpoints = connectRes.json();
  if (!endpoints || !endpoints.wsUrl) return;

  const res = ws.connect(endpoints.wsUrl, {}, function (socket) {
    socket.on('open', function () {
      socket.setInterval(function () {
        socket.send('ping');
      }, 10000);
      socket.setTimeout(function () {
        socket.close();
      }, 1800000);
    });
  });

  check(res, { 'ws upgrade ok': (r) => r && r.status === 101 });
}
