package re.lilith.kalia

object KaliaHooks {
    @JvmStatic
    fun renderFrame(renderGame: Runnable) {
        if (!KaliaEngine.ensureStarted()) {
            renderGame.run()
            return
        }
        if (!KaliaEngine.renderFrame(renderGame::run)) {
            renderGame.run()
        }
    }

    @JvmStatic
    fun isActive(): Boolean = KaliaEngine.isActive

    @JvmStatic
    fun shutdown() {
        KaliaEngine.shutdown()
    }

    @JvmStatic
    fun setVsync(enabled: Boolean) {
        KaliaEngine.settings = KaliaEngine.settings.copy(vsync = enabled)
    }
}
