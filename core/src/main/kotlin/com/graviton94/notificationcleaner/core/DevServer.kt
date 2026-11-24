package com.graviton94.notificationcleaner.core

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder

/**
 * Desktop stand-in for the Android bridge: serves the game page and answers the same calls from one [WebSession],
 * so the page can be played and screenshotted in a browser. `gradle -p core run --args="serve 8765 ../app/src/main/assets/game"`
 * Query `?seed=` on /api/new restarts with a fixed seed.
 */
fun serve(port: Int, root: File) {
    var session = WebSession(now = 1L)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.createContext("/api/") { ex ->
        val q = (ex.requestURI.rawQuery ?: "").split("&").filter { "=" in it }
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        val now = q["now"]?.toLongOrNull() ?: System.currentTimeMillis()
        val body = when (ex.requestURI.path.removePrefix("/api/")) {
            "new" -> { val seed = q["seed"]?.toLongOrNull() ?: now; session = WebSession(state = Engine().newGame(now, seed), now = now); session.snapshot(now) }
            "snapshot" -> session.snapshot(now)
            "swipe" -> session.swipe(q.getValue("id").toLong(), now)
            "auto" -> session.auto(now)
            "hint" -> session.hint(now)
            "panel" -> session.panel(now)
            "upgradeN" -> session.upgradeN(q["app"] ?: "", q["n"] ?: "1", now)
            "setDeck" -> session.setDeck((q["apps"] ?: "").split(",").filter { it.isNotBlank() }, now)
            "buyBackup" -> session.buyBackup(q.getValue("item"), now)
            // dev only: jump to a level with its run P (and wallet), to try the late panels
            "cheat" -> { val lv = q["level"]?.toInt() ?: 30; val r = session.engine.rules; val p = r.levelCeil(lv, 0)
                session = WebSession(state = session.state.copy(level = lv, runEarned = p, wallet = p * java.math.BigInteger.valueOf((q["wallet"] ?: "3").toLong())), now = now); session.snapshot(now) }
            "buy" -> session.buy(q.getValue("app"), now)
            "upgrade" -> session.upgrade(q.getValue("app"), now)
            "reset" -> session.reset(now)
            "offline" -> session.offline(q.getValue("ms").toLong(), now)
            else -> "{}"
        }.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
    }
    server.createContext("/") { ex ->
        val path = ex.requestURI.path.trimStart('/').ifEmpty { "index.html" }
        val f = File(root, path).canonicalFile
        if (!f.path.startsWith(root.canonicalPath) || !f.isFile) { ex.sendResponseHeaders(404, -1); ex.close(); return@createContext }
        val type = when (f.extension) { "html" -> "text/html; charset=utf-8"; "js" -> "text/javascript"; "json" -> "application/json"; "svg" -> "image/svg+xml"; "png" -> "image/png"; "ogg" -> "audio/ogg"; "css" -> "text/css"; "woff2" -> "font/woff2"; else -> "application/octet-stream" }
        ex.responseHeaders.add("Content-Type", type)
        val bytes = f.readBytes(); ex.sendResponseHeaders(200, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
    }
    server.start()
    println("serving ${root.canonicalPath} on http://127.0.0.1:$port")
}
