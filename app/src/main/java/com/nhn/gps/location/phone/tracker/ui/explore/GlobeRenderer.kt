package com.nhn.gps.location.phone.tracker.ui.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.nhn.gps.location.phone.tracker.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class GlobeCameraState(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val cameraDistance: Float,
)

data class GlobeMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val imageRes: Int = R.drawable.place_category_1,
    val isSelected: Boolean = false,
    internal val ux: Float = 0f,
    internal val uy: Float = 0f,
    internal val uz: Float = 0f,
)

/**
 * Small self-contained globe renderer inspired by the WorldWind presentation in the sample app.
 * It adds a star field, directional lighting and a separate atmospheric rim pass while keeping
 * the existing offline texture and marker interaction.
 */
class GlobeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "GlobeRenderer"
        private const val GLOBE_RADIUS = 1.55f
        private const val MARKER_RADIUS = 1.61f
        private const val MARKER_TEXTURE_SIZE = 128
        private const val STAR_COUNT = 260
    }

    private val modelMatrix = FloatArray(16)
    private val atmosphereModelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewModelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val atmosphereViewModelMatrix = FloatArray(16)
    private val atmosphereMvpMatrix = FloatArray(16)

    private val tempModel = FloatArray(16)
    private val tempView = FloatArray(16)
    private val tempMvp = FloatArray(16)
    private val markerPosition = FloatArray(4)
    private val clipPosition = FloatArray(4)

    @Volatile var angleX = 18f
    @Volatile var angleY = 8f
    @Volatile var cameraDistance = 4.55f
    @Volatile var autoRotate = true

    @Volatile private var markers: List<GlobeMarker> = emptyList()
    private var globeProgram = 0
    private var atmosphereProgram = 0
    private var markerProgram = 0
    private var starProgram = 0
    private var sphereVbo = 0
    private var sphereIbo = 0
    private var starVbo = 0
    private var textureId = 0
    private var markerTextureId = 0
    private var indexCount = 0
    private var lastFrameNanos = 0L

    private var globeMvpLocation = 0
    private var globeModelLocation = 0
    private var globeTextureLocation = 0
    private var globeCameraDistanceLocation = 0

    private var atmosphereMvpLocation = 0
    private var atmosphereModelLocation = 0
    private var atmosphereCameraDistanceLocation = 0

    private var markerMvpLocation = 0
    private var markerTextureLocation = 0
    private var markerPointSizeLocation = 0

    fun updateMarkers(newMarkers: List<GlobeMarker>) {
        markers = newMarkers.map { marker ->
            val theta = (Math.PI / 2.0 - Math.toRadians(marker.latitude)).toFloat()
            val phi = Math.toRadians(marker.longitude).toFloat()
            marker.copy(
                ux = sin(theta) * sin(phi),
                uy = cos(theta),
                uz = sin(theta) * cos(phi),
            )
        }
    }

    fun hitTest(tapX: Float, tapY: Float, width: Int, height: Int): String? {
        val currentMarkers = markers
        if (currentMarkers.isEmpty()) return null

        Matrix.setIdentityM(tempModel, 0)
        Matrix.rotateM(tempModel, 0, angleX, 1f, 0f, 0f)
        Matrix.rotateM(tempModel, 0, angleY, 0f, 1f, 0f)
        Matrix.setLookAtM(tempView, 0, 0f, 0f, cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(tempMvp, 0, tempView, 0, tempModel, 0)
        Matrix.multiplyMM(tempMvp, 0, projectionMatrix, 0, tempMvp, 0)

        val radiansX = Math.toRadians(angleX.toDouble())
        val radiansY = Math.toRadians(angleY.toDouble())
        val cameraX = (-cos(radiansX) * sin(radiansY)).toFloat()
        val cameraY = sin(radiansX).toFloat()
        val cameraZ = (cos(radiansX) * cos(radiansY)).toFloat()
        val hitRadiusSquared = 64f * 64f

        for (marker in currentMarkers) {
            markerPosition[0] = marker.ux * MARKER_RADIUS
            markerPosition[1] = marker.uy * MARKER_RADIUS
            markerPosition[2] = marker.uz * MARKER_RADIUS
            markerPosition[3] = 1f

            val facingCamera = markerPosition[0] * cameraX +
                markerPosition[1] * cameraY +
                markerPosition[2] * cameraZ
            if (facingCamera < 0f) continue

            Matrix.multiplyMV(clipPosition, 0, tempMvp, 0, markerPosition, 0)
            if (clipPosition[3] == 0f) continue
            val normalizedX = clipPosition[0] / clipPosition[3]
            val normalizedY = clipPosition[1] / clipPosition[3]
            val screenX = (normalizedX + 1f) * width / 2f
            val screenY = (1f - normalizedY) * height / 2f
            val deltaX = screenX - tapX
            val deltaY = screenY - tapY
            if (deltaX * deltaX + deltaY * deltaY <= hitRadiusSquared) return marker.id
        }
        return null
    }

    fun getCameraState(): GlobeCameraState {
        val radiansX = Math.toRadians(angleX.toDouble())
        val radiansY = Math.toRadians(angleY.toDouble())
        val localX = -cos(radiansX) * sin(radiansY)
        val localY = sin(radiansX)
        val localZ = cos(radiansX) * cos(radiansY)
        return GlobeCameraState(
            centerLatitude = Math.toDegrees(asin(localY)),
            centerLongitude = Math.toDegrees(atan2(localX, localZ)),
            cameraDistance = cameraDistance,
        )
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.008f, 0.02f, 0.065f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        initShaders()
        initSphereMesh()
        initStars()
        loadEarthTexture()
        loadMarkerTexture()
        lastFrameNanos = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projectionMatrix, 0, 43f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        updateAutoRotation()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawStars()
        if (globeProgram == 0 || sphereVbo == 0 || sphereIbo == 0) return

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angleX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, cameraDistance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(viewModelMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewModelMatrix, 0)

        drawGlobe()
        drawAtmosphere()
        drawMarkers()
    }

    private fun updateAutoRotation() {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L && autoRotate) {
            val elapsedSeconds = ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            angleY = (angleY + elapsedSeconds * 1.35f) % 360f
        }
        lastFrameNanos = now
    }

    private fun drawStars() {
        if (starProgram == 0 || starVbo == 0) return
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glUseProgram(starProgram)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, starVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * Float.SIZE_BYTES, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 4 * Float.SIZE_BYTES, 2 * Float.SIZE_BYTES)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, 4 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, STAR_COUNT)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawGlobe() {
        GLES30.glUseProgram(globeProgram)
        GLES30.glUniformMatrix4fv(globeMvpLocation, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(globeModelLocation, 1, false, modelMatrix, 0)
        GLES30.glUniform1f(globeCameraDistanceLocation, cameraDistance)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(globeTextureLocation, 0)
        bindSphereAttributes(withTextureCoordinates = true)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
    }

    private fun drawAtmosphere() {
        if (atmosphereProgram == 0) return
        System.arraycopy(modelMatrix, 0, atmosphereModelMatrix, 0, modelMatrix.size)
        Matrix.scaleM(atmosphereModelMatrix, 0, 1.065f, 1.065f, 1.065f)
        Matrix.multiplyMM(atmosphereViewModelMatrix, 0, viewMatrix, 0, atmosphereModelMatrix, 0)
        Matrix.multiplyMM(atmosphereMvpMatrix, 0, projectionMatrix, 0, atmosphereViewModelMatrix, 0)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glCullFace(GLES30.GL_FRONT)
        GLES30.glUseProgram(atmosphereProgram)
        GLES30.glUniformMatrix4fv(atmosphereMvpLocation, 1, false, atmosphereMvpMatrix, 0)
        GLES30.glUniformMatrix4fv(atmosphereModelLocation, 1, false, atmosphereModelMatrix, 0)
        GLES30.glUniform1f(atmosphereCameraDistanceLocation, cameraDistance)
        bindSphereAttributes(withTextureCoordinates = false)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawMarkers() {
        val currentMarkers = markers
        if (currentMarkers.isEmpty() || markerProgram == 0) return
        GLES30.glUseProgram(markerProgram)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glUniformMatrix4fv(markerMvpLocation, 1, false, mvpMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, markerTextureId)
        GLES30.glUniform1i(markerTextureLocation, 0)

        currentMarkers.forEach { marker ->
            GLES30.glUniform1f(markerPointSizeLocation, if (marker.isSelected) 92f else 62f)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glVertexAttrib3f(
                0,
                marker.ux * MARKER_RADIUS,
                marker.uy * MARKER_RADIUS,
                marker.uz * MARKER_RADIUS,
            )
            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 1)
        }
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun bindSphereAttributes(withTextureCoordinates: Boolean) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sphereVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0)
        if (withTextureCoordinates) {
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(
                1,
                2,
                GLES30.GL_FLOAT,
                false,
                5 * Float.SIZE_BYTES,
                3 * Float.SIZE_BYTES,
            )
        } else {
            GLES30.glDisableVertexAttribArray(1)
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, sphereIbo)
    }

    private fun initShaders() {
        val globeVertex = """#version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec2 aTexCoord;
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            out vec2 vTexCoord;
            out vec3 vWorldPosition;
            out vec3 vNormal;
            void main() {
                vec4 worldPosition = uModelMatrix * aPosition;
                gl_Position = uMvpMatrix * aPosition;
                vTexCoord = aTexCoord;
                vWorldPosition = worldPosition.xyz;
                vNormal = normalize(mat3(uModelMatrix) * normalize(aPosition.xyz));
            }
        """.trimIndent()
        val globeFragment = """#version 300 es
            precision highp float;
            in vec2 vTexCoord;
            in vec3 vWorldPosition;
            in vec3 vNormal;
            uniform sampler2D uTexture;
            uniform float uCameraDistance;
            out vec4 fragColor;
            void main() {
                vec3 normal = normalize(vNormal);
                vec3 lightDirection = normalize(vec3(-0.45, 0.36, 1.0));
                vec3 viewDirection = normalize(vec3(0.0, 0.0, uCameraDistance) - vWorldPosition);
                float diffuse = max(dot(normal, lightDirection), 0.0);
                float ambient = 0.28;
                float rim = pow(1.0 - max(dot(normal, viewDirection), 0.0), 3.2);
                float specular = pow(max(dot(reflect(-lightDirection, normal), viewDirection), 0.0), 28.0);
                vec3 textureColor = texture(uTexture, vTexCoord).rgb;
                vec3 litColor = textureColor * (ambient + diffuse * 0.84);
                litColor += vec3(0.06, 0.26, 0.55) * rim * 0.55;
                litColor += vec3(0.72, 0.86, 1.0) * specular * 0.16;
                fragColor = vec4(litColor, 1.0);
            }
        """.trimIndent()
        globeProgram = createProgram(globeVertex, globeFragment)
        globeMvpLocation = GLES30.glGetUniformLocation(globeProgram, "uMvpMatrix")
        globeModelLocation = GLES30.glGetUniformLocation(globeProgram, "uModelMatrix")
        globeTextureLocation = GLES30.glGetUniformLocation(globeProgram, "uTexture")
        globeCameraDistanceLocation = GLES30.glGetUniformLocation(globeProgram, "uCameraDistance")

        val atmosphereVertex = """#version 300 es
            layout(location = 0) in vec4 aPosition;
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            out vec3 vWorldPosition;
            out vec3 vNormal;
            void main() {
                vec4 worldPosition = uModelMatrix * aPosition;
                gl_Position = uMvpMatrix * aPosition;
                vWorldPosition = worldPosition.xyz;
                vNormal = normalize(mat3(uModelMatrix) * normalize(aPosition.xyz));
            }
        """.trimIndent()
        val atmosphereFragment = """#version 300 es
            precision highp float;
            in vec3 vWorldPosition;
            in vec3 vNormal;
            uniform float uCameraDistance;
            out vec4 fragColor;
            void main() {
                vec3 viewDirection = normalize(vec3(0.0, 0.0, uCameraDistance) - vWorldPosition);
                float fresnel = pow(1.0 - abs(dot(normalize(vNormal), viewDirection)), 2.15);
                float alpha = smoothstep(0.08, 0.94, fresnel) * 0.72;
                fragColor = vec4(0.12, 0.48, 1.0, alpha);
            }
        """.trimIndent()
        atmosphereProgram = createProgram(atmosphereVertex, atmosphereFragment)
        atmosphereMvpLocation = GLES30.glGetUniformLocation(atmosphereProgram, "uMvpMatrix")
        atmosphereModelLocation = GLES30.glGetUniformLocation(atmosphereProgram, "uModelMatrix")
        atmosphereCameraDistanceLocation = GLES30.glGetUniformLocation(atmosphereProgram, "uCameraDistance")

        val markerVertex = """#version 300 es
            layout(location = 0) in vec4 aPosition;
            uniform mat4 uMvpMatrix;
            uniform float uPointSize;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
                gl_PointSize = uPointSize;
            }
        """.trimIndent()
        val markerFragment = """#version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, gl_PointCoord);
                if (fragColor.a < 0.08) discard;
            }
        """.trimIndent()
        markerProgram = createProgram(markerVertex, markerFragment)
        markerMvpLocation = GLES30.glGetUniformLocation(markerProgram, "uMvpMatrix")
        markerTextureLocation = GLES30.glGetUniformLocation(markerProgram, "uTexture")
        markerPointSizeLocation = GLES30.glGetUniformLocation(markerProgram, "uPointSize")

        val starVertex = """#version 300 es
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in float aSize;
            layout(location = 2) in float aBrightness;
            out float vBrightness;
            void main() {
                gl_Position = vec4(aPosition, 0.99, 1.0);
                gl_PointSize = aSize;
                vBrightness = aBrightness;
            }
        """.trimIndent()
        val starFragment = """#version 300 es
            precision mediump float;
            in float vBrightness;
            out vec4 fragColor;
            void main() {
                float distanceToCenter = distance(gl_PointCoord, vec2(0.5));
                float alpha = (1.0 - smoothstep(0.05, 0.5, distanceToCenter)) * vBrightness;
                fragColor = vec4(0.72, 0.86, 1.0, alpha);
            }
        """.trimIndent()
        starProgram = createProgram(starVertex, starFragment)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == 0 || fragmentShader == 0) return 0
        return GLES30.glCreateProgram().also { program ->
            GLES30.glAttachShader(program, vertexShader)
            GLES30.glAttachShader(program, fragmentShader)
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) Log.e(TAG, "Program link failed: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
        }
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun initSphereMesh() {
        val latitudeSegments = 64
        val longitudeSegments = 128
        val vertices = ArrayList<Float>((latitudeSegments + 1) * (longitudeSegments + 1) * 5)
        for (latitude in 0..latitudeSegments) {
            val theta = (latitude * Math.PI / latitudeSegments).toFloat()
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            for (longitude in 0..longitudeSegments) {
                val phi = (longitude * 2.0 * Math.PI / longitudeSegments).toFloat()
                val x = sinTheta * sin(phi)
                val y = cosTheta
                val z = sinTheta * cos(phi)
                vertices += x * GLOBE_RADIUS
                vertices += y * GLOBE_RADIUS
                vertices += z * GLOBE_RADIUS
                vertices += longitude.toFloat() / longitudeSegments
                vertices += latitude.toFloat() / latitudeSegments
            }
        }

        val indices = ArrayList<Short>(latitudeSegments * longitudeSegments * 6)
        for (latitude in 0 until latitudeSegments) {
            for (longitude in 0 until longitudeSegments) {
                val first = (latitude * (longitudeSegments + 1) + longitude).toShort()
                val second = (first + longitudeSegments + 1).toShort()
                indices += first
                indices += second
                indices += (first + 1).toShort()
                indices += second
                indices += (second + 1).toShort()
                indices += (first + 1).toShort()
            }
        }
        indexCount = indices.size

        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices.toFloatArray())
            .apply { position(0) }
        val indexBuffer = ByteBuffer.allocateDirect(indices.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices.toShortArray())
            .apply { position(0) }

        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        sphereVbo = buffers[0]
        sphereIbo = buffers[1]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sphereVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * Float.SIZE_BYTES,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW,
        )
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, sphereIbo)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indices.size * Short.SIZE_BYTES,
            indexBuffer,
            GLES30.GL_STATIC_DRAW,
        )
    }

    private fun initStars() {
        val random = Random(20260803)
        val stars = FloatArray(STAR_COUNT * 4)
        repeat(STAR_COUNT) { index ->
            val offset = index * 4
            stars[offset] = random.nextFloat() * 2f - 1f
            stars[offset + 1] = random.nextFloat() * 2f - 1f
            stars[offset + 2] = 1.2f + random.nextFloat() * 3.1f
            stars[offset + 3] = 0.35f + random.nextFloat() * 0.65f
        }
        val buffer = ByteBuffer.allocateDirect(stars.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(stars)
            .apply { position(0) }
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        starVbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, starVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            stars.size * Float.SIZE_BYTES,
            buffer,
            GLES30.GL_STATIC_DRAW,
        )
    }

    private fun loadEarthTexture() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        textureId = ids[0]
        val bitmap = runCatching {
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.earth_texture,
                BitmapFactory.Options().apply { inScaled = false },
            )
        }.onFailure { Log.e(TAG, "Unable to decode earth texture", it) }.getOrNull()
        if (bitmap == null) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bitmap.recycle()
    }

    private fun loadMarkerTexture() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        markerTextureId = ids[0]
        val bitmap = drawableToBitmap(R.drawable.ic_marker_pin, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE)
            ?: return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, markerTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    private fun drawableToBitmap(drawableId: Int, width: Int, height: Int): Bitmap? = runCatching {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId)
            ?: return@runCatching null
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }.onFailure { Log.e(TAG, "Unable to create marker texture", it) }.getOrNull()
}
