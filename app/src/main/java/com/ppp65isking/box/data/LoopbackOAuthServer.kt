package com.ppp65isking.box.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder

/**
 * One-shot loopback HTTP server for Google sign-in.
 *
 * Google's Desktop OAuth clients have no place to register a redirect
 * URI — their console page has no such field — so Box cannot use a
 * custom scheme for Google at all. What Desktop clients DO accept,
 * with no registration, is a loopback redirect: Google sends the
 * browser to http://127.0.0.1:<port>/ and this server, listening on
 * that port, catches the ?code=…&state=… and hands it to [onResult].
 *
 * The browser shows the little "signed in" page this server returns;
 * the user just presses back to Box. The server stops itself after
 * the first real result, or after [TIMEOUT_MS].
 *
 * [bind] is quick and returns the redirect URI; [serve] blocks on
 * the accept loop, so callers run it on Dispatchers.IO.
 */
class LoopbackOAuthServer(
    private val onResult: (code: String?, state: String?, error: String?) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var resultDelivered = false

    /** The redirect URI Google must send the browser to — null until [bind]. */
    var redirectUri: String? = null
        private set

    /** Open the socket on a free loopback port. Fast, non-blocking. */
    fun bind(): String {
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        val uri = "http://127.0.0.1:${socket.localPort}/"
        redirectUri = uri
        return uri
    }

    /** Accept connections until the login result arrives or we time out.
     *  Returns true when a result was delivered. */
    fun serve(): Boolean {
        val socket = serverSocket ?: return false
        socket.soTimeout = 1_000
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        try {
            while (System.currentTimeMillis() < deadline) {
                val client = try {
                    socket.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                client.use { if (handle(it)) break }
            }
        } finally {
            stop()
        }
        return resultDelivered
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    /** Returns true when this request carried the login result. */
    private fun handle(client: Socket): Boolean {
        client.soTimeout = 5_000
        val reader = BufferedReader(
            InputStreamReader(client.getInputStream(), Charsets.US_ASCII),
        )
        val requestLine = reader.readLine() ?: return false
        // Drain the headers.
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        val path = requestLine.split(" ").getOrNull(1) ?: return false
        val params = parseQuery(path.substringAfter("?", ""))
        val code = params["code"]
        val error = params["error"]
        val state = params["state"]
        val done = code != null || error != null
        val body = when {
            error != null ->
                "<h1>Sign-in cancelled</h1>" +
                    "<p>You can close this tab and go back to Box.</p>"
            code != null ->
                "<h1>Signed in to Box</h1>" +
                    "<p>You can close this tab and go back to Box.</p>"
            else -> null
        }
        val out = client.getOutputStream()
        if (body != null) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            out.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n")
                    .toByteArray(Charsets.US_ASCII),
            )
            out.write(bytes)
        } else {
            out.write(
                "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
                    .toByteArray(Charsets.US_ASCII),
            )
        }
        out.flush()
        if (done) {
            resultDelivered = true
            onResult(code, state, error)
        }
        return done
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").mapNotNull { part ->
            val i = part.indexOf("=")
            if (i < 0) {
                null
            } else {
                URLDecoder.decode(part.substring(0, i), "UTF-8") to
                    URLDecoder.decode(part.substring(i + 1), "UTF-8")
            }
        }.toMap()

    companion object {
        private const val TIMEOUT_MS = 5 * 60 * 1_000L
    }
}
