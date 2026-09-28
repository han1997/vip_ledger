package com.han1997.vipledger.core.data.backup

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

data class RemoteSnapshot(val name: String, val modifiedAt: String?, val size: Long?)

/**
 * 极简 WebDAV 客户端：只用 OkHttp 手写 `PROPFIND` / `MKCOL` / `PUT` / `GET` / `DELETE`，
 * 每个请求都带 Basic 认证（`.trellis/spec/android/import-export.md` WebDAV 一节）。
 *
 * 上传/下载都走文件流，不把整份快照读进内存；`latest.json` 只在快照 PUT 成功后更新。
 */
class WebDavClient(
    private val endpoint: String,
    private val rootPath: String,
    private val username: String,
    private val password: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).build(),
) {
    private val directory = endpoint.trimEnd('/') + "/" + rootPath.trim('/') + "/"
    private val auth = Credentials.basic(username, password, Charsets.UTF_8)

    /** 连通性探测：目录不可达/鉴权失败都返回 false，不抛异常给界面。 */
    fun check(): Boolean = runCatching {
        execute(directory, "PROPFIND", PROPFIND_RESOURCE_TYPE).use { it.code == 207 || it.isSuccessful }
    }.getOrDefault(false)

    fun upload(file: File, name: String) {
        ensureDirectory()
        // 流式请求体：大文件不整份进内存。
        val request = Request.Builder().url(url(name)).header("Authorization", auth)
            .put(file.asRequestBody(ZIP_MIME)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("WebDAV upload failed (${response.code})")
        }
        // 快照上传成功后才更新指针；失败时直接抛出，latest.json 保持原值。
        val latest = JSONObject().put("file", name).put("size", file.length())
            .put("sha256", file.sha256()).put("formatVersion", SnapshotArchive.FORMAT_VERSION)
        execute(url("latest.json"), "PUT", latest.toString(), JSON_MIME).close()
    }

    fun list(): List<RemoteSnapshot> {
        val response = execute(directory, "PROPFIND", PROPFIND_LIST, depth = "1")
        val body = response.use { it.body?.string().orEmpty() }
        return Regex("(?is)<(?:[\\w-]+:)?response[^>]*>(.*?)</(?:[\\w-]+:)?response>").findAll(body)
            .mapNotNull { match ->
                val part = match.groupValues[1]
                val href = Regex("(?is)<(?:[\\w-]+:)?href[^>]*>(.*?)</(?:[\\w-]+:)?href>").find(part)?.groupValues?.get(1)
                    ?.replace("&amp;", "&")?.substringAfterLast('/') ?: return@mapNotNull null
                if (!href.startsWith("vipledger-backup-") || !href.endsWith(".zip")) return@mapNotNull null
                val modified = Regex("(?is)<(?:[\\w-]+:)?getlastmodified[^>]*>(.*?)</(?:[\\w-]+:)?getlastmodified>")
                    .find(part)?.groupValues?.get(1)
                val size = Regex("(?is)<(?:[\\w-]+:)?getcontentlength[^>]*>(.*?)</(?:[\\w-]+:)?getcontentlength>")
                    .find(part)?.groupValues?.get(1)?.toLongOrNull()
                RemoteSnapshot(href, modified, size)
            }.sortedByDescending { it.name }
            .toList()
    }

    fun download(name: String, destination: File) {
        require(name.matches(SNAPSHOT_NAME))
        val request = Request.Builder().url(url(name)).header("Authorization", auth).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("WebDAV download failed (${response.code})")
            destination.outputStream().use { sink ->
                response.body?.byteStream()?.copyTo(sink) ?: throw IOException("WebDAV download empty body")
            }
        }
    }

    fun delete(name: String) {
        require(name.matches(SNAPSHOT_NAME))
        execute(url(name), "DELETE").close()
    }

    fun retain(keep: Int) {
        require(keep >= 1)
        list().drop(keep).forEach { snapshot -> runCatching { delete(snapshot.name) } }
    }

    private fun ensureDirectory() {
        var path = endpoint.trimEnd('/')
        rootPath.trim('/').split('/').filter(String::isNotBlank).forEach { segment ->
            path += "/" + segment
            val code = execute(path + "/", "MKCOL").use { it.code }
            if (code !in setOf(201, 405) && code !in 200..299) {
                throw IOException("WebDAV directory creation failed ($code)")
            }
        }
    }

    private fun execute(
        url: String,
        method: String,
        body: String? = null,
        mediaType: String = "application/xml",
        depth: String? = null,
    ): Response {
        val builder = Request.Builder().url(url).header("Authorization", auth)
        if (depth != null) builder.header("Depth", depth)
        val requestBody = body?.toRequestBody(mediaType.toMediaType())
        builder.method(
            method,
            when (method) {
                "PROPFIND", "PUT", "POST" -> requestBody ?: ByteArray(0).toRequestBody(null)
                else -> null
            },
        )
        val response = client.newCall(builder.build()).execute()
        if (!response.isSuccessful && response.code != 207 && !(method == "MKCOL" && response.code == 405)) {
            val code = response.code
            response.close()
            throw IOException("WebDAV $method failed ($code)")
        }
        return response
    }

    private fun url(name: String): String = directory + name

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val ZIP_MIME = "application/zip".toMediaType()
        private const val JSON_MIME = "application/json"
        private val SNAPSHOT_NAME = Regex("vipledger-backup-[0-9]{8}-[0-9]{6}\\.zip")
        private val PROPFIND_RESOURCE_TYPE =
            """<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        private val PROPFIND_LIST =
            """<d:propfind xmlns:d="DAV:"><d:prop><d:getlastmodified/><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>"""
    }
}
