package com.local.appcontrol.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native, virtualized app list. A checkbox selects management; it never silently stops an app. */
public final class MainActivity extends Activity {
    private static final int BG = 0xff080e18, CARD = 0xff111b2a, ACCENT = 0xff38d4f2, TEXT = 0xffedf5ff, MUTED = 0xff9bacc1;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AppEngine.Listener listener = this::render;
    private AppEngine engine;
    private TextView environment, message, emptyHint;
    private Button root, alarms, restoreAll, managedTab, chooseTab, refresh;
    private AppAdapter adapter;
    private boolean choosing;
    private String filter = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() { if (adapter != null && engine.rules.stream().anyMatch(Rule::timed)) adapter.notifyDataSetChanged();
            ui.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); engine = AppEngine.get(this);
        if (saved != null) { choosing = saved.getBoolean("choosing"); filter = saved.getString("filter", ""); }
        build(); render();
    }
    private void build() {
        LinearLayout page = column(); page.setPadding(dp(16), dp(12), dp(16), dp(8)); page.setBackgroundColor(BG); setContentView(page);
        TextView kicker = text("LOCAL APP CONTROL", 11, ACCENT); page.addView(kicker);
        TextView title = text("应用管控 Demo", 26, TEXT); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); page.addView(title);
        TextView hint = text("独立 Root 工具 · 勾选即加入管控名单", 12, MUTED); page.addView(hint);
        environment = text("", 12, ACCENT); environment.setPadding(0, dp(8), 0, dp(4)); page.addView(environment);
        LinearLayout tools = new LinearLayout(this); page.addView(tools);
        root = button("检测 Root", false); root.setOnClickListener(v -> engine.checkRoot()); tools.addView(root, weight(48));
        alarms = button("倒计时权限", false); alarms.setOnClickListener(v -> openAlarmPermission()); tools.addView(alarms, weight(48));
        refresh = button("刷新", false); refresh.setOnClickListener(v -> engine.execute(ControlManager::reconcile, "已刷新系统管控状态")); tools.addView(refresh, weight(48));
        restoreAll = button("恢复全部受控应用", true); restoreAll.setOnClickListener(v -> engine.execute(ControlManager::restoreAll, "本Demo的暂停已全部解除"));
        LinearLayout.LayoutParams full = new LinearLayout.LayoutParams(-1, dp(48)); full.setMargins(0, dp(8), 0, dp(8)); page.addView(restoreAll, full);
        LinearLayout tabs = new LinearLayout(this); page.addView(tabs);
        managedTab = button("管控名单", false); chooseTab = button("选择应用", false);
        managedTab.setOnClickListener(v -> { choosing = false; render(); }); chooseTab.setOnClickListener(v -> { choosing = true; render(); });
        tabs.addView(managedTab, weight(48)); tabs.addView(chooseTab, weight(48));
        EditText search = new EditText(this); search.setSingleLine(true); search.setTextColor(TEXT); search.setTextSize(14);
        search.setHintTextColor(MUTED); search.setHint("搜索应用名称或包名"); search.setContentDescription("搜索已安装应用"); search.setText(filter);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { filter = s.toString(); renderList(); }
            public void afterTextChanged(Editable value) { }
        }); page.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));
        message = text("", 12, MUTED); message.setMinLines(2); message.setMaxLines(3);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); page.addView(message);
        emptyHint = text("", 14, MUTED); page.addView(emptyHint);
        ListView list = new ListView(this); list.setDividerHeight(dp(8)); list.setDivider(new android.graphics.drawable.ColorDrawable(BG));
        list.setClipToPadding(false); adapter = new AppAdapter(); list.setAdapter(adapter); page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = text("倒计时使用系统闹钟；普通退出仍生效。卸载/强行停止本Demo前，请先恢复全部。", 11, MUTED);
        footer.setPadding(0, dp(8), 0, dp(4)); page.addView(footer);
    }
    private void render() {
        environment.setText(getString(R.string.environment_state, engine.rootReady ? "● Root 已授权" : "○ Root 待检测", engine.alarm.allowed() ? "● 倒计时可用" : "○ 需闹钟权限"));
        message.setText(getString(R.string.operation_message, engine.busy ? "正在处理，请稍候…\n" : "", engine.message));
        root.setEnabled(!engine.busy && engine.loaded); refresh.setEnabled(!engine.busy && engine.loaded);
        restoreAll.setEnabled(!engine.busy && engine.rules.stream().anyMatch(rule -> rule.owned));
        managedTab.setText(getString(R.string.managed_tab, choosing ? "" : "● ", engine.rules.size()));
        chooseTab.setText(getString(R.string.choose_tab, choosing ? "● " : "", engine.apps.size()));
        renderList();
    }
    private void renderList() {
        if (adapter == null) return;
        String query = filter.trim().toLowerCase(Locale.ROOT);
        List<AppEntry> shown = new ArrayList<>(); Map<String, Rule> rules = rules();
        for (AppEntry app : engine.apps) if ((choosing || rules.containsKey(app.packageName))
                && (app.label.toLowerCase(Locale.ROOT).contains(query) || app.packageName.toLowerCase(Locale.ROOT).contains(query))) shown.add(app);
        adapter.values = shown; adapter.notifyDataSetChanged();
        emptyHint.setVisibility(shown.isEmpty() && !engine.busy && engine.loaded ? View.VISIBLE : View.GONE);
        emptyHint.setText(choosing ? "没有匹配的第三方应用；系统与关键管理应用不会列入。" : "名单为空：请点“选择应用”，勾选抖音或其他应用。");
    }
    private Map<String, Rule> rules() { Map<String, Rule> result = new HashMap<>(); for (Rule rule : engine.rules) result.put(rule.packageName, rule); return result; }
    private void openAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31 && !engine.alarm.allowed()) {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
            catch (Exception error) { new AlertDialog.Builder(this).setMessage("请到系统设置 → 应用 → 特殊应用权限 → 闹钟和提醒，允许本Demo。").setPositiveButton("知道了", null).show(); }
        } else Toast.makeText(this, "系统闹钟权限已满足", Toast.LENGTH_SHORT).show();
    }
    private void block(AppEntry app) {
        if (!engine.rootReady) { Toast.makeText(this, "请先检测并授权 Root", Toast.LENGTH_SHORT).show(); return; }
        LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), 0);
        TextView hint = text("停用后无法打开。倒计时结束自动恢复使用，不会自动启动应用。", 14, TEXT); form.addView(hint);
        RadioGroup mode = new RadioGroup(this); RadioButton timed = new RadioButton(this), manual = new RadioButton(this);
        timed.setId(View.generateViewId()); manual.setId(View.generateViewId()); timed.setText("倒计时后恢复"); manual.setText("手动恢复（不设倒计时）");
        timed.setMinHeight(dp(48)); manual.setMinHeight(dp(48)); mode.addView(timed); mode.addView(manual); timed.setChecked(true); form.addView(mode);
        LinearLayout duration = new LinearLayout(this); EditText amount = new EditText(this); amount.setText("5"); amount.setInputType(InputType.TYPE_CLASS_NUMBER); amount.setSingleLine();
        amount.setContentDescription("停用时长数值"); duration.addView(amount, new LinearLayout.LayoutParams(0, dp(56), 1));
        Spinner unit = new Spinner(this); unit.setContentDescription("停用时长单位");
        ArrayAdapter<String> units = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"秒", "分钟", "小时"});
        unit.setAdapter(units); unit.setSelection(1); duration.addView(unit, new LinearLayout.LayoutParams(dp(112), dp(56))); form.addView(duration);
        mode.setOnCheckedChangeListener((group, checked) -> { amount.setEnabled(checked == timed.getId()); unit.setEnabled(checked == timed.getId()); });
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("停用 · " + app.label).setView(scroll)
                .setNegativeButton("取消", null).setPositiveButton("确认停用", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            long delay = 0;
            if (timed.isChecked()) {
                if (!engine.alarm.allowed()) { openAlarmPermission(); return; }
                try {
                    long number = Long.parseLong(amount.getText().toString().trim());
                    long multiplier = new long[] {1000, 60_000, 3_600_000}[unit.getSelectedItemPosition()];
                    delay = Math.multiplyExact(number, multiplier);
                    if (number <= 0 || delay > ControlManager.MAX_DURATION) throw new ArithmeticException();
                } catch (Exception invalid) { amount.setError("请输入1秒到30天之间的时长"); return; }
            }
            final long chosen = delay;
            engine.execute(manager -> manager.block(app.packageName, chosen), app.label + (chosen == 0 ? "已停用，等待手动恢复" : "已停用，倒计时结束自动恢复")); dialog.dismiss();
        })); dialog.show();
    }
    private void launch(AppEntry app) {
        if (app.suspended) { Toast.makeText(this, "应用已停用，请先恢复使用", Toast.LENGTH_SHORT).show(); return; }
        Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (intent == null) { Toast.makeText(this, "该应用没有可打开的主界面", Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(intent); } catch (Exception blocked) { Toast.makeText(this, "暂时无法打开，请刷新系统状态", Toast.LENGTH_SHORT).show(); }
    }
    private final class AppAdapter extends BaseAdapter {
        List<AppEntry> values = Collections.emptyList();
        public int getCount() { return values.size(); } public Object getItem(int index) { return values.get(index); } public long getItemId(int index) { return index; }
        @Override public View getView(int index, View recycled, android.view.ViewGroup parent) {
            Row row;
            if (recycled == null) { row = new Row(); recycled = row.card; recycled.setTag(row); } else row = (Row) recycled.getTag();
            AppEntry app = values.get(index); Rule rule = rules().get(app.packageName);
            row.name.setText(app.label); row.pkg.setText(app.packageName);
            row.select.setOnCheckedChangeListener(null); row.select.setChecked(rule != null);
            row.select.setContentDescription("管控 " + app.label + "，勾选加入，取消前自动恢复"); row.select.setEnabled(!engine.busy && engine.loaded);
            row.select.setOnCheckedChangeListener((box, checked) -> engine.execute(m -> { if (checked) m.select(app.packageName); else m.remove(app.packageName); }, checked ? app.label + "已加入管控名单" : app.label + "已恢复并移出名单"));
            String state;
            if (!app.installed) state = "应用已卸载，可移出名单";
            else if (rule != null && rule.owned) state = app.suspended ? (rule.timed() ? "已停用 · " + countdown(rule.remaining(engine.now())) + " 后恢复" : "已停用 · 等待手动恢复") : "系统已开放 · 恢复记录待同步";
            else state = app.suspended ? "由其他来源暂停，Demo未接管" : "允许使用";
            if (rule != null && rule.error != null) state += "\n上次操作未确认：" + rule.error;
            row.state.setText(state); row.state.setTextColor(app.suspended ? 0xffffbe64 : ACCENT);
            row.actions.setVisibility(choosing ? View.GONE : View.VISIBLE);
            row.allow.setEnabled(!engine.busy && rule != null && rule.owned); row.block.setEnabled(!engine.busy && app.installed && engine.rootReady); row.open.setEnabled(!engine.busy && app.installed && !app.suspended);
            row.allow.setOnClickListener(v -> engine.execute(m -> m.allow(app.packageName), app.label + "已恢复使用")); row.block.setOnClickListener(v -> block(app)); row.open.setOnClickListener(v -> launch(app));
            row.allow.setContentDescription("恢复使用 " + app.label); row.block.setContentDescription("停用及设置倒计时 " + app.label); row.open.setContentDescription("打开 " + app.label);
            return recycled;
        }
    }
    private final class Row {
        final LinearLayout card = column(), actions = new LinearLayout(MainActivity.this);
        final TextView name = text("", 18, TEXT), pkg = text("", 11, MUTED), state = text("", 13, ACCENT);
        final CheckBox select = new CheckBox(MainActivity.this);
        final Button allow = button("恢复使用", false), block = button("停用…", true), open = button("打开", false);
        Row() {
            card.setPadding(dp(12), dp(10), dp(12), dp(12)); card.setBackground(background(CARD));
            LinearLayout heading = new LinearLayout(MainActivity.this); heading.setGravity(Gravity.CENTER_VERTICAL); card.addView(heading);
            heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1)); select.setButtonTintList(ColorStateList.valueOf(ACCENT)); heading.addView(select, new LinearLayout.LayoutParams(dp(48), dp(48)));
            card.addView(pkg); state.setPadding(0, dp(8), 0, dp(8)); card.addView(state); card.addView(actions);
            actions.addView(allow, weight(48)); actions.addView(block, weight(48)); actions.addView(open, weight(48));
        }
    }
    private static String countdown(long ms) { long seconds = (ms + 999) / 1000; return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60); }
    private LinearLayout column() { LinearLayout result = new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL); return result; }
    private TextView text(String value, int size, int color) { TextView result = new TextView(this); result.setText(value); result.setTextSize(size); result.setTextColor(color); return result; }
    private Button button(String label, boolean primary) {
        Button result = new Button(this); result.setText(label); result.setAllCaps(false); result.setTextSize(13); result.setTextColor(primary ? BG : TEXT);
        result.setBackgroundTintList(null); result.setBackground(background(primary ? ACCENT : CARD)); result.setPadding(dp(8), 0, dp(8), 0); result.setMinHeight(dp(48)); result.setMinimumWidth(dp(48)); result.setContentDescription(label); return result;
    }
    private GradientDrawable background(int color) { GradientDrawable result = new GradientDrawable(); result.setColor(color); result.setCornerRadius(dp(12)); result.setStroke(dp(1), 0xff27374b); return result; }
    private LinearLayout.LayoutParams weight(int height) { LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(0, dp(height), 1); result.setMargins(dp(3), 0, dp(3), 0); return result; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onStart() { super.onStart(); engine.attach(listener); engine.execute(ControlManager::reconcile, null); ui.post(tick); render(); }
    @Override protected void onStop() { engine.detach(listener); ui.removeCallbacksAndMessages(null); super.onStop(); }
    @Override public void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putBoolean("choosing", choosing); state.putString("filter", filter); }
}
