import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    online: {
      executor: 'constant-vus',
      vus: 100,
      duration: '2m',
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

function connectClient(appId, userId) {
  const res = http.get(`http://127.0.0.1:8080/api/connect?appId=${appId}&userId=${userId}`);
  check(res, { 'connect ok': (r) => r.status === 200 });
  const endpoints = res.json();
  if (!endpoints || !endpoints.wsUrl) return null;
  return endpoints.wsUrl;
}

export default function () {
  const appId = 'systemA';
  const userId = String(700000 + (__VU % 1000));
  const wsUrl = connectClient(appId, userId);
  if (!wsUrl) {
    sleep(1);
    return;
  }

  const response = ws.connect(wsUrl, {}, function (socket) {
    socket.on('open', function () {
      socket.setTimeout(function () {
        socket.close();
      }, 10000);
    });

    socket.on('message', function () {
      // keep connection alive while messages are delivered
    });

    socket.on('error', function () {
      // ignore baseline errors
    });
  });

  check(response, {
    'ws upgrade ok': (r) => r && r.status === 101,
  });

  sleep(1);
}
