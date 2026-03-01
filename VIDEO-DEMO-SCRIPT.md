# Video Demonstration Script — Forward Web Proxy (Max 5 mins)

**Format:** MP4 or MOV, high resolution (e.g. 1080p).  
**Tips:** Use a large font in the IDE (e.g. 16–18pt). Zoom code when showing it. Speak clearly and at a steady pace; pause briefly between sections. Rehearse once to stay under 5 minutes.

---

## 1. Introduction (≈30 sec)

*[Face to camera or voice-over; project visible in background.]*

"Hi, I'm Joel Mathew Jojan. This is my submission for CSU33032 Project 1: a forward web proxy in Java.

The proxy sits between the browser and the internet. The browser sends all HTTP and HTTPS requests to the proxy; the proxy can block URLs, cache HTTP responses, and forward or tunnel traffic to the origin server. I'll show it running, then explain the design and walk through the important code."

---

## 2. Working Prototype Demo (≈1 min 45 sec)

*[Share screen: terminal + management console. Have proxy already compiled.]*

**Start the proxy**

"From the project directory I run: java ProxyServer 8080."

*[Run: `java ProxyServer 8080`]*

"The proxy listens on port 8080 and the management console window opens. It has three tabs: Requests, Block list, and Cache."

*[Show the console window and the three tabs.]*

**HTTP and cache**

"My browser is set to use this machine as the HTTP and HTTPS proxy. I'll open a simple HTTP site — for example http://neverssl.com."

*[Open http://neverssl.com in the browser.]*

"In the Requests tab we see the request: method GET, host neverssl.com, path slash. Source is 'Origin' and we get a response time in milliseconds — meaning it was fetched from the origin server. I'll refresh the page."

*[Refresh.]*

"Now the same request appears again, but the source is 'Cache' and the time in milliseconds is much lower. That shows the response was served from the proxy cache. In the Cache tab we can see the stored entry for neverssl.com."

*[Switch to Cache tab, show the entry.]*

**HTTPS (CONNECT)**

"For HTTPS I'll open any HTTPS site. In the request log we see CONNECT to the host, and the source is 'Tunnel'. The proxy doesn't cache HTTPS; it only establishes a tunnel and forwards bytes."

**Blocking**

"I'll add a block rule. In the Block list tab I type a host or URL and click Add — for example I can block a host. Then when I try to load that host in the browser, the request appears in the log with status 'Blocked' and the source 'Blocked'. The proxy returns 403 and never contacts the origin."

*[Optionally add a rule and show one blocked request.]*

"That’s the prototype: forwarding, caching for HTTP GET, CONNECT tunnel for HTTPS, and configurable blocking with a visible request log and timing."

---

## 3. Design Choices (≈45 sec)

*[Can stay on console or switch to a short bullet list on screen.]*

"The main design choices are:

- **One thread per client** — each connection is handled in its own thread, so the implementation stays simple and one slow request doesn’t block others.

- **Block list and cache are shared** — all handler threads use the same block list and response cache, so blocking and caching are consistent. Both are implemented in a thread-safe way.

- **HTTPS is only tunnelled** — for CONNECT we don’t look at the content; we just send 200 Connection Established and then copy bytes in both directions. That keeps TLS end-to-end and avoids the complexity and security issues of decrypting HTTPS in the proxy.

- **Only HTTP GET is cached** — we only cache 200 responses for GET, and we respect Cache-Control and Expires. That keeps caching predictable and avoids storing sensitive or non-idempotent responses."

---

## 4. Code Walkthrough (≈1 min 45 sec)

*[IDE with project open; font size large. Go through the following in order; don’t rush.]*

**Entry point and request handling**

"First, the entry point: ProxyServer."

*[Open ProxyServer.java, scroll to main and start.]*

"In main we create a block list, a response cache, and the management console. We start the console on the Swing thread, then start the proxy. The proxy’s start method is an accept loop: for each new client socket we create a ClientHandler and run it in a new thread. So every connection is handled by its own thread."

*[Scroll to ClientHandler.java.]*

**Request parsing and routing**

"ClientHandler’s run method does the core work. We read the request line — method, target, version — and then the headers. From the target we derive host, port, and path. If it’s CONNECT, the target is host colon port. If it’s an absolute URL we strip the scheme and split host and path. If it’s origin form we take the path from the request and the host from the Host header."

*[Point at the CONNECT vs absolute-URL vs origin-form blocks; don’t read every line.]*

**Block, cache, CONNECT, forward**

"Next we check the block list; if it matches we send 403 and report the request as blocked. Then for GET we look up the cache; on a hit we send the cached response and report source Cache. If the method is CONNECT we open a socket to the origin, send 200 Connection Established to the client, and start two threads that copy bytes client-to-origin and origin-to-client — a blind tunnel. Otherwise we forward the request to the origin: we send the request in origin form, read the full response using HttpResponseReader, send it back to the client, and for GET we may store it in the cache if it’s 200 and cacheable."

*[Optionally show runConnectTunnel or fetchFromOrigin briefly.]*

**Cache and block list**

"The cache key is host, pipe, port, pipe, path. ResponseCache stores entries with an expiry time and evicts when over capacity, removing expired entries first then LRU. BlockList stores normalised rules — host or host slash path — and matches with subdomain support so that blocking example.com also blocks www.example.com."

*[Open ResponseCache.java, show cacheKey and maybe get/put or computeExpiry. Open BlockList.java, show isBlocked or hostMatches.]*

**Response parsing**

"When we forward an HTTP request we need to read the origin’s response. HttpResponseReader reads the status line and headers line by line, then the body. It supports Content-Length and Transfer-Encoding chunked, and decodes chunked into a single byte array so we can cache the response and resend it with Content-Length when serving from cache."

*[Open HttpResponseReader.java, show readResponse or the chunked/Content-Length logic briefly.]*

"That’s how the main pieces fit together: parsing, blocking, cache lookup and store, CONNECT tunnel, and forwarding with full response parsing."

---

## 5. Closing (≈15 sec)

*[Back to running proxy or to camera.]*

"So we have a working forward proxy with blocking, caching, and HTTPS tunnelling, a management console for requests and timing, and a clear separation between request handling, cache, and block list. Thanks for watching."

---

## Checklist Before Recording

- [ ] Proxy compiles: `javac *.java`
- [ ] Browser proxy set to localhost:8080 (or your port)
- [ ] IDE font size 16–18pt; theme with good contrast
- [ ] Only one monitor/screen share if possible to avoid clutter
- [ ] Close other apps and notifications
- [ ] Rehearse once and time; cut or shorten if over 5 minutes
- [ ] Export as MP4 or MOV, 1080p if your tool allows

## Approximate Timings

| Section           | Duration   |
|------------------|-----------|
| 1. Introduction  | ~30 s     |
| 2. Demo          | ~1 min 45 s |
| 3. Design choices| ~45 s     |
| 4. Code walkthrough | ~1 min 45 s |
| 5. Closing       | ~15 s     |
| **Total**        | **~5 min** |

Adjust by speaking a bit faster or slower, or by shortening the code section (e.g. only ProxyServer + ClientHandler flow) to stay under 5 minutes.
