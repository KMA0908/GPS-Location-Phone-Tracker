package com.nhn.gps.location.phone.tracker.ui.splash

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import kotlin.math.max

/** Texture-backed video view with a true center-crop transform for portrait splash screens. */
class CenterCropVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var videoUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var playbackErrorListener: (() -> Unit)? = null
    private var firstFrameRenderedListener: (() -> Unit)? = null
    private var hasRenderedFirstFrame = false

    init {
        surfaceTextureListener = this
    }

    fun setVideoUri(uri: Uri) {
        videoUri = uri
        if (isAvailable) preparePlayer(surfaceTexture)
    }

    fun setOnPlaybackErrorListener(listener: (() -> Unit)?) {
        playbackErrorListener = listener
    }

    fun setOnFirstFrameRenderedListener(listener: (() -> Unit)?) {
        firstFrameRenderedListener = listener
    }

    fun stopPlayback() {
        releasePlayer()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        preparePlayer(surfaceTexture)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        applyCenterCrop()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        if (!hasRenderedFirstFrame) {
            hasRenderedFirstFrame = true
            firstFrameRenderedListener?.invoke()
        }
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }

    private fun preparePlayer(surfaceTexture: SurfaceTexture?) {
        val uri = videoUri ?: return
        val texture = surfaceTexture ?: return
        releasePlayer()
        hasRenderedFirstFrame = false
        val surface = Surface(texture)
        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            player.setDataSource(context, uri)
            player.setSurface(surface)
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                applyCenterCrop()
            }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    if (!hasRenderedFirstFrame) {
                        hasRenderedFirstFrame = true
                        firstFrameRenderedListener?.invoke()
                    }
                }
                false
            }
            player.setOnPreparedListener {
                videoWidth = it.videoWidth
                videoHeight = it.videoHeight
                applyCenterCrop()
                it.start()
            }
            player.setOnErrorListener { _, _, _ ->
                playbackErrorListener?.invoke()
                true
            }
            player.prepareAsync()
        }.onFailure {
            releasePlayer()
            playbackErrorListener?.invoke()
        }
        surface.release()
    }

    private fun applyCenterCrop() {
        if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return
        val scale = max(
            width.toFloat() / videoWidth.toFloat(),
            height.toFloat() / videoHeight.toFloat(),
        )
        val scaledWidth = videoWidth * scale
        val scaledHeight = videoHeight * scale
        val transform = Matrix().apply {
            setScale(
                scaledWidth / width.toFloat(),
                scaledHeight / height.toFloat(),
                width / 2f,
                height / 2f,
            )
        }
        setTransform(transform)
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching {
            setOnPreparedListener(null)
            setOnVideoSizeChangedListener(null)
            setOnInfoListener(null)
            setOnErrorListener(null)
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
    }
}
