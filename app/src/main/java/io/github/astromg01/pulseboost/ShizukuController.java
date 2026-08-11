package io.github.astromg01.pulseboost;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import io.github.astromg01.pulseboost.shizuku.CommandService;
import io.github.astromg01.pulseboost.shizuku.ICommandService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

final class ShizukuController {
    enum Status {
        OFFLINE,
        PERMISSION_REQUIRED,
        CONNECTING,
        READY,
        UNSUPPORTED,
        ERROR
    }

    interface Listener {
        void onShizukuStatusChanged(Status status, String detail);
    }

    interface CommandCallback {
        void onComplete(ShellResult result);
    }

    private static final int PERMISSION_REQUEST_CODE = 4107;
    private static volatile ShizukuController instance;

    static ShizukuController get(Context context) {
        if (instance == null) {
            synchronized (ShizukuController.class) {
                if (instance == null) {
                    instance = new ShizukuController(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs userServiceArgs;

    private volatile ICommandService commandService;
    private volatile boolean binding;
    private volatile Status status = Status.OFFLINE;
    private volatile String statusDetail = "Shizuku não iniciado";
    private Listener listener;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::refresh;
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        commandService = null;
        binding = false;
        updateStatus(Status.OFFLINE, "O serviço do Shizuku parou");
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != PERMISSION_REQUEST_CODE) {
                    return;
                }
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    refresh();
                } else {
                    updateStatus(Status.PERMISSION_REQUIRED, "Permissão recusada no Shizuku");
                }
            };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            commandService = ICommandService.Stub.asInterface(binder);
            binding = false;
            updateStatus(Status.READY, "Conectado como shell • pronto para otimizar");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            commandService = null;
            binding = false;
            updateStatus(Status.CONNECTING, "Reconectando ao serviço de otimização");
            bindUserService();
        }
    };

    private ShizukuController(Context context) {
        this.context = context;
        this.userServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(context, CommandService.class))
                .daemon(false)
                .debuggable(false)
                .processNameSuffix("optimizer")
                .tag("pulseboost-optimizer")
                .version(1);

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener, mainHandler);
        Shizuku.addBinderDeadListener(binderDeadListener, mainHandler);
        Shizuku.addRequestPermissionResultListener(permissionResultListener, mainHandler);
    }

    void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) {
            listener.onShizukuStatusChanged(status, statusDetail);
        }
    }

    Status getStatus() {
        return status;
    }

    boolean isReady() {
        return status == Status.READY && commandService != null;
    }

    void refresh() {
        mainHandler.post(() -> {
            try {
                if (!Shizuku.pingBinder()) {
                    commandService = null;
                    binding = false;
                    updateStatus(Status.OFFLINE, "Inicie o Shizuku antes de jogar");
                    return;
                }
                if (Shizuku.isPreV11()) {
                    updateStatus(Status.UNSUPPORTED, "Atualize o Shizuku para a versão 11 ou superior");
                    return;
                }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    updateStatus(Status.PERMISSION_REQUIRED, "Autorize o PulseBoost dentro do Shizuku");
                    return;
                }
                if (commandService == null) {
                    updateStatus(Status.CONNECTING, "Preparando acesso seguro ao sistema");
                    bindUserService();
                } else {
                    updateStatus(Status.READY, "Conectado como shell • pronto para otimizar");
                }
            } catch (Throwable error) {
                commandService = null;
                binding = false;
                updateStatus(Status.ERROR, "Falha ao consultar Shizuku: " + safeMessage(error));
            }
        });
    }

    void requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                updateStatus(Status.OFFLINE, "Abra o Shizuku e inicie o serviço primeiro");
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refresh();
                return;
            }
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
        } catch (Throwable error) {
            updateStatus(Status.ERROR, "Não foi possível pedir permissão: " + safeMessage(error));
        }
    }

    boolean openManager() {
        try {
            Intent intent = context.getPackageManager()
                    .getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (intent == null) {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    void execute(String command, CommandCallback callback) {
        ICommandService service = commandService;
        if (service == null || status != Status.READY) {
            mainHandler.post(() -> callback.onComplete(
                    new ShellResult(-1, "Shizuku ainda não está pronto")));
            return;
        }

        executor.execute(() -> {
            ShellResult result;
            try {
                result = ShellResult.parse(service.exec(command));
            } catch (Throwable error) {
                result = new ShellResult(-1, "Falha no comando: " + safeMessage(error));
                commandService = null;
                binding = false;
                mainHandler.post(this::refresh);
            }
            ShellResult finalResult = result;
            mainHandler.post(() -> callback.onComplete(finalResult));
        });
    }

    ShellResult executeBlocking(String command) {
        ICommandService service = commandService;
        if (service == null || status != Status.READY) {
            return new ShellResult(-1, "Shizuku ainda não está pronto");
        }
        try {
            return ShellResult.parse(service.exec(command));
        } catch (Throwable error) {
            return new ShellResult(-1, "Falha no comando: " + safeMessage(error));
        }
    }

    void runInBackground(Runnable runnable) {
        executor.execute(runnable);
    }

    void postToMain(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private void bindUserService() {
        if (binding || commandService != null) {
            return;
        }
        binding = true;
        try {
            Shizuku.bindUserService(userServiceArgs, connection);
        } catch (Throwable error) {
            binding = false;
            updateStatus(Status.ERROR, "Falha ao abrir serviço: " + safeMessage(error));
        }
    }

    private void updateStatus(Status newStatus, String detail) {
        status = newStatus;
        statusDetail = detail;
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onShizukuStatusChanged(newStatus, detail);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
