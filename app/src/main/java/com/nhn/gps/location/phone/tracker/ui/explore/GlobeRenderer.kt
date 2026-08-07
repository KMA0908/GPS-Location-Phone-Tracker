package com.nhn.gps.location.phone.tracker.ui.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.nhn.gps.location.phone.tracker.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

data class GlobeCameraState(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val cameraDistance: Float
)

data class GlobeMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val isSelected: Boolean = false,
    // Unit vector on sphere surface for faster rendering and hit testing
    internal val ux: Float = 0f,
    internal val uy: Float = 0f,
    internal val uz: Float = 0f
)

class GlobeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val MARKER_TEXTURE_SIZE = 128
    }

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Reusable objects for hit testing and transformations to avoid per-frame allocations
    private val tempModel = FloatArray(16)
    private val tempView = FloatArray(16)
    private val tempMVP = FloatArray(16)
    private val markerPos = FloatArray(4)
    private val clipPos = FloatArray(4)

    // Rotation and Zoom state
    @Volatile
    var angleX = 0f
    @Volatile
    var angleY = 0f
    @Volatile
    var cameraDistance = 6f

    // Markers state
    @Volatile
    private var markers: List<GlobeMarker> = emptyList()
    private val markerPositionBuffer: FloatBuffer = ByteBuffer.allocateDirect(3 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    private var program = 0
    private var vbo = 0
    private var ibo = 0
    private var textureId = 0
    private var indexCount = 0

    private var uMVPMatrixLocation = 0
    private var uTextureLocation = 0

    // Marker Shader Locations
    private var markerProgram = 0
    private var uMarkerMVPMatrixLocation = 0
    private var uMarkerTextureLocation = 0
    private var uMarkerPointSizeLocation = 0
    private var markerTextureId = 0

    fun updateMarkers(newMarkers: List<GlobeMarker>) {
        // Pre-calculate unit vectors for all markers to avoid trig in render loop
        markers = newMarkers.map { m ->
            val theta = (Math.PI / 2.0 - Math.toRadians(m.latitude)).toFloat()
            val phi = Math.toRadians(m.longitude).toFloat()
            m.copy(
                ux = sin(theta) * sin(phi),
                uy = cos(theta),
                uz = sin(theta) * cos(phi)
            )
        }
    }

    fun hitTest(tapX: Float, tapY: Float, width: Int, height: Int): String? {
        val currentMarkers = markers
        if (currentMarkers.isEmpty()) return null

        val hitRadiusSq = 80f * 80f // hit radius in pixels squared

        // Capture a thread-safe snapshot of the current camera state
        val curAngleX = angleX
        val curAngleY = angleY
        val curDist = cameraDistance

        // Use pre-allocated arrays to avoid GC pressure
        Matrix.setIdentityM(tempModel, 0)
        Matrix.rotateM(tempModel, 0, curAngleX, 1f, 0f, 0f)
        Matrix.rotateM(tempModel, 0, curAngleY, 0f, 1f, 0f)

        Matrix.setLookAtM(tempView, 0, 0f, 0f, curDist, 0f, 0f, 0f, 0f, 1f, 0f)

        Matrix.multiplyMM(tempMVP, 0, tempView, 0, tempModel, 0)
        Matrix.multiplyMM(tempMVP, 0, projectionMatrix, 0, tempMVP, 0)

        val radX = Math.toRadians(curAngleX.toDouble())
        val radY = Math.toRadians(curAngleY.toDouble())
        val camX = (-cos(radX) * sin(radY)).toFloat()
        val camY = sin(radX).toFloat()
        val camZ = (cos(radX) * cos(radY)).toFloat()

        for (marker in currentMarkers) {
            val radius = 2.05f

            markerPos[0] = marker.ux * radius
            markerPos[1] = marker.uy * radius
            markerPos[2] = marker.uz * radius
            markerPos[3] = 1f

            // Dot product with camera direction to check hemisphere (visibility)
            val dot = markerPos[0] * camX + markerPos[1] * camY + markerPos[2] * camZ
            if (dot < 0) continue

            Matrix.multiplyMV(clipPos, 0, tempMVP, 0, markerPos, 0)
            if (clipPos[3] == 0f) continue

            val ndcX = clipPos[0] / clipPos[3]
            val ndcY = clipPos[1] / clipPos[3]

            val screenX = (ndcX + 1f) * width / 2f
            val screenY = (1f - ndcY) * height / 2f

            val dx = screenX - tapX
            val dy = screenY - tapY
            if (dx * dx + dy * dy < hitRadiusSq) {
                return marker.id
            }
        }
        return null
    }

    fun getCameraState(): GlobeCameraState {
        val radX = Math.toRadians(angleX.toDouble())
        val radY = Math.toRadians(angleY.toDouble())

        val localX = -cos(radX) * sin(radY)
        val localY = sin(radX)
        val localZ = cos(radX) * cos(radY)

        val latitude = Math.toDegrees(asin(localY))
        val longitude = Math.toDegrees(atan2(localX, localZ))

        return GlobeCameraState(latitude, longitude, cameraDistance)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        initShaders()
        initMesh()
        loadTexture()
        loadMarkerTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (program == 0 || vbo == 0 || ibo == 0) return

        // 1. Draw Globe
        GLES30.glUseProgram(program)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angleX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES30.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uTextureLocation, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 5 * 4, 3 * 4)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)

        // 2. Draw Markers
        drawMarkers()
    }

    private fun drawMarkers() {
        val currentMarkers = markers
        if (currentMarkers.isEmpty() || markerProgram == 0) return

        GLES30.glUseProgram(markerProgram)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        
        // Depth test is on, so markers behind globe will be occluded.
        val radius = 2.05f 

        GLES30.glUniformMatrix4fv(uMarkerMVPMatrixLocation, 1, false, mvpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, markerTextureId)
        GLES30.glUniform1i(uMarkerTextureLocation, 0)

        for (marker in currentMarkers) {
            val mx = marker.ux * radius
            val my = marker.uy * radius
            val mz = marker.uz * radius

            val pointSize = if (marker.isSelected) 100f else 60f
            GLES30.glUniform1f(uMarkerPointSizeLocation, pointSize)

            markerPositionBuffer.clear()
            markerPositionBuffer.put(mx).put(my).put(mz).position(0)
            
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, markerPositionBuffer)
            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 1)
        }
        
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun initShaders() {
        // Globe Shaders
        val vertexShaderSource = """#version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            uniform mat4 uMVPMatrix;
            out vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val fragmentShaderSource = """#version 300 es
            precision mediump float;
            in vec2 vTexCoord;
            uniform sampler2D uTexture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vTexCoord);
            }
        """.trimIndent()

        program = createProgram(vertexShaderSource, fragmentShaderSource)
        uMVPMatrixLocation = GLES30.glGetUniformLocation(program, "uMVPMatrix")
        uTextureLocation = GLES30.glGetUniformLocation(program, "uTexture")

        // Marker Shaders
        val markerVertexShaderSource = """#version 300 es
            layout(location = 0) in vec4 aPosition;
            uniform mat4 uMVPMatrix;
            uniform float uPointSize;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                gl_PointSize = uPointSize;
            }
        """.trimIndent()

        val markerFragmentShaderSource = """#version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, gl_PointCoord);
                if (fragColor.a < 0.1) discard;
            }
        """.trimIndent()

        markerProgram = createProgram(markerVertexShaderSource, markerFragmentShaderSource)
        uMarkerMVPMatrixLocation = GLES30.glGetUniformLocation(markerProgram, "uMVPMatrix")
        uMarkerTextureLocation = GLES30.glGetUniformLocation(markerProgram, "uTexture")
        uMarkerPointSizeLocation = GLES30.glGetUniformLocation(markerProgram, "uPointSize")
    }

    private fun createProgram(vSource: String, fSource: String): Int {
        val vs = loadShader(GLES30.GL_VERTEX_SHADER, vSource)
        val fs = loadShader(GLES30.GL_FRAGMENT_SHADER, fSource)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        return p
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        return shader
    }

    private fun initMesh() {
        val latitudeSegments = 64
        val longitudeSegments = 128
        val radius = 1.5f

        val vertices = mutableListOf<Float>()
        for (lat in 0..latitudeSegments) {
            val theta = (lat * Math.PI / latitudeSegments).toFloat()
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            for (lon in 0..longitudeSegments) {
                val phi = (lon * 2 * Math.PI / longitudeSegments).toFloat()
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                // Correct spherical to Cartesian mapping for standard texture orientation
                // In OpenGL: Y is up, X is right, Z is towards viewer.
                // For a globe: 
                // x = R * sin(theta) * sin(phi)
                // y = R * cos(theta) (North pole at theta=0, y=R; South pole at theta=PI, y=-R)
                // z = R * sin(theta) * cos(phi)
                val x = sinTheta * sinPhi
                val y = cosTheta 
                val z = sinTheta * cosPhi

                // Standard UV mapping:
                // U: [0, 1] maps to longitude [0, 360]
                // V: [0, 1] maps to latitude [North, South]
                val u = lon.toFloat() / longitudeSegments
                val v = lat.toFloat() / latitudeSegments

                vertices.add(x * radius)
                vertices.add(y * radius)
                vertices.add(z * radius)
                vertices.add(u)
                vertices.add(v)
            }
        }

        val indices = mutableListOf<Short>()
        for (lat in 0 until latitudeSegments) {
            for (lon in 0 until longitudeSegments) {
                val first = (lat * (longitudeSegments + 1) + lon).toShort()
                val second = (first + longitudeSegments + 1).toShort()

                indices.add(first)
                indices.add(second)
                indices.add((first + 1).toShort())

                indices.add(second)
                indices.add((second + 1).toShort())
                indices.add((first + 1).toShort())
            }
        }

        indexCount = indices.size

        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices.toFloatArray()).position(0)

        val indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        indexBuffer.put(indices.toShortArray()).position(0)

        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        vbo = buffers[0]
        ibo = buffers[1]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, indexBuffer, GLES30.GL_STATIC_DRAW)
    }

    private fun loadTexture() {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.earth_texture, options)
        } catch (e: Exception) {
            android.util.Log.e("GlobeRenderer", "Error decoding earth texture", e)
            null
        }

        if (bitmap != null) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
        } else {
            android.util.Log.e("GlobeRenderer", "Failed to load earth texture: ic_earth_3d is null")
            // Set a fallback color or texture if possible
        }
    }

    private fun loadMarkerTexture() {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        markerTextureId = textures[0]

        val bitmap = drawableToBitmap(R.drawable.ic_marker_pin, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE)

        if (bitmap != null) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, markerTextureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
        } else {
            android.util.Log.e("GlobeRenderer", "Failed to load marker texture: Bitmap is null")
        }
    }

    private fun drawableToBitmap(drawableId: Int, width: Int, height: Int): Bitmap? {
        return try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId) ?: return null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("GlobeRenderer", "Error converting drawable to bitmap", e)
            null
        }
    }
}
