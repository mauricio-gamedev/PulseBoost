package io.github.astromg01.cloudkeys.overlay

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku

class ShizukuKeyInjector(private val context: Context) : AutoCloseable {

    private var remote: IKeyInjector? = null

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context, ShizukuInputService::class.java)
    )
        .daemon(false)
        .debuggable(false)
        .version(1)
        .tag("cloudkeys-input")
        .processNameSuffix("input")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = service?.let { IKeyInjector.Stub.asInterface(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
        }
    }

    init { connect() }

    private fun connect() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        runCatching { Shizuku.bindUserService(args, connection) }
    }

    fun sendKey(keyCode: Int) {
        val r = remote ?: run {
            connect()
            return
        }
        runCatching { r.sendKey(keyCode) }
    }

    override fun close() {
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        remote = null
    }
}
