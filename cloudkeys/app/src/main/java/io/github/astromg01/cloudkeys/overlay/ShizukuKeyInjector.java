package io.github.astromg01.cloudkeys.overlay;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import rikka.shizuku.Shizuku;

public final class ShizukuKeyInjector implements AutoCloseable {

    private final Shizuku.UserServiceArgs args;
    private IKeyInjector remote;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remote = IKeyInjector.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
        }
    };

    public ShizukuKeyInjector(Context context) {
        args = new Shizuku.UserServiceArgs(
                new ComponentName(context, ShizukuInputService.class)
        )
                .daemon(false)
                .debuggable(false)
                // Bump the user-service version after changing the AIDL so
                // Shizuku replaces an older cached service process.
                .version(5)
                .tag("cloudkeys-input")
                .processNameSuffix("input");
        connect();
    }

    private void connect() {
        if (!Shizuku.pingBinder()) return;
        if (Shizuku.checkSelfPermission()
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;

        try {
            Shizuku.bindUserService(args, connection);
        } catch (Throwable ignored) {
        }
    }

    public void sendKey(int keyCode) {
        IKeyInjector current = remote;
        if (current == null) {
            connect();
            current = remote;
        }
        if (current == null) return;

        try {
            current.sendKey(keyCode);
        } catch (Throwable ignored) {
            remote = null;
        }
    }

    public void sendTap(int x, int y) {
        IKeyInjector current = remote;
        if (current == null) {
            connect();
            current = remote;
        }
        if (current == null) return;

        try {
            current.sendTap(x, y);
        } catch (Throwable ignored) {
            remote = null;
        }
    }

    @Override
    public void close() {
        try {
            Shizuku.unbindUserService(args, connection, true);
        } catch (Throwable ignored) {
        }
        remote = null;
    }
}
