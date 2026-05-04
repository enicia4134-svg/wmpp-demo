import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    storm: {
      executor: 'constant-vus',
      vus: 50,
      duration: '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const appId = 'systemA';
  const userId = String(500000 + (__VU % 1000));

  const connectRes = http.get(`http://127.0.0.1:8080/api/connect?appId=${appId}&userId=${userId}`);
  check(connectRes, {
    'connect ok': (r) => r.status === 200,
  });

  const endpoints = connectRes.json();
  if (!endpoints || !endpoints.wsUrl) {
    return;
  }

  const response = ws.connect(endpoints.wsUrl, {}, function (socket) {
    socket.on('open', function () {
      socket.send('ping');
      sleep(1);
      socket.close();
    });

    socket.on('message', function () {
      // ignore; just keep the connection briefly
    });

    socket.on('error', function () {
      // ignore for storm baseline
    });
  });

  check(response, {
    'ws upgrade ok': (r) => r && r.status === 101,
  });
}
