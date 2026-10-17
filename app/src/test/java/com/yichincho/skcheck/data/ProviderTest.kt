package com.yichincho.skcheck.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderTest {
    @Test fun includesLatestGlmVisionAndNvidiaButExcludesTextGlm() {
        val top = Nvidia.topVision(listOf("z-ai/glm-5.3", "z-ai/glm-5.3-flash",
            "nvidia/nemotron-3.5-super-vl-120b-a12b", "z-ai/glm-4.6v",
            "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning", "nvidia/llama-nemotron-embed-vl-1b-v2"))
        assertEquals("nvidia/nemotron-3.5-super-vl-120b-a12b", top.first())
        assertTrue("z-ai/glm-5.3-flash" in top)
        assertTrue("z-ai/glm-4.6v" in top)
        assertTrue(top.any { "nano-omni" in it })
        assertFalse("z-ai/glm-5.3" in top)
        assertFalse(top.any { "embed" in it })
    }

    @Test fun deepseekUsesImageMetadataRatherThanName() {
        val body = """{"data":[{"id":"deepseek-flash","input_modalities":["text","image"]},
            {"id":"deepseek-v4-pro","input_modalities":["text"]},
            {"id":"deepseek-future","input_modalities":["image"]}]}"""
        assertEquals(listOf("deepseek-flash", "deepseek-future"), DeepSeek.visionModels(body))
        assertTrue(DeepSeek.visionModels("""{"data":[{"id":"deepseek-flash","input_modalities":["text"]}]}""").isEmpty())
    }

    @Test fun verifyProbesImagesAndKeepsWorkingModelFirstAfterModelForbidden() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"z-ai/glm-5.3-flash"},{"id":"meta/llama-4-maverick-17b-128e-instruct"}]}"""))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"model not enabled"}}"""))
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"OK"}}]}"""))
            val models = Nvidia.verify(" Bearer test-key ", server.url("/v1").toString())
            assertEquals("meta/llama-4-maverick-17b-128e-instruct", models.first())
            assertEquals("Bearer test-key", server.takeRequest().getHeader("Authorization"))
            val request = JSONObject(server.takeRequest().body.readUtf8())
            val content = request.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
            assertEquals("image_url", content.getJSONObject(1).getString("type"))
            assertTrue(request.getInt("max_tokens") >= 256)
            assertEquals("/v1/chat/completions", server.takeRequest().path)
        }
    }

    @Test fun verifyDoesNotAcceptAnEmptyReasoningOnlyReply() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"z-ai/glm-5.3-flash"}]}"""))
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"","reasoning_content":"thinking"}}]}"""))
            try {
                Nvidia.verify("test-key", server.url("/v1").toString())
                fail("An empty answer must not mark verification successful")
            } catch (e: NetException) { assertTrue(e.message!!.contains("沒有")) }
        }
    }

    @Test fun invalidCredentialsStopWithoutTryingEveryModel() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"z-ai/glm-5.3-flash"},{"id":"z-ai/glm-4.6v"}]}"""))
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"unauthorized"}}"""))
            try { Nvidia.verify("bad-key", server.url("/v1").toString()); fail("Expected 401") }
            catch (e: NetException) { assertEquals(401, e.httpCode) }
            assertEquals(2, server.requestCount)
        }
    }
}
