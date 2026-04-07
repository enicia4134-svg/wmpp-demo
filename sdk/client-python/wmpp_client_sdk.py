from __future__ import annotations

import os
import threading
import time
from dataclasses import dataclass
from typing import Callable, Optional

import requests
try:
    import websocket  # type: ignore
except Exception:  # pragma: no cover
    websocket = None


@dataclass(frozen=True)
class WmppClientOptions:
    base_http: str
    base_ws: str
    app_id: str
    user_id: str
    heartbeat_interval_s: float = 15.0


class WmppClient:
    def __init__(self, opts: WmppClientOptions, on_message: Optional[Callable[[str], None]] = None):
        self.opts = opts
        self.on_message = on_message or (lambda s: print("WMPP:", s))
        self._stop = threading.Event()
        self._ws: Optional[websocket.WebSocketApp] = None

    def start(self):
        self._stop.clear()
        threading.Thread(target=self._run_sse, name="wmpp-sse", daemon=True).start()
        threading.Thread(target=self._run_ws, name="wmpp-ws", daemon=True).start()

    def stop(self):
        self._stop.set()
        try:
            if self._ws is not None:
                self._ws.close()
        except Exception:
            pass

    def _run_sse(self):
        url = f"{self.opts.base_http}/stream?appId={self.opts.app_id}&userId={self.opts.user_id}"
        while not self._stop.is_set():
            try:
                with requests.get(url, stream=True, timeout=10) as resp:
                    resp.raise_for_status()
                    for raw in resp.iter_lines(decode_unicode=True):
                        if self._stop.is_set():
                            return
                        if not raw:
                            continue
                        if raw.startswith("data:"):
                            data = raw[len("data:"):].strip()
                            if data:
                                self.on_message(data)
            except Exception:
                time.sleep(2)

    def _run_ws(self):
        if websocket is None:
            # Environment without websocket-client installed: keep SSE only.
            return

        url = f"{self.opts.base_ws}/ws/push?appId={self.opts.app_id}&userId={self.opts.user_id}"

        def on_open(wsapp):
            def hb():
                while not self._stop.is_set():
                    try:
                        wsapp.send("ping")
                    except Exception:
                        break
                    time.sleep(self.opts.heartbeat_interval_s)

            threading.Thread(target=hb, name="wmpp-hb", daemon=True).start()

        def on_message(wsapp, msg: str):
            if msg == "pong":
                return
            # control channel messages (if any)
            self.on_message(f"(ws){msg}")

        def on_close(wsapp, *_):
            pass

        while not self._stop.is_set():
            try:
                self._ws = websocket.WebSocketApp(url, on_open=on_open, on_message=on_message, on_close=on_close)
                self._ws.run_forever(ping_interval=None)
            except Exception:
                pass
            time.sleep(2)


def main():
    base_http = os.getenv("WMPP_HTTP", "http://localhost:8084")
    base_ws = os.getenv("WMPP_WS", "ws://localhost:8084")
    app_id = os.getenv("WMPP_APP_ID", "systemA")
    user_id = os.getenv("WMPP_USER_ID", "1001")
    client = WmppClient(WmppClientOptions(base_http=base_http, base_ws=base_ws, app_id=app_id, user_id=user_id))
    client.start()
    print("Client started. Ctrl+C to exit.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        client.stop()


if __name__ == "__main__":
    main()

