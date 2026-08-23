package re.lilith.kalia.frame

object RenderThreadRef {
    @Volatile
    @JvmStatic
    var thread: Thread? = null
}
