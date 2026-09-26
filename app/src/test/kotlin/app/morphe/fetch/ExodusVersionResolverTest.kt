package app.morphe.fetch

import app.morphe.fetch.aurora.ExodusVersionResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ExodusVersionResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun resolveVersionCode_findsMatchingVersion() = runBlocking {
        val sampleJson = """
            {
              "com.google.android.youtube": {
                "reports": [
                  {
                    "version": "19.16.39",
                    "version_code": "1608930000",
                    "creation_date": "2024-04-20T12:00:00Z"
                  },
                  {
                    "version": "19.15.36",
                    "version_code": "1608820000",
                    "creation_date": "2024-04-10T12:00:00Z"
                  }
                ]
              }
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(sampleJson)
        )

        val resolver = ExodusVersionResolver(client, server.url("/api/search/").toString())
        val codeExact = resolver.resolveVersionCode("com.google.android.youtube", "19.16.39")
        assertEquals(1608930000L, codeExact)

        val codeClean = resolver.resolveVersionCode("com.google.android.youtube", "v19.15.36")
        assertEquals(1608820000L, codeClean)

        val codeMissing = resolver.resolveVersionCode("com.google.android.youtube", "18.00.00")
        assertNull(codeMissing)
    }

    @Test
    fun parseExodusReports_extractsValidReportsSortedByVersionCode() {
        val sampleJson = """
            {
              "com.example.app": {
                "reports": [
                  {
                    "version": "1.0.0",
                    "version_code": "100",
                    "creation_date": "2024-01-01"
                  },
                  {
                    "version": "2.0.0",
                    "version_code": "200",
                    "creation_date": "2024-02-01"
                  }
                ]
              }
            }
        """.trimIndent()

        val resolver = ExodusVersionResolver(client)
        val method = ExodusVersionResolver::class.java.getDeclaredMethod(
            "parseExodusReports",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val reports = method.invoke(resolver, sampleJson, "com.example.app") as List<Any>
        assertEquals(2, reports.size)

        val first = reports[0]
        val vCodeField = first.javaClass.getDeclaredField("versionCode").apply { isAccessible = true }
        assertEquals(200L, vCodeField.get(first))
    }
}
