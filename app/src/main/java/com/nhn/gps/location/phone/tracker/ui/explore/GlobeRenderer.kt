package com.nhn.gps.location.phone.tracker.ui.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.nhn.gps.location.phone.tracker.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class GlobeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var program = 0
    private var vbo = 0
    private var ibo = 0
    private var textureId = 0
    private var indexCount = 0

    private var uMVPMatrixLocation = 0
    private var uTextureLocation = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        initShaders()
        initMesh()
        loadTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (program == 0 || vbo == 0 || ibo == 0) return

        GLES30.glUseProgram(program)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES30.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uTextureLocation, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        
        // Position
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * 4, 0)

        // TexCoord
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 5 * 4, 3 * 4)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun initShaders() {
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

        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource)

        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        uMVPMatrixLocation = GLES30.glGetUniformLocation(program, "uMVPMatrix")
        uTextureLocation = GLES30.glGetUniformLocation(program, "uTexture")
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        return shader
    }

    private fun initMesh() {
        val latitudeSegments = 32
        val longitudeSegments = 64
        val radius = 2.0f

        val vertices = mutableListOf<Float>()
        for (lat in 0..latitudeSegments) {
            val theta = lat * Math.PI / latitudeSegments
            val sinTheta = sin(theta).toFloat()
            val cosTheta = cos(theta).toFloat()

            for (lon in 0..longitudeSegments) {
                val phi = lon * 2 * Math.PI / longitudeSegments
                val sinPhi = sin(phi).toFloat()
                val cosPhi = cos(phi).toFloat()

                val x = cosPhi * sinTheta
                val y = cosTheta
                val z = sinPhi * sinTheta

                val u = 1f - (lon.toFloat() / longitudeSegments)
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
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_earth_3d, options)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }
}
