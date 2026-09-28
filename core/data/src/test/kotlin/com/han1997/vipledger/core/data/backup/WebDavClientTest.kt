package com.han1997.vipledger.core.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.runner.RunWith

/**
 * WebDAV 客户端契约（`.trellis/spec/android/import-export.md`）：
 * 每个请求都带 Basic 认证；MKCOL 建目录、PUT 上传、GET 下载、DELETE 删除、PROPFIND 列目录；
 * 非 2xx/207 一律冒泡为失败；`latest.json` 只在快照上传成功后才更新。
 *
 * 走 Robolectric 是因为 `upload` 用 `org.json` 组装 `latest.json`，纯 JVM 单测里它只是 stub。
 */
@RunWith(AndroidJUnit4::class)
class WebDavClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient

    private val snapshotName = "vipledger-backup-20260928-101500.zip"

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = WebDavClient(
            endpoint = server.url("/dav").toString(),
            rootPath = "vipledger",
            username = "alice",
            password = "s3cret",
            client = OkHttpClient(),
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun uploadCreatesDirectoryUploadsSnapshotThenUpdatesLatestPointer() {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(204))

        client.upload(tempFile(), snapshotName)

        val mkcol = server.takeRequest()
        assertEquals("MKCOL", mkcol.method)
        assertEquals("/dav/vipledger/", mkcol.path)
        val putSnapshot = server.takeRequest()
        assertEquals("PUT", putSnapshot.method)
        assertEquals("/dav/vipledger/$snapshotName", putSnapshot.path)
        val putLatest = server.takeRequest()
        assertEquals("PUT", putLatest.method)
        assertEquals("/dav/vipledger/latest.json", putLatest.path)
    }

    @Test
    fun everyRequestCarriesBasicAuth() {
        server.enqueue(MockResponse().setResponseCode(207).setBody(PROPFIND_BODY))
        server.enqueue(MockResponse().setResponseCode(204))

        client.check()
        client.delete(snapshotName)

        val expected = Credentials.basic("alice", "s3cret", Charsets.UTF_8)
        repeat(2) {
            val request = server.takeRequest()
            assertEquals(expected, request.getHeader("Authorization"))
        }
    }

    @Test
    fun uploadFailureSurfacesAndLeavesLatestPointerUntouched() {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(500))

        assertFailsWith<IOException> { client.upload(tempFile(), snapshotName) }

        assertEquals("MKCOL", server.takeRequest().method)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals(2, server.requestCount, "快照上传失败后不应再 PUT latest.json")
    }

    @Test
    fun listParsesOnlySnapshotEntriesWithMetadata() {
        server.enqueue(MockResponse().setResponseCode(207).setBody(PROPFIND_BODY))

        val snapshots = client.list()

        assertEquals(listOf(snapshotName, "vipledger-backup-20260927-090000.zip"), snapshots.map { it.name })
        assertEquals(20480L, snapshots.first().size)
        assertEquals("1", processDepth(server.takeRequest()))
    }

    @Test
    fun downloadStreamsResponseBodyToFile() {
        val payload = "snapshot-bytes".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(payload)))

        val destination = File.createTempFile("download", ".zip")
        try {
            client.download(snapshotName, destination)
            assertTrue(destination.readBytes().contentEquals(payload))
            assertEquals("GET", server.takeRequest().method)
        } finally {
            destination.delete()
        }
    }

    @Test
    fun nonSuccessListStatusSurfacesAsFailure() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertFailsWith<IOException> { client.list() }
    }

    @Test
    fun downloadRejectsNamesOutsideTheSnapshotPattern() {
        assertFailsWith<IllegalArgumentException> { client.download("../evil.zip", File("unused")) }
    }

    @Test
    fun retainKeepsTheNewestSnapshotsAndDeletesOnlyTheOlderOnes() {
        server.enqueue(MockResponse().setResponseCode(207).setBody(PROPFIND_BODY))
        server.enqueue(MockResponse().setResponseCode(204))

        client.retain(1)

        assertEquals("PROPFIND", server.takeRequest().method)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/dav/vipledger/vipledger-backup-20260927-090000.zip", delete.path)
        assertEquals(2, server.requestCount, "刚上传（最新）的那份不得被保留策略删除")
    }

    @Test
    fun checkReturnsFalseInsteadOfThrowingOnTransportError() {
        server.enqueue(MockResponse().setResponseCode(207).setBody(PROPFIND_BODY))
        server.enqueue(MockResponse().setResponseCode(401))

        assertTrue(client.check())
        assertFalse(client.check())
    }

    private fun tempFile(): File {
        val file = File.createTempFile("vipledger", ".zip")
        file.writeText("payload")
        return file
    }

    private fun processDepth(request: RecordedRequest): String = request.getHeader("Depth").orEmpty()

    private companion object {
        val PROPFIND_BODY = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/vipledger/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/vipledger/vipledger-backup-20260927-090000.zip</d:href>
                <d:propstat><d:prop>
                  <d:getlastmodified>Sun, 27 Sep 2026 09:00:00 GMT</d:getlastmodified>
                  <d:getcontentlength>10240</d:getcontentlength>
                </d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/vipledger/vipledger-backup-20260928-101500.zip</d:href>
                <d:propstat><d:prop>
                  <d:getlastmodified>Mon, 28 Sep 2026 10:15:00 GMT</d:getlastmodified>
                  <d:getcontentlength>20480</d:getcontentlength>
                </d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/vipledger/latest.json</d:href>
                <d:propstat><d:prop><d:getcontentlength>120</d:getcontentlength></d:prop></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }
}
