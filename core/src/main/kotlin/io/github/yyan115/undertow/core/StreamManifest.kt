package io.github.yyan115.undertow.core

import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI

object StreamManifest {
    fun parse(response: JSONObject): StreamInfo {
        if (response.optString("assetPresentation", "FULL") != "FULL") {
            throw ServiceException(403, "TIDAL returned a preview instead of a full track. Check this client's account access.")
        }
        val bytes = try { Base64.getDecoder().decode(response.getString("manifest")) }
        catch (_: IllegalArgumentException) { throw ServiceException(502, "The stream manifest is invalid.") }
        val manifest = bytes.toString(Charsets.UTF_8)
        if (manifest.length > 2_000_000 || manifest.contains("<!DOCTYPE", ignoreCase = true) || manifest.contains("<!ENTITY", ignoreCase = true)) {
            throw ServiceException(502, "The stream manifest contains unsupported XML declarations.")
        }
        val quality = response.optString("audioQuality")
        val bitDepth = response.optInt("bitDepth")
        val sampleRate = response.optInt("sampleRate")
        return when (response.getString("manifestMimeType")) {
            "application/vnd.tidal.bts" -> {
                val data = JSONObject(manifest)
                if (data.optString("encryptionType", "NONE") != "NONE" || data.optString("keyId").isNotBlank()) {
                    throw ProtectedStreamException()
                }
                val urls = data.optJSONArray("urls")
                val url = urls?.optString(0)?.takeIf(String::isNotBlank)
                    ?: throw ServiceException(502, "No audio URL was returned.")
                requireHttps(url)
                StreamInfo(uri = url, mimeType = data.optString("mimeType", "audio/mp4"),
                    codec = data.optString("codecs", "unknown"), quality = quality,
                    bitDepth = bitDepth, sampleRate = sampleRate)
            }
            "application/dash+xml" -> {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                }
                val builder = factory.newDocumentBuilder().apply {
                    setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External XML entities are not supported.") }
                }
                val document = builder.parse(InputSource(StringReader(manifest)))
                if (document.getElementsByTagNameNS("*", "ContentProtection").length > 0) throw ProtectedStreamException()
                val nodes = document.getElementsByTagNameNS("*", "Representation")
                val representation = nodes.item(0) as? org.w3c.dom.Element
                val adaptations = document.getElementsByTagNameNS("*", "AdaptationSet")
                val adaptation = adaptations.item(0) as? org.w3c.dom.Element
                val codec = representation?.getAttribute("codecs").orEmpty().ifBlank { adaptation?.getAttribute("codecs").orEmpty() }
                StreamInfo(dashManifest = manifest, mimeType = "application/dash+xml", codec = codec.ifBlank { "unknown" },
                    quality = quality, bitDepth = bitDepth,
                    sampleRate = sampleRate.takeIf { it > 0 } ?: representation?.getAttribute("audioSamplingRate")?.toIntOrNull() ?: 0)
            }
            else -> throw ServiceException(502, "This stream format is not supported.")
        }
    }

    fun requireHttps(value: String) {
        val uri = URI(value)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
            throw ServiceException(502, "The service returned an unsafe audio URL.")
        }
    }
}
