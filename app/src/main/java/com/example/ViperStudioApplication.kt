package com.example

import android.app.Application
import com.example.data.local.ViperStudioDatabase
import com.example.data.local.entity.SceneEntity
import com.example.data.model.LayerType
import com.example.data.model.SceneLayer
import com.example.engine.audio.AudioMixerEngine
import com.example.engine.recording.ScreenRecorderManager
import com.example.engine.streaming.LocalMirrorServer
import com.example.engine.streaming.RtmpStreamEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.data.local.AppPreferencesRepository
import org.json.JSONArray
import org.json.JSONObject

class ViperStudioApplication : Application() {

    val database by lazy { ViperStudioDatabase.getDatabase(this) }
    val preferencesRepository by lazy { AppPreferencesRepository(this) }
    val audioMixerEngine by lazy { AudioMixerEngine(this) }
    val rtmpStreamEngine by lazy { RtmpStreamEngine(this) }
    val localMirrorServer by lazy { LocalMirrorServer(this) }
    val screenRecorderManager by lazy { ScreenRecorderManager(this, database) }

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        seedDefaultScenesIfEmpty()
    }

    private fun seedDefaultScenesIfEmpty() {
        applicationScope.launch {
            if (database.sceneDao().getCount() == 0) {
                // Preset 1: Pro Gameplay + Facecam
                val scene1Layers = listOf(
                    SceneLayer(
                        id = "layer_game",
                        type = LayerType.GAME_SCREEN,
                        label = "BGMI Game Capture",
                        xPct = 0f,
                        yPct = 0f,
                        widthPct = 100f,
                        heightPct = 100f,
                        zIndex = 0
                    ),
                    SceneLayer(
                        id = "layer_facecam",
                        type = LayerType.CAMERA_PIP,
                        label = "Facecam PiP",
                        xPct = 72f,
                        yPct = 8f,
                        widthPct = 25f,
                        heightPct = 30f,
                        zIndex = 10,
                        pipShape = "ROUNDED",
                        bgColorHex = "#FF00E676"
                    ),
                    SceneLayer(
                        id = "layer_alert",
                        type = LayerType.ALERT_BOX,
                        label = "Alert Box",
                        xPct = 25f,
                        yPct = 12f,
                        widthPct = 50f,
                        heightPct = 20f,
                        zIndex = 15
                    ),
                    SceneLayer(
                        id = "layer_banner",
                        type = LayerType.TEXT_BANNER,
                        label = "Stream Title Ticker",
                        xPct = 0f,
                        yPct = 92f,
                        widthPct = 100f,
                        heightPct = 8f,
                        zIndex = 20,
                        textContent = "VIPER STUDIO • BGMI TOURNAMENT LIVE • DROP A LIKE & SUBSCRIBE!",
                        textColorHex = "#00E676",
                        bgColorHex = "#D90A0E17",
                        isMarqueeScroll = true
                    ),
                    SceneLayer(
                        id = "layer_social",
                        type = LayerType.SOCIAL_BADGE,
                        label = "Social Handle",
                        xPct = 3f,
                        yPct = 6f,
                        widthPct = 30f,
                        heightPct = 8f,
                        zIndex = 12,
                        textContent = "@ViperGamerLive • Discord /viper",
                        textColorHex = "#00E5FF",
                        bgColorHex = "#B3111722"
                    )
                )

                // Preset 2: Starting Soon
                val scene2Layers = listOf(
                    SceneLayer(
                        id = "layer_start_bg",
                        type = LayerType.IMAGE_OVERLAY,
                        label = "Background Card",
                        xPct = 0f,
                        yPct = 0f,
                        widthPct = 100f,
                        heightPct = 100f,
                        zIndex = 0,
                        bgColorHex = "#FF0A0E17"
                    ),
                    SceneLayer(
                        id = "layer_start_txt",
                        type = LayerType.TEXT_BANNER,
                        label = "Title",
                        xPct = 15f,
                        yPct = 40f,
                        widthPct = 70f,
                        heightPct = 20f,
                        zIndex = 10,
                        textContent = "STREAM STARTING SOON\nWARM UP IN PROGRESS",
                        textColorHex = "#00E676",
                        bgColorHex = "#00000000"
                    ),
                    SceneLayer(
                        id = "layer_start_alert",
                        type = LayerType.ALERT_BOX,
                        label = "Alert Box",
                        xPct = 20f,
                        yPct = 15f,
                        widthPct = 60f,
                        heightPct = 20f,
                        zIndex = 15
                    )
                )

                // Preset 3: Be Right Back (BRB)
                val scene3Layers = listOf(
                    SceneLayer(
                        id = "layer_brb_txt",
                        type = LayerType.TEXT_BANNER,
                        label = "BRB Notice",
                        xPct = 20f,
                        yPct = 42f,
                        widthPct = 60f,
                        heightPct = 16f,
                        zIndex = 10,
                        textContent = "BE RIGHT BACK\nGRABBING SOME WATER",
                        textColorHex = "#00E5FF",
                        bgColorHex = "#CC111722"
                    )
                )

                // Preset 4: Vertical Mobile Live (9:16 YouTube Shorts / Reels)
                val scene4Layers = listOf(
                    SceneLayer(
                        id = "layer_vert_game",
                        type = LayerType.GAME_SCREEN,
                        label = "Mobile Game Screen",
                        xPct = 0f,
                        yPct = 15f,
                        widthPct = 100f,
                        heightPct = 60f,
                        zIndex = 0
                    ),
                    SceneLayer(
                        id = "layer_vert_facecam",
                        type = LayerType.CAMERA_PIP,
                        label = "Facecam Top PiP",
                        xPct = 25f,
                        yPct = 2f,
                        widthPct = 50f,
                        heightPct = 12f,
                        zIndex = 10,
                        pipShape = "ROUNDED",
                        bgColorHex = "#FF00E676"
                    ),
                    SceneLayer(
                        id = "layer_vert_alert",
                        type = LayerType.ALERT_BOX,
                        label = "Alert Box",
                        xPct = 10f,
                        yPct = 76f,
                        widthPct = 80f,
                        heightPct = 10f,
                        zIndex = 15
                    ),
                    SceneLayer(
                        id = "layer_vert_ticker",
                        type = LayerType.TEXT_BANNER,
                        label = "Shorts Ticker",
                        xPct = 0f,
                        yPct = 90f,
                        widthPct = 100f,
                        heightPct = 8f,
                        zIndex = 20,
                        textContent = "⚡ VIPER STUDIO VERTICAL LIVE • SUBSCRIBE FOR MORE PRO CLUTCHES!",
                        textColorHex = "#00E676",
                        bgColorHex = "#D90A0E17",
                        isMarqueeScroll = true
                    )
                )

                database.sceneDao().insertScene(
                    SceneEntity("scene_pro_gaming", "BGMI Pro Gameplay + Facecam", true, serializeLayers(scene1Layers))
                )
                database.sceneDao().insertScene(
                    SceneEntity("scene_starting_soon", "Starting Soon", false, serializeLayers(scene2Layers))
                )
                database.sceneDao().insertScene(
                    SceneEntity("scene_brb", "Be Right Back (BRB)", false, serializeLayers(scene3Layers))
                )
                database.sceneDao().insertScene(
                    SceneEntity("scene_vertical_shorts", "Vertical Gaming (9:16 Shorts)", false, serializeLayers(scene4Layers))
                )
            }
        }
    }

    companion object {
        fun serializeLayers(layers: List<SceneLayer>): String {
            val array = JSONArray()
            for (l in layers) {
                val obj = JSONObject()
                obj.put("id", l.id)
                obj.put("type", l.type.name)
                obj.put("label", l.label)
                obj.put("xPct", l.xPct.toDouble())
                obj.put("yPct", l.yPct.toDouble())
                obj.put("widthPct", l.widthPct.toDouble())
                obj.put("heightPct", l.heightPct.toDouble())
                obj.put("rotationDeg", l.rotationDeg.toDouble())
                obj.put("isVisible", l.isVisible)
                obj.put("isLocked", l.isLocked)
                obj.put("zIndex", l.zIndex)
                obj.put("textContent", l.textContent)
                obj.put("textColorHex", l.textColorHex)
                obj.put("bgColorHex", l.bgColorHex)
                obj.put("pipShape", l.pipShape)
                obj.put("isMarqueeScroll", l.isMarqueeScroll)
                array.put(obj)
            }
            return array.toString()
        }

        fun deserializeLayers(json: String): List<SceneLayer> {
            val list = mutableListOf<SceneLayer>()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SceneLayer(
                            id = obj.getString("id"),
                            type = LayerType.valueOf(obj.getString("type")),
                            label = obj.optString("label", "Layer"),
                            xPct = obj.getDouble("xPct").toFloat(),
                            yPct = obj.getDouble("yPct").toFloat(),
                            widthPct = obj.getDouble("widthPct").toFloat(),
                            heightPct = obj.getDouble("heightPct").toFloat(),
                            rotationDeg = obj.optDouble("rotationDeg", 0.0).toFloat(),
                            isVisible = obj.optBoolean("isVisible", true),
                            isLocked = obj.optBoolean("isLocked", false),
                            zIndex = obj.optInt("zIndex", i),
                            textContent = obj.optString("textContent", ""),
                            textColorHex = obj.optString("textColorHex", "#00E676"),
                            bgColorHex = obj.optString("bgColorHex", "#B3111722"),
                            pipShape = obj.optString("pipShape", "ROUNDED"),
                            isMarqueeScroll = obj.optBoolean("isMarqueeScroll", false)
                        )
                    )
                }
            } catch (_: Exception) {}
            return list
        }
    }
}
