package br.com.nider.biblioteca

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

internal data class Photo(val id: String, val albumId: String, val name: String)
internal data class Album(val id: String, val name: String)
internal data class Doc(val id: String, val name: String, val mime: String)

internal class LibraryStore(private val root: File) {
    private val index = File(root, "index.json")
    private val files = File(root, "files").apply { mkdirs() }

    fun albums(): List<Album> {
        val o = read()
        val arr = o.optJSONArray("albums") ?: JSONArray()
        if (arr.length() == 0) {
            val first = Album(UUID.randomUUID().toString(), "Geral")
            save(o.put("albums", JSONArray().put(JSONObject().put("id", first.id).put("name", first.name))))
            return listOf(first)
        }
        return (0 until arr.length()).map {
            val a = arr.getJSONObject(it)
            Album(a.getString("id"), a.getString("name"))
        }
    }

    fun addAlbum(name: String): Album {
        val album = Album(UUID.randomUUID().toString(), name.trim())
        val o = read()
        o.getJSONArray("albums").put(JSONObject().put("id", album.id).put("name", album.name))
        save(o)
        return album
    }

    fun photos(albumId: String): List<Photo> {
        val arr = read().optJSONArray("photos") ?: JSONArray()
        return (0 until arr.length()).map {
            val p = arr.getJSONObject(it)
            Photo(p.getString("id"), p.getString("albumId"), p.getString("name"))
        }.filter { it.albumId == albumId }
    }

    fun addPhoto(albumId: String, name: String, bytes: ByteArray): Photo {
        val photo = Photo(UUID.randomUUID().toString(), albumId, name)
        File(files, photo.id).writeBytes(bytes)
        val o = read()
        val arr = o.optJSONArray("photos") ?: JSONArray().also { o.put("photos", it) }
        arr.put(JSONObject().put("id", photo.id).put("albumId", albumId).put("name", name))
        save(o)
        return photo
    }

    fun file(id: String): File = File(files, id)

    fun delete(id: String) {
        File(files, id).delete()
        val o = read()
        val arr = o.optJSONArray("photos") ?: return
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            if (p.getString("id") != id) next.put(p)
        }
        o.put("photos", next)
        save(o)
    }

    private fun read(): JSONObject {
        root.mkdirs()
        if (!index.exists()) return JSONObject().put("albums", JSONArray()).put("photos", JSONArray())
        return JSONObject(index.readText())
    }

    private fun save(o: JSONObject) {
        index.writeText(o.toString())
    }
}

internal class OpenSpace(
    val key: SecretKey,
    val dir: File,
    val primary: Boolean,
)

internal class VaultStore(private val root: File) {
    private val config = File(root, "config.json")

    fun ready(): Boolean = config.exists()

    fun create(real: CharArray, decoy: CharArray) {
        require(!real.contentEquals(decoy))
        root.mkdirs()
        val a = newSpace(real, primary = true)
        val b = newSpace(decoy, primary = false)
        real.fill('\u0000')
        decoy.fill('\u0000')
        val spaces = listOf(a, b).shuffled()
        val arr = JSONArray()
        spaces.forEach { (id, salt, check) ->
            arr.put(JSONObject().put("id", id).put("salt", Crypto.b64(salt)).put("check", Crypto.b64(check)))
        }
        config.writeText(JSONObject().put("spaces", arr).toString())
    }

    fun unlock(password: CharArray): OpenSpace? {
        if (!config.exists()) return null
        val spaces = JSONObject(config.readText()).getJSONArray("spaces")
        try {
            for (i in 0 until spaces.length()) {
                val s = spaces.getJSONObject(i)
                val salt = Crypto.b64d(s.getString("salt"))
                val expect = Crypto.b64d(s.getString("check"))
                val key = Crypto.key(password, salt)
                if (!Crypto.same(Crypto.check(key), expect)) continue
                val dir = File(root, s.getString("id"))
                val index = readIndex(key, dir)
                return OpenSpace(key, dir, index.getString("role") == "primary")
            }
            return null
        } finally {
            password.fill('\u0000')
        }
    }

    fun list(open: OpenSpace): List<Doc> {
        val items = readIndex(open.key, open.dir).getJSONArray("items")
        return (0 until items.length()).map {
            val o = items.getJSONObject(it)
            Doc(o.getString("id"), o.getString("name"), o.getString("mime"))
        }
    }

    fun add(open: OpenSpace, name: String, mime: String, bytes: ByteArray): Doc {
        val doc = Doc(UUID.randomUUID().toString(), name, mime.ifBlank { "application/octet-stream" })
        File(open.dir, doc.id).writeBytes(Crypto.encrypt(open.key, bytes))
        val index = readIndex(open.key, open.dir)
        index.getJSONArray("items").put(
            JSONObject().put("id", doc.id).put("name", doc.name).put("mime", doc.mime),
        )
        writeIndex(open.key, open.dir, index)
        return doc
    }

    fun read(open: OpenSpace, id: String): ByteArray =
        Crypto.decrypt(open.key, File(open.dir, id).readBytes())

    fun delete(open: OpenSpace, id: String) {
        File(open.dir, id).delete()
        val index = readIndex(open.key, open.dir)
        val items = index.getJSONArray("items")
        val next = JSONArray()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            if (o.getString("id") != id) next.put(o)
        }
        index.put("items", next)
        writeIndex(open.key, open.dir, index)
    }

    private fun newSpace(password: CharArray, primary: Boolean): Triple<String, ByteArray, ByteArray> {
        val id = UUID.randomUUID().toString()
        val salt = Crypto.salt()
        val key = Crypto.key(password, salt)
        val dir = File(root, id).apply { mkdirs() }
        writeIndex(
            key,
            dir,
            JSONObject().put("role", if (primary) "primary" else "reserve").put("items", JSONArray()),
        )
        return Triple(id, salt, Crypto.check(key))
    }

    private fun readIndex(key: SecretKey, dir: File): JSONObject =
        JSONObject(Crypto.decrypt(key, File(dir, "index.bin").readBytes()).decodeToString())

    private fun writeIndex(key: SecretKey, dir: File, index: JSONObject) {
        File(dir, "index.bin").writeBytes(Crypto.encrypt(key, index.toString().encodeToByteArray()))
    }

    companion object {
        fun library(context: Context) = LibraryStore(File(context.filesDir, "library"))
        fun vault(context: Context) = VaultStore(File(context.filesDir, "forte"))
    }
}
