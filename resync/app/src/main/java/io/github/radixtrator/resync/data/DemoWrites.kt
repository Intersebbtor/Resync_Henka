package io.github.radixtrator.resync.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Fake responses for write endpoints in demo mode, shaped like Reddit's, so the UI flows run end to end. */
internal object DemoWrites {
    private var counter = 0

    fun respond(path: String, form: Map<String, String>): JsonElement = when (path) {
        "/api/comment", "/api/editusertext" -> comment(form["thing_id"].orEmpty(), form["text"].orEmpty(), isEdit = path.endsWith("editusertext"))
        "/api/submit" -> buildJsonObject {
            putJsonObject("json") {
                putJsonArray("errors") {}
                putJsonObject("data") {
                    put("id", "dm0007")
                    put("name", "t3_dm0007")
                }
            }
        }
        else -> JsonObject(emptyMap())
    }

    private fun comment(thingId: String, text: String, isEdit: Boolean): JsonElement {
        val id = if (isEdit) thingId.removePrefix("t1_") else "demo${++counter}"
        return buildJsonObject {
            putJsonObject("json") {
                putJsonArray("errors") {}
                putJsonObject("data") {
                    put("things", buildJsonArray {
                        add(buildJsonObject {
                            put("kind", "t1")
                            putJsonObject("data") {
                                put("id", id)
                                put("name", "t1_$id")
                                put("parent_id", if (isEdit) "" else thingId)
                                put("author", "demo_user")
                                put("body", text)
                                put("body_html", "<div class=\"md\">${toHtml(text)}</div>")
                                put("score", 1)
                                put("likes", true)
                                put("created_utc", System.currentTimeMillis() / 1000)
                                put("edited", isEdit)
                            }
                        })
                    })
                }
            }
        }
    }

    private fun toHtml(markdown: String) = markdown.split(Regex("\n{2,}")).joinToString("") { para ->
        "<p>" + para.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>"
    }
}
