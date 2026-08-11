package io.github.astromg01.pulseboost;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class GameRepository {
    static final class GameApp {
        final String label;
        final String packageName;
        final Drawable icon;
        final boolean categorizedAsGame;

        GameApp(String label, String packageName, Drawable icon, boolean categorizedAsGame) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.categorizedAsGame = categorizedAsGame;
        }
    }

    private GameRepository() {
    }

    static List<GameApp> loadLauncherApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                launcherIntent, PackageManager.MATCH_ALL);
        List<GameApp> apps = new ArrayList<>();
        Set<String> seenPackages = new HashSet<>();

        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (packageName == null
                    || packageName.equals(context.getPackageName())
                    || packageName.equals("moe.shizuku.privileged.api")
                    || !seenPackages.add(packageName)) {
                continue;
            }
            try {
                ApplicationInfo appInfo = info.activityInfo.applicationInfo;
                CharSequence labelText = info.loadLabel(packageManager);
                String label = labelText == null ? packageName : labelText.toString().trim();
                Drawable icon = info.loadIcon(packageManager);
                apps.add(new GameApp(
                        label.isEmpty() ? packageName : label,
                        packageName,
                        icon,
                        appInfo.category == ApplicationInfo.CATEGORY_GAME));
            } catch (Throwable ignored) {
                // Um pacote quebrado não deve impedir a lista dos demais aplicativos.
            }
        }

        Collator collator = Collator.getInstance(new Locale("pt", "BR"));
        collator.setStrength(Collator.PRIMARY);
        apps.sort((left, right) -> {
            if (left.categorizedAsGame != right.categorizedAsGame) {
                return left.categorizedAsGame ? -1 : 1;
            }
            return collator.compare(left.label, right.label);
        });
        return apps;
    }

    static GameApp findByPackage(List<GameApp> apps, String packageName) {
        if (packageName == null) {
            return null;
        }
        for (GameApp app : apps) {
            if (packageName.equals(app.packageName)) {
                return app;
            }
        }
        return null;
    }
}
