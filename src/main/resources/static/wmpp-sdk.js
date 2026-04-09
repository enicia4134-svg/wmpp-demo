window.wmpp = {

    ws: null,
    es: null,
    heartbeatTimer: null,
    reconnectTimer: null,
    wsRecoveryTimer: null,
    opts: null,
    endpoints: null,
    mode: "idle", // idle | ws | sse
    wsRetryCount: 0,
    lastWsCloseAt: 0,
    dedupeCache: new Map(),

    connect: async function (appId, userId, options) {
        this.disconnect()

        this.opts = {
            appId,
            userId,
            // Production default: WS first, SSE fallback.
            transportPolicy: options && options.transportPolicy ? options.transportPolicy : "ws_primary_sse_fallback",
            heartbeatMs: options && options.heartbeatMs ? options.heartbeatMs : 15000,
            wsStableMs: options && options.wsStableMs ? options.wsStableMs : 10000,
            wsRecoveryMs: options && options.wsRecoveryMs ? options.wsRecoveryMs : 15000,
            wsReconnectMinMs: options && options.wsReconnectMinMs ? options.wsReconnectMinMs : 1000,
            wsReconnectMaxMs: options && options.wsReconnectMaxMs ? options.wsReconnectMaxMs : 15000,
            dedupeWindowMs: options && options.dedupeWindowMs ? options.dedupeWindowMs : 30000,
            onMessage: options && typeof options.onMessage === "function" ? options.onMessage : null
        }

        await this._resolveEndpoints()

        if (this.opts.transportPolicy === "ws_only") {
            this._openWs(true)
            return
        }

        if (this.opts.transportPolicy === "sse_only") {
            this._switchToSse("transport policy: sse_only")
            return
        }

        // default: ws_primary_sse_fallback
        this._openWs(true)
    },

    disconnect: function () {
        this._clearReconnect()
        this._clearWsRecovery()
        this._stopHeartbeat()

        if (this.ws) {
            try { this.ws.close() } catch (e) { }
            this.ws = null
        }
        if (this.es) {
            try { this.es.close() } catch (e) { }
            this.es = null
        }

        this.mode = "idle"
        this.wsRetryCount = 0
        this.dedupeCache.clear()
    },

    _resolveEndpoints: async function () {
        try {
            const appId = this.opts.appId
            const userId = this.opts.userId
            const resp = await fetch("/api/connect?appId=" + encodeURIComponent(appId) + "&userId=" + encodeURIComponent(userId))
            if (resp.ok) {
                this.endpoints = await resp.json()
                return
            }
            throw new Error("/api/connect not ok: " + resp.status)
        } catch (e) {
            throw new Error("resolve endpoints failed: " + (e && e.message ? e.message : e))
        }
    },

    _wsUrl: function () {
        if (!this.endpoints || !this.endpoints.wsUrl) {
            throw new Error("wsUrl missing from /api/connect")
        }
        return this.endpoints.wsUrl
    },

    _sseUrl: function () {
        if (!this.endpoints || !this.endpoints.sseUrl) {
            throw new Error("sseUrl missing from /api/connect")
        }
        return this.endpoints.sseUrl
    },

    _openWs: function (initial) {
        this._clearReconnect()

        const url = this._wsUrl()

        if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
            return
        }

        this.ws = new WebSocket(url)
        const openedAt = Date.now()

        this.ws.onopen = () => {
            this.mode = "ws"
            this.wsRetryCount = 0
            console.log("WMPP WS Connected")
            this._stopSseIfAny()
            this._clearWsRecovery()
            this._startHeartbeat()
        }

        this.ws.onmessage = (e) => {
            if (e.data === "pong") return
            this._handleInbound("ws", e.data)
        }

        this.ws.onerror = () => {
            // rely on onclose for state transitions
        }

        this.ws.onclose = () => {
            const livedMs = Date.now() - openedAt
            this.ws = null
            this._stopHeartbeat()
            this.lastWsCloseAt = Date.now()

            if (!this.opts) return
            if (this.opts.transportPolicy === "ws_only") {
                this._scheduleWsReconnect()
                return
            }

            // In default policy, quickly fallback to SSE when WS is unstable/unavailable.
            if (initial || livedMs < this.opts.wsStableMs || this.mode !== "sse") {
                this._switchToSse("ws closed")
            }
            this._scheduleWsRecoveryProbe()
        }
    },

    _switchToSse: function (reason) {
        if (this.mode !== "sse") {
            console.log("WMPP fallback to SSE:", reason)
        }
        this.mode = "sse"

        const url = this._sseUrl()

        if (this.es) {
            try { this.es.close() } catch (e) { }
            this.es = null
        }

        this.es = new EventSource(url)

        this.es.onopen = () => {
            console.log("WMPP SSE Open")
        }

        this.es.onmessage = (e) => {
            this._handleInbound("sse", e.data)
        }

        this.es.onerror = () => {
            // EventSource auto-reconnect handles transient network errors.
            console.log("WMPP SSE Error/Closed")
        }
    },

    _stopSseIfAny: function () {
        if (this.es) {
            try { this.es.close() } catch (e) { }
            this.es = null
            console.log("WMPP SSE Closed (WS active)")
        }
    },

    _scheduleWsReconnect: function () {
        this._clearReconnect()
        const delay = this._nextBackoffMs()
        this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null
            if (!this.opts) return
            this._openWs(false)
        }, delay)
    },

    _scheduleWsRecoveryProbe: function () {
        if (this.opts.transportPolicy !== "ws_primary_sse_fallback") return
        if (this.wsRecoveryTimer) return

        this.wsRecoveryTimer = setInterval(() => {
            if (!this.opts) return
            if (this.mode !== "sse") return
            if (this.ws) return
            this._openWs(false)
        }, this.opts.wsRecoveryMs)
    },

    _clearReconnect: function () {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer)
            this.reconnectTimer = null
        }
    },

    _clearWsRecovery: function () {
        if (this.wsRecoveryTimer) {
            clearInterval(this.wsRecoveryTimer)
            this.wsRecoveryTimer = null
        }
    },

    _nextBackoffMs: function () {
        this.wsRetryCount += 1
        const min = this.opts.wsReconnectMinMs
        const max = this.opts.wsReconnectMaxMs
        const exp = Math.min(max, min * Math.pow(2, this.wsRetryCount - 1))
        const jitter = Math.floor(Math.random() * Math.max(250, Math.floor(exp * 0.2)))
        return Math.min(max, exp + jitter)
    },

    _startHeartbeat: function () {
        this._stopHeartbeat()
        this.heartbeatTimer = setInterval(() => {
            try {
                if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                    this.ws.send("ping")
                }
            } catch (e) { }
        }, this.opts.heartbeatMs)
    },

    _stopHeartbeat: function () {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer)
            this.heartbeatTimer = null
        }
    },

    _handleInbound: function (channel, rawData) {
        const event = this._normalizeEvent(rawData)
        if (this._isDuplicate(event.id)) {
            return
        }

        this._autoAck(event.id)

        if (this.opts && this.opts.onMessage) {
            try {
                this.opts.onMessage({ channel, ...event })
            } catch (e) {
                console.error("WMPP onMessage callback error:", e)
            }
            return
        }

        // Default behavior: prefer envelope payload for readability; fallback to raw text.
        const shown = (event.body && typeof event.body === "object" && event.body.payload != null)
            ? event.body.payload
            : event.raw
        console.log((channel || "msg").toUpperCase() + ":", shown)
    },

    _normalizeEvent: function (rawData) {
        const text = String(rawData)
        let parsed = null
        try {
            parsed = JSON.parse(text)
        } catch (e) { }

        // Preferred payload: { msgId, type, payload, ... }
        const msgId = parsed && typeof parsed === "object" && parsed.msgId ? String(parsed.msgId) : null
        const preferredRaw = parsed && typeof parsed === "object" && Object.prototype.hasOwnProperty.call(parsed, "payload")
            ? String(parsed.payload)
            : text

        return {
            id: msgId || null,
            raw: preferredRaw,
            body: parsed
        }
    },

    _isDuplicate: function (msgId) {
        this._evictDedupeCache()
        if (!msgId) return false

        if (this.dedupeCache.has(msgId)) {
            return true
        }

        this.dedupeCache.set(msgId, Date.now())
        return false
    },

    _evictDedupeCache: function () {
        const now = Date.now()
        const ttl = (this.opts && this.opts.dedupeWindowMs) ? this.opts.dedupeWindowMs : 30000
        for (const [key, ts] of this.dedupeCache.entries()) {
            if (now - ts > ttl) {
                this.dedupeCache.delete(key)
            }
        }
    },

    _autoAck: function (msgId) {
        if (!msgId) return
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return
        try {
            this.ws.send(JSON.stringify({ type: "ack", msgId: msgId }))
        } catch (e) {
            // ignore ack send errors, retry path will handle eventual consistency
        }
    },

    send: function (msg) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            throw new Error("WS not connected; send is only available on ws channel")
        }
        this.ws.send(msg)
    }
}
