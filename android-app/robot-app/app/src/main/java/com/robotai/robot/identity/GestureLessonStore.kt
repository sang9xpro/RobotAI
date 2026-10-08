package com.robotai.robot.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.robotai.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GestureModelRecord(val model:GestureLinearModel, val report:JSONObject, val importedAt:Long)
data class GestureLessonBook(val data:GestureLessonData=GestureLessonData(),
    val models:List<GestureModelRecord> = emptyList(), val active:Map<String,String> = emptyMap(),
    val previous:Map<String,String> = emptyMap())

/** Separate, account-scoped encrypted store. Export is an explicit user action through SAF. */
class GestureLessonStore(context:Context,scope:String) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"gestures-$scope.bin"))
    private val alias="robotai-gestures-$scope"
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read():GestureLessonBook {
        if(!file.baseFile.exists()) return GestureLessonBook()
        require(file.baseFile.length()<=16_000_000) { "Kho cử chỉ quá lớn" }
        val bytes=file.readFully();require(bytes.size>=29)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        val root=JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
        require(root.getInt("version")==1)
        val data=GestureLessonJson.decodeData(root.getJSONObject("data"))
        val models=root.getJSONArray("models").objects().map { GestureModelRecord(
            GestureLessonJson.decodeModel(it.getJSONObject("model")),it.getJSONObject("report"),it.getLong("importedAt")) }
        fun map(name:String)=root.getJSONObject(name).let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
        return GestureLessonBook(data,models,map("active"),map("previous"))
    }
    @Synchronized fun write(book:GestureLessonBook) {
        require(book.data.definitions.size<=600 && book.data.examples.size<=4000) { "Kho mẫu đầy; hãy xuất và xóa bớt" }
        val root=JSONObject().put("version",1).put("data",GestureLessonJson.encodeData(book.data))
            .put("models",JSONArray(book.models.map { JSONObject().put("model",GestureLessonJson.encodeModel(it.model))
                .put("report",it.report).put("importedAt",it.importedAt) })).put("active",JSONObject(book.active)).put("previous",JSONObject(book.previous))
        val plain=root.toString().toByteArray(Charsets.UTF_8);require(plain.size<=16_000_000) { "Kho mẫu đầy; hãy xuất và xóa bớt" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val out=file.startWrite()
        try { out.write(cipher.iv+cipher.doFinal(plain));file.finishWrite(out) } catch(e:Exception) { file.failWrite(out);throw e }
    }
}

internal fun JSONArray.objects()=(0 until length()).map { getJSONObject(it) }
object GestureLessonJson {
    fun encodeData(data:GestureLessonData)=JSONObject().put("version",1).put("featureVersion",GestureFeatures.VERSION)
        .put("revision",data.revision).put("definitions",JSONArray(data.definitions.map { d -> JSONObject()
            .put("id",d.id).put("personId",d.personId).put("name",d.name).put("action",d.action?.name ?: "none") }))
        .put("examples",JSONArray(data.examples.map { e -> JSONObject().put("id",e.id).put("gestureId",e.gestureId)
            .put("sessionId",e.sessionId).put("createdAt",e.createdAt).put("accepted",e.accepted).put("feedback",e.feedback)
            .put("frames",JSONArray(e.frames.map { f -> JSONObject().put("side",f.side).put("aspect",f.aspect)
                .put("elapsedMs",f.elapsedMs).put("points",JSONArray(f.points.map { JSONArray(listOf(it.x,it.y,it.z)) })) })) }))
    fun decodeData(root:JSONObject):GestureLessonData {
        require(root.getInt("version")==1 && root.getString("featureVersion")==GestureFeatures.VERSION)
        val definitions=root.getJSONArray("definitions").objects().map { d -> TaughtGesture(d.getString("id"),
            d.getString("personId"),d.getString("name"),d.getString("action").let { if(it=="none") null else SocialGesture.valueOf(it) }) }
        val examples=root.getJSONArray("examples").objects().map { e -> GestureExample(e.getString("id"),e.getString("gestureId"),
            e.getString("sessionId"),e.getJSONArray("frames").objects().map { f ->
                val points=f.getJSONArray("points")
                GestureFrame((0 until points.length()).map { i -> points.getJSONArray(i).let { HandPoint(it.getDouble(0).toFloat(),it.getDouble(1).toFloat(),it.getDouble(2).toFloat()) } },
                    f.getString("side"),f.getDouble("aspect").toFloat(),f.getLong("elapsedMs")) },
            e.getLong("createdAt"),e.getBoolean("accepted"),e.getString("feedback")) }
        require(definitions.size<=600 && examples.size<=4000 && definitions.map { it.id }.distinct().size==definitions.size)
        require(examples.map { it.id }.distinct().size==examples.size)
        require(definitions.all { it.id.length in 1..100 && it.personId.length in 1..100 && it.name.length in 1..60 })
        require(examples.all { e -> e.gestureId in definitions.map { it.id } && e.sessionId.length in 1..100 &&
            e.frames.size in 3..12 && e.frames.all { GestureFeatures.extract(it.hand())!=null } &&
            e.frames.zipWithNext().all { (a,b) -> b.elapsedMs>a.elapsedMs } })
        return GestureLessonData(root.getInt("revision"),definitions,examples)
    }
    fun encodeModel(m:GestureLinearModel)=JSONObject().put("id",m.id).put("personId",m.personId)
        .put("featureVersion",m.featureVersion).put("labels",JSONArray(m.labels)).put("weights",JSONArray(m.weights.map { JSONArray(it.toList()) }))
        .put("bias",JSONArray(m.bias.toList())).put("threshold",m.threshold).put("margin",m.margin).put("radius",m.radius)
        .put("anchors",JSONArray(m.anchors.map { examples -> JSONArray(examples.map { JSONArray(it.toList()) }) }))
    fun decodeModel(root:JSONObject):GestureLinearModel {
        fun floats(a:JSONArray)=FloatArray(a.length()) { a.getDouble(it).toFloat() }
        val labels=root.getJSONArray("labels");val weights=root.getJSONArray("weights")
        require(labels.length() in 2..40 && weights.length()==labels.length())
        val anchors=root.getJSONArray("anchors")
        return GestureLinearModel(root.getString("id"),root.getString("personId"),
            (0 until labels.length()).map { labels.getString(it) },(0 until weights.length()).map { floats(weights.getJSONArray(it)) },
            floats(root.getJSONArray("bias")),root.getDouble("threshold").toFloat(),root.getDouble("margin").toFloat(),
            root.getDouble("radius").toFloat(),root.getString("featureVersion"),
            (0 until anchors.length()).map { i -> anchors.getJSONArray(i).let { examples -> (0 until examples.length()).map { floats(examples.getJSONArray(it)) } } }).also { it.validate() }
    }
}
