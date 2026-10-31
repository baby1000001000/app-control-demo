package com.local.appcontrol.demo;

final class AppEntry {
    final String packageName, label; final boolean suspended, installed;
    AppEntry(String pkg, String label, boolean suspended, boolean installed) { packageName = pkg; this.label = label; this.suspended = suspended; this.installed = installed; }
}
