package com.local.appcontrol.demo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.Build;
import java.io.IOException;

final class AndroidBackend implements ControlManager.Backend {
    private final PackageManager packages;
    private final RootCommander root;
    private final int userId = Process.myUid() / 100000;
    AndroidBackend(Context context, RootCommander root) { packages = context.getPackageManager(); this.root = root; }
    @Override public boolean isControllable(String pkg) throws Exception {
        PackagePolicy.requireValid(pkg);
        if (PackagePolicy.protectedPackage(pkg)) return false;
        ApplicationInfo info = packages.getApplicationInfo(pkg, 0);
        if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo launcher = packages.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return launcher == null || launcher.activityInfo == null || !pkg.equals(launcher.activityInfo.packageName);
    }
    @Override public boolean isInstalled(String pkg) {
        PackagePolicy.requireValid(pkg);
        try { packages.getApplicationInfo(pkg, 0); return true; } catch (PackageManager.NameNotFoundException missing) { return false; }
    }
    @Override public boolean isSuspended(String pkg) throws Exception {
        PackagePolicy.requireValid(pkg);
        if (Build.VERSION.SDK_INT >= 29) return packages.isPackageSuspended(pkg);
        return (packages.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SUSPENDED) != 0;
    }
    @Override public void setSuspended(String pkg, boolean suspended) throws Exception {
        PackagePolicy.requireValid(pkg);
        if (suspended && !isControllable(pkg)) throw new IOException("不能暂停受保护应用");
        String command = "pm " + (suspended ? "suspend" : "unsuspend") + " --user " + userId + " " + pkg;
        if (suspended) command += " && am force-stop --user " + userId + " " + pkg;
        root.run(command, 8);
        if (isSuspended(pkg) != suspended) throw new IOException("系统状态未确认，请检查Root授权或系统限制");
    }
}
