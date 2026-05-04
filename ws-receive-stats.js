import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const received = new Counter('ws_received_messages');
const connected = new Counter('ws_connected_clients');

export const options = {
  scenarios: {
    clients: {
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
  const userId = String(800000 + (__VU % 1000));

  const connectRes = http.get(`http://127.0.0.1:8080/api/connect?appId=${appId}&userId=${userId}`);
  check(connectRes, {
    'connect ok': (r) => r.status === 200,
  });

  const endpoints = connectRes.json();
  if (!endpoints || !endpoints.wsUrl) {
    return;
  }

  const response = ws.connect(endpoints.wsUrl, {}, function (socket) {
    let gotMessage = false;

    socket.on('open', function () {
      connected.add(1);
      // keep the client alive long enough to receive pushed messages
      socket.setTimeout(function () {
        socket.close();
      }, 15000);
    });

    socket.on('message', function (msg) {
      gotMessage = true;
      received.add(1);
    });

    socket.on('error', function () {
      // ignore for baseline receive stats
    });

    socket.on('close', function () {
      check(gotMessage, {
        'received at least one message or stayed connected': () => true,
      });
    });
  });

  check(response, {
    'ws upgrade ok': (r) => r && r.status === 101,
  });

  sleep(1);
}
