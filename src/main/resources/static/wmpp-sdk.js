window.wmpp = {

    ws:null,
    es:null,
    heartbeatTimer:null,
    reconnectTimer:null,
    opts:null,
    endpoints:null,

    connect:function(appId,userId){

        this.opts = { appId, userId }

        this._resolveEndpoints().then(()=>{
            this._connectWs()
            this._connectSse()
        })
    },

    _resolveEndpoints: async function(){
        // Prefer gateway /connect for microservices mode.
        // If it fails, fall back to same-origin localhost:8080 behavior.
        try{
            const appId = this.opts.appId
            const userId = this.opts.userId
            const resp = await fetch("/api/connect?appId="+encodeURIComponent(appId)+"&userId="+encodeURIComponent(userId))
            if(resp.ok){
                const data = await resp.json()
                this.endpoints = data
                return
            }
        }catch(e){}
        this.endpoints = null
    },

    _connectWs:function(){

        let appId = this.opts.appId
        let userId = this.opts.userId
        let url = (this.endpoints && this.endpoints.wsUrl)
            ? this.endpoints.wsUrl
            : ("ws://localhost:8080/connect?appId="+encodeURIComponent(appId)+"&userId="+encodeURIComponent(userId))

        // One WS per tab: avoid closing a healthy socket when connect() / race fires twice.
        if(this.ws && this.ws.readyState === WebSocket.OPEN && this._activeWsUrl === url){
            return
        }
        this._activeWsUrl = url

        if(this.ws && this.ws.readyState === WebSocket.OPEN){
            this.ws.close()
        }

        this.ws = new WebSocket(url)

        this.ws.onopen = ()=> {
            console.log("WMPP WS Connected")
            this._startHeartbeat()
        }

        this.ws.onmessage = (e)=> {
            if(e.data === "pong") return
            console.log("WS:",e.data)
        }

        this.ws.onclose = ()=> {
            console.log("WMPP WS Closed")
            this._stopHeartbeat()
            this._scheduleReconnect()
        }
    },

    _connectSse:function(){
        let appId = this.opts.appId
        let userId = this.opts.userId
        let url = (this.endpoints && this.endpoints.sseUrl)
            ? this.endpoints.sseUrl
            : ("http://localhost:8080/stream?appId="+encodeURIComponent(appId)+"&userId="+encodeURIComponent(userId))

        if(this.es){
            this.es.close()
        }

        this.es = new EventSource(url)

        this.es.onopen = ()=> console.log("WMPP SSE Open")

        this.es.onmessage = (e)=> console.log("SSE:",e.data)

        this.es.addEventListener("message", (e)=> console.log("SSE:", e.data))

        this.es.onerror = ()=> {
            console.log("WMPP SSE Error/Closed")
            // EventSource auto-reconnects internally; keep it simple here.
        }
    },

    _startHeartbeat:function(){
        this._stopHeartbeat()
        this.heartbeatTimer = setInterval(()=>{
            try{
                if(this.ws && this.ws.readyState === WebSocket.OPEN){
                    this.ws.send("ping")
                }
            }catch(e){}
        }, 15000)
    },

    _stopHeartbeat:function(){
        if(this.heartbeatTimer){
            clearInterval(this.heartbeatTimer)
            this.heartbeatTimer = null
        }
    },

    _scheduleReconnect:function(){
        if(this.reconnectTimer) return
        this.reconnectTimer = setTimeout(()=>{
            this.reconnectTimer = null
            if(!this.opts) return
            this._connectWs()
        }, 2000)
    },

    send:function(msg){

        this.ws.send(msg)
    }
}