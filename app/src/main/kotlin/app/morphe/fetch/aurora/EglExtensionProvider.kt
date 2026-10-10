package app.morphe.fetch.aurora

import android.opengl.GLES10
import android.text.TextUtils
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

internal object EglExtensionProvider {

    val eglExtensions: List<String>
        get() = runCatching {
            val extensions = mutableSetOf<String>()
            val egl = EGLContext.getEGL() as? EGL10 ?: return@runCatching emptyList()
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL10.EGL_NO_DISPLAY) return@runCatching emptyList()

            egl.eglInitialize(display, null)
            val configCount = IntArray(1)

            if (egl.eglGetConfigs(display, null, 0, configCount) && configCount[0] > 0) {
                val configs = arrayOfNulls<EGLConfig>(configCount[0])
                if (egl.eglGetConfigs(display, configs, configCount[0], configCount)) {
                    val pbufferAttribs = intArrayOf(
                        EGL10.EGL_WIDTH,
                        EGL10.EGL_PBUFFER_BIT,
                        EGL10.EGL_HEIGHT,
                        EGL10.EGL_PBUFFER_BIT,
                        EGL10.EGL_NONE
                    )
                    val contextAttributes = intArrayOf(12440, EGL10.EGL_PIXMAP_BIT, EGL10.EGL_NONE)

                    for (config in configs) {
                        if (isValidConfig(egl, display, config)) {
                            addExtensionsForConfig(
                                egl,
                                display,
                                config,
                                pbufferAttribs,
                                null,
                                extensions
                            )
                            addExtensionsForConfig(
                                egl,
                                display,
                                config,
                                pbufferAttribs,
                                contextAttributes,
                                extensions
                            )
                        }
                    }
                }
            }

            egl.eglTerminate(display)

            extensions.filter { it.isNotEmpty() }.sorted()
        }.getOrDefault(emptyList())

    private fun isValidConfig(egl: EGL10, display: EGLDisplay, config: EGLConfig?): Boolean {
        if (config == null) return false
        val configAttrib = IntArray(1)
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_CONFIG_CAVEAT, configAttrib)
        if (configAttrib[0] == EGL10.EGL_SLOW_CONFIG) return false

        egl.eglGetConfigAttrib(display, config, EGL10.EGL_SURFACE_TYPE, configAttrib)
        if (configAttrib[0] and 1 == 0) return false

        egl.eglGetConfigAttrib(display, config, EGL10.EGL_RENDERABLE_TYPE, configAttrib)
        return configAttrib[0] and 1 != 0
    }

    private fun addExtensionsForConfig(
        egl: EGL10,
        display: EGLDisplay,
        config: EGLConfig?,
        pbufferAttribs: IntArray,
        contextAttribs: IntArray?,
        extensions: MutableSet<String>
    ) {
        val context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, contextAttribs)
        if (context == null || context == EGL10.EGL_NO_CONTEXT) return

        val surface = egl.eglCreatePbufferSurface(display, config, pbufferAttribs)
        if (surface == null || surface == EGL10.EGL_NO_SURFACE) {
            egl.eglDestroyContext(display, context)
            return
        }

        egl.eglMakeCurrent(display, surface, surface, context)
        val extensionString = GLES10.glGetString(GLES10.GL_EXTENSIONS)

        if (!TextUtils.isEmpty(extensionString)) {
            extensions.addAll(extensionString.split(" "))
        }

        egl.eglMakeCurrent(
            display,
            EGL10.EGL_NO_SURFACE,
            EGL10.EGL_NO_SURFACE,
            EGL10.EGL_NO_CONTEXT
        )
        egl.eglDestroySurface(display, surface)
        egl.eglDestroyContext(display, context)
    }
}
