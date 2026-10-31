package com.local.appcontrol.demo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Shell arguments are never accepted from raw UI text; identity and self-protection are checked centrally. */
public final class PackagePolicy {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "com.local.appcontrol.demo", "com.android.settings", "com.android.systemui", "com.android.shell",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller",
            "com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch"));
    private PackagePolicy() { }
    public static boolean valid(String pkg) { return pkg != null && PACKAGE.matcher(pkg).matches(); }
    public static boolean protectedPackage(String pkg) { return PROTECTED.contains(pkg); }
    public static void requireValid(String pkg) { if (!valid(pkg)) throw new IllegalArgumentException("无效应用包名"); }
}
