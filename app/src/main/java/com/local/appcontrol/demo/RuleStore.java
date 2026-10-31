package com.local.appcontrol.demo;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Private transactional file; a corrupt recovery ledger is reported, never silently reset. */
final class RuleStore implements ControlManager.Store {
    private final AtomicFile file;
    RuleStore(Context context) { file = new AtomicFile(new File(context.getFilesDir(), "control-rules.json")); }
    @Override public List<Rule> load() throws Exception {
        byte[] bytes;
        try (InputStream input = file.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 1024 * 1024) throw new IOException("管控记录过大");
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        } catch (FileNotFoundException missing) {
            if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()) throw missing;
            return Collections.emptyList();
        }
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (root.getInt("schema") != 1) throw new IOException("不支持的管控记录版本");
        JSONArray list = root.getJSONArray("rules");
        if (list.length() > 500) throw new IOException("最多支持500个受控应用");
        List<Rule> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject value = list.getJSONObject(i); String pkg = value.getString("package");
            PackagePolicy.requireValid(pkg); if (!seen.add(pkg)) throw new IOException("重复的应用记录");
            long wall = value.getLong("wall"), elapsed = value.getLong("elapsed"); int boot = value.getInt("boot");
            if (wall < 0 || elapsed < 0 || wall > Long.MAX_VALUE / 2 || elapsed > Long.MAX_VALUE / 2 || boot < 0)
                throw new IOException("无效的倒计时记录");
            String error = value.isNull("error") ? null : value.getString("error");
            result.add(new Rule(pkg, value.getBoolean("owned"), wall, elapsed, boot, error));
        }
        return result;
    }
    @Override public void save(List<Rule> values) throws Exception {
        if (values.size() > 500) throw new IOException("最多支持500个受控应用");
        JSONArray array = new JSONArray();
        for (Rule rule : values) array.put(new JSONObject().put("package", rule.packageName).put("owned", rule.owned)
                .put("wall", rule.wallDeadline).put("elapsed", rule.elapsedDeadline).put("boot", rule.boot)
                .put("error", rule.error == null ? JSONObject.NULL : rule.error));
        byte[] bytes = new JSONObject().put("schema", 1).put("rules", array).toString(2).getBytes(StandardCharsets.UTF_8);
        FileOutputStream output = null;
        try { output = file.startWrite(); output.write(bytes); file.finishWrite(output); }
        catch (Exception failure) { if (output != null) file.failWrite(output); throw failure; }
    }
}
