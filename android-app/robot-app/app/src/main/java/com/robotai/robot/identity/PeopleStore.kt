package com.robotai.robot.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Person(val id: String, val name: String, val address: String,
    val faces: List<FloatArray> = emptyList(), val voices: List<FloatArray> = emptyList(),
    val learnedFaces: List<FloatArray> = emptyList(), val previousLearnedFaces: List<FloatArray> = emptyList(),
    val learningRevision: Int = 0, val learnedAt: Long = 0, val canUndoLearning: Boolean = false)

/** Account-scoped, authenticated encryption; templates never go to the LLM or backup. */
class PeopleStore(context: Context, scope: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "people-$scope.bin"))
    private val alias = "robotai-people-$scope"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): List<Person> {
        if (!file.baseFile.exists()) return emptyList()
        val bytes = file.readFully()
        require(bytes.size >= 29) { "Dữ liệu người quen bị hỏng" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val root = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        require(root.getInt("version") in 1..2 && root.getString("faceModel") == FACE_MODEL)
        val people = root.getJSONArray("people")
        return (0 until people.length()).map { i -> people.getJSONObject(i).let {
            Person(it.getString("id"), it.getString("name"), it.getString("address"), vectors(it.getJSONArray("faces")), vectors(it.getJSONArray("voices")),
                vectors(it.optJSONArray("learnedFaces") ?: JSONArray()), vectors(it.optJSONArray("previousLearnedFaces") ?: JSONArray()),
                it.optInt("learningRevision",0),it.optLong("learnedAt",0),it.optBoolean("canUndoLearning",false))
        } }
    }
    @Synchronized fun write(people: List<Person>) {
        require(people.size <= 30)
        val array = JSONArray()
        people.forEach { p -> array.put(JSONObject().put("id", p.id).put("name", p.name).put("address", p.address)
            .put("faces", encode(p.faces)).put("voices", encode(p.voices))
            .put("learnedFaces",encode(p.learnedFaces.takeLast(24))).put("previousLearnedFaces",encode(p.previousLearnedFaces.takeLast(24)))
            .put("learningRevision",p.learningRevision).put("learnedAt",p.learnedAt).put("canUndoLearning",p.canUndoLearning)) }
        val plain = JSONObject().put("version", 2).put("faceModel", FACE_MODEL).put("people", array).toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(plain)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    companion object {
        const val FACE_MODEL = "sface-2021dec-rgb112-v1"
        fun create(name: String, address: String) = Person(UUID.randomUUID().toString(), name.trim().take(60), address.trim().take(40))
        private fun vectors(a: JSONArray) = (0 until a.length()).map { i -> a.getJSONArray(i).let { v -> FloatArray(v.length()) { v.getDouble(it).toFloat() } } }
        private fun encode(v: List<FloatArray>) = JSONArray().apply { v.forEach { put(JSONArray(it.toList())) } }
    }
}
