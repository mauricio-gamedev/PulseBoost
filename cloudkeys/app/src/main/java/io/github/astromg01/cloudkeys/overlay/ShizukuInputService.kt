package io.github.astromg01.cloudkeys.overlay

import java.util.concurrent.Executors

class ShizukuInputService : IKeyInjector.Stub() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun sendKey(keyCode: Int) {
        require(keyCode in 0..288)
        executor.execute {
            runCatching {
                val process = ProcessBuilder(
                    "/system/bin/input",
                    "keyevent",
                    keyCode.toString()
                ).redirectErrorStream(true).start()
                process.inputStream.use { it.readBytes() }
                process.waitFor()
            }
        }
    }

    override fun destroy() {
        executor.shutdownNow()
        System.exit(0)
    }
}
