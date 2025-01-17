/*
 Copyright 2013 Sebastián Katzer

 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

package de.appplant.cordova.plugin.background;

  import android.annotation.SuppressLint;
  import android.annotation.TargetApi;
  import android.app.Activity;
  import android.app.ActivityManager;
  import android.app.ActivityManager.AppTask;
  import android.app.AlertDialog;
  import android.content.ComponentName;
  import android.content.Context;
  import android.content.Intent;
  import android.content.pm.PackageManager;
  import android.net.Uri;
  import android.os.Build;
  import android.os.PowerManager;
  import android.provider.Settings;
  import android.support.v4.app.NotificationManagerCompat;
  import android.view.View;
  import android.widget.Toast;

  import org.apache.cordova.CallbackContext;
  import org.apache.cordova.CordovaPlugin;
  import org.apache.cordova.PluginResult;
  import org.apache.cordova.PluginResult.Status;
  import org.json.JSONArray;
  import org.json.JSONObject;

  import java.util.Arrays;
  import java.util.HashMap;
  import java.util.List;
  import java.util.Map;
  import java.util.Set;

  import static android.R.string.cancel;
  import static android.R.string.ok;
  import static android.R.style.Theme_DeviceDefault_Light_Dialog;
  import static android.content.Context.ACTIVITY_SERVICE;
  import static android.content.Context.POWER_SERVICE;
  import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
  import static android.os.Build.VERSION.SDK_INT;
  import static android.os.Build.VERSION_CODES.M;
  import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
  import static android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS;
  import static android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
  import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
  import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
  import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

/**
 * Implements extended functions around the main purpose
 * of infinite execution in the background.
 */
public class BackgroundModeExt extends CordovaPlugin {

  // To keep the device awake
  private PowerManager.WakeLock wakeLock;

  /**
   * Executes the request.
   *
   * @param action   The action to execute.
   * @param args     The exec() arguments.
   * @param callback The callback context used when
   *                 calling back into JavaScript.
   *
   * @return Returning false results in a "MethodNotFound" error.
   */
  @Override
  public boolean execute (String action, JSONArray args,
                          CallbackContext callback)
  {
    boolean validAction = true;

    switch (action)
    {
      case "battery":
        disableBatteryOptimizations();
        break;
      case "batterysettings":
        openBatterySettings();
        break;
      case "optimizationstatus":
        isIgnoringBatteryOptimizations(callback);
        break;
      case "isOpenNotification":
        isOpenNotification(callback);
        break;
      case "openNotificationSettings":
        openNotificationSettings();
        break;
      case "webview":
        disableWebViewOptimizations();
        break;
      case "appstart":
//        openAppStart(args.opt(0));
        startToAutoStartSetting();
        break;
      case "background":
        moveToBackground();
        break;
      case "foreground":
        moveToForeground();
        break;
      case "requestTopPermissions":
        requestTopPermissions();
        break;
      case "tasklist":
        excludeFromTaskList();
        break;
      case "dimmed":
        isDimmed(callback);
        break;
      case "wakeup":
        wakeup();
        break;
      case "unlock":
        wakeup();
        unlock();
        break;
      default:
        validAction = false;
    }

    if (validAction) {
      callback.success();
    } else {
      callback.error("Invalid action: " + action);
    }

    return validAction;
  }

  /**
   * Moves the app to the background.
   */
  private void moveToBackground()
  {
    Intent intent = new Intent(Intent.ACTION_MAIN);

    intent.addCategory(Intent.CATEGORY_HOME);

    getApp().startActivity(intent);
  }

  /**
   * Moves the app to the foreground.
   */
  private void moveToForeground()
  {
    Activity  app = getApp();
    Intent intent = getLaunchIntent();

    intent.addFlags(
      Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
        Intent.FLAG_ACTIVITY_SINGLE_TOP |
        Intent.FLAG_ACTIVITY_CLEAR_TOP);

    clearScreenAndKeyguardFlags();
    app.startActivity(intent);
  }

  /**
   * Enable GPS position tracking while in background.
   */
  private void disableWebViewOptimizations() {
    Thread thread = new Thread(){
      public void run() {
        try {
          Thread.sleep(2000);
          getApp().runOnUiThread(() -> {
            View view = webView.getView();

            try {
              Class.forName("org.crosswalk.engine.XWalkCordovaView")
                .getMethod("onShow")
                .invoke(view);
            } catch (Exception e){
              view.dispatchWindowVisibilityChanged(View.VISIBLE);
            }
          });
        } catch (InterruptedException e) {
          // do nothing
        }
      }
    };

    thread.start();
  }

  /**
   * Disables battery optimizations for the app.
   * Requires permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to function.
   */
  @SuppressLint("BatteryLife")
  private void disableBatteryOptimizations()
  {
    Activity activity = cordova.getActivity();
    Intent intent     = new Intent();
    String pkgName    = activity.getPackageName();
    PowerManager pm   = (PowerManager)getService(POWER_SERVICE);

    if (SDK_INT < M)
      return;

    if (pm.isIgnoringBatteryOptimizations(pkgName))
      return;

    intent.setAction(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
    intent.setData(Uri.parse("package:" + pkgName));

    cordova.getActivity().startActivity(intent);
  }

  /**
   * Opens the Battery Optimization settings screen
   */
  private void openBatterySettings()
  {
    if (SDK_INT < M)
      return;

    Activity activity  = cordova.getActivity();
    String pkgName     = activity.getPackageName();
    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
    intent.setData(Uri.parse("package:"+pkgName));
    cordova.getActivity().startActivity(intent);
  }

  /**
   * Opens the Battery Optimization settings screen
   *
   * @param callback The callback to invoke.
   */
  private void isIgnoringBatteryOptimizations(CallbackContext callback)
  {
    if (SDK_INT < M)
      return;

    Activity activity  = cordova.getActivity();
    String pkgName     = activity.getPackageName();
    PowerManager pm    = (PowerManager)getService(POWER_SERVICE);
    boolean isIgnoring = pm.isIgnoringBatteryOptimizations(pkgName);
    PluginResult res   = new PluginResult(Status.OK, isIgnoring);

    callback.sendPluginResult(res);
  }

  private void requestTopPermissions() {
    if (SDK_INT >= M) {

      Activity activity = cordova.getActivity();
      if (Settings.canDrawOverlays(activity.getApplicationContext())) {
        return;
      }

      String pkgName    = activity.getPackageName();
      Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkgName));
      activity.startActivity(intent);
    }
  }

  /**
   * Opens the system settings dialog where the user can tweak or turn off any
   * custom app start settings added by the manufacturer if available.
   *
   * @param arg Text and title for the dialog or false to skip the dialog.
   */
  private void openAppStart (Object arg)
  {
    Activity activity = cordova.getActivity();
    PackageManager pm = activity.getPackageManager();

    for (Intent intent : getAppStartIntents())
    {
      if (pm.resolveActivity(intent, MATCH_DEFAULT_ONLY) != null)
      {
        JSONObject spec = (arg instanceof JSONObject) ? (JSONObject) arg : null;

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (arg instanceof Boolean && !((Boolean) arg))
        {
          activity.startActivity(intent);
          break;
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(activity, Theme_DeviceDefault_Light_Dialog);

        dialog.setPositiveButton(ok, (o, d) -> activity.startActivity(intent));
        dialog.setNegativeButton(cancel, (o, d) -> {});
        dialog.setCancelable(true);

        if (spec != null && spec.has("title"))
        {
          dialog.setTitle(spec.optString("title"));
        }

        if (spec != null && spec.has("text"))
        {
          dialog.setMessage(spec.optString("text"));
        }
        else
        {
          dialog.setMessage("missing text");
        }

        activity.runOnUiThread(dialog::show);

        break;
      }
    }
  }


  private void isOpenNotification (CallbackContext callback)
  {

    Activity activity  = cordova.getActivity();
    String pkgName     = activity.getPackageName();
    NotificationManagerCompat notification = NotificationManagerCompat.from(activity);
    boolean isEnabled = notification.areNotificationsEnabled();

    PluginResult res   = new PluginResult(Status.OK, isEnabled);

    callback.sendPluginResult(res);

  }

  private void openNotificationSettings ()
  {

    Intent intent = new Intent();
    Activity activity  = cordova.getActivity();
    String pkgName     = activity.getPackageName();
    Activity  app = getApp();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
      intent.putExtra("android.provider.extra.APP_PACKAGE", pkgName);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {  //5.0
      intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
      intent.putExtra("app_package", pkgName);
      intent.putExtra("app_uid", activity.getApplicationInfo().uid);
      app.startActivity(intent);
    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {  //4.4
      intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setData(Uri.parse("package:" + pkgName));
    } else if (Build.VERSION.SDK_INT >= 15) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
      intent.setData(Uri.fromParts("package", pkgName, null));
    }
    app.startActivity(intent);

  }

  /**
   * Excludes the app from the recent tasks list.
   */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void excludeFromTaskList()
  {
    ActivityManager am = (ActivityManager) getService(ACTIVITY_SERVICE);

    if (am == null || SDK_INT < 21)
      return;

    List<AppTask> tasks = am.getAppTasks();

    if (tasks == null || tasks.isEmpty())
      return;

    tasks.get(0).setExcludeFromRecents(true);
  }

  /**
   * Invokes the callback with information if the screen is on.
   *
   * @param callback The callback to invoke.
   */
  @SuppressWarnings("deprecation")
  private void isDimmed (CallbackContext callback)
  {
    boolean status   = isDimmed();
    PluginResult res = new PluginResult(Status.OK, status);

    callback.sendPluginResult(res);
  }

  /**
   * Returns if the screen is active.
   */
  @SuppressWarnings("deprecation")
  private boolean isDimmed()
  {
    PowerManager pm = (PowerManager) getService(POWER_SERVICE);

    if (SDK_INT < 20)
    {
      return !pm.isScreenOn();
    }

    return !pm.isInteractive();
  }

  /**
   * Wakes up the device if the screen isn't still on.
   */
  private void wakeup()
  {
    try {
      acquireWakeLock();
    } catch (Exception e) {
      releaseWakeLock();
    }
  }

  /**
   * Unlocks the device even with password protection.
   */
  private void unlock()
  {
    addSreenAndKeyguardFlags();
    getApp().startActivity(getLaunchIntent());
  }

  /**
   * Acquires a wake lock to wake up the device.
   */
  @SuppressWarnings("deprecation")
  private void acquireWakeLock()
  {
    PowerManager pm = (PowerManager) getService(POWER_SERVICE);

    releaseWakeLock();

    if (!isDimmed())
      return;

    int level = PowerManager.SCREEN_DIM_WAKE_LOCK |
      PowerManager.ACQUIRE_CAUSES_WAKEUP;

    wakeLock = pm.newWakeLock(level, "backgroundmode:wakelock");
    wakeLock.setReferenceCounted(false);
    wakeLock.acquire(1000);
  }

  /**
   * Releases the previously acquire wake lock.
   */
  private void releaseWakeLock()
  {
    if (wakeLock != null && wakeLock.isHeld()) {
      wakeLock.release();
      wakeLock = null;
    }
  }

  /**
   * Adds required flags to the window to unlock/wakeup the device.
   */
  private void addSreenAndKeyguardFlags()
  {
    getApp().runOnUiThread(() -> getApp().getWindow().addFlags(FLAG_ALLOW_LOCK_WHILE_SCREEN_ON | FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD));
  }

  /**
   * Clears required flags to the window to unlock/wakeup the device.
   */
  private void clearScreenAndKeyguardFlags()
  {
    getApp().runOnUiThread(() -> getApp().getWindow().clearFlags(FLAG_ALLOW_LOCK_WHILE_SCREEN_ON | FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD));
  }

  /**
   * Removes required flags to the window to unlock/wakeup the device.
   */
  static void clearKeyguardFlags (Activity app)
  {
    app.runOnUiThread(() -> app.getWindow().clearFlags(FLAG_DISMISS_KEYGUARD));
  }

  /**
   * Returns the activity referenced by cordova.
   */
  Activity getApp() {
    return cordova.getActivity();
  }

  /**
   * Gets the launch intent for the main activity.
   */
  private Intent getLaunchIntent()
  {
    Context app    = getApp().getApplicationContext();
    String pkgName = app.getPackageName();

    return app.getPackageManager().getLaunchIntentForPackage(pkgName);
  }

  /**
   * Get the requested system service by name.
   *
   * @param name The name of the service.
   */
  private Object getService(String name)
  {
    return getApp().getSystemService(name);
  }

  /**
   * Returns list of all possible intents to present the app start settings.
   */
  private List<Intent> getAppStartIntents()
  {
    return Arrays.asList(
      new Intent().setComponent(new ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity")),
      new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
      new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
      new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
      new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
      new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
      new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
      new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
      new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
      new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
      new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
      new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")).setData(android.net.Uri.parse("mobilemanager://function/entry/AutoStart")),
      new Intent().setAction("com.letv.android.permissionautoboot"),
      new Intent().setComponent(new ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity")),
      new Intent().setComponent(ComponentName.unflattenFromString("com.iqoo.secure/.MainActivity")),
      new Intent().setComponent(ComponentName.unflattenFromString("com.meizu.safe/.permission.SmartBGActivity")),
      new Intent().setComponent(new ComponentName("com.yulong.android.coolsafe", ".ui.activity.autorun.AutoRunListActivity")),
      new Intent().setComponent(new ComponentName("cn.nubia.security2", "cn.nubia.security.appmanage.selfstart.ui.SelfStartActivity")),
      new Intent().setComponent(new ComponentName("com.zui.safecenter", "com.lenovo.safecenter.MainTab.LeSafeMainActivity"))
    );
  }
  private static HashMap<String, List<String>> hashMap = new HashMap<String, List<String>>() {
    {
      put("Xiaomi", Arrays.asList(
        "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",//MIUI10_9.8.1(9.0)
        "com.miui.securitycenter"
      ));

      put("samsung", Arrays.asList(
        "com.samsung.android.sm_cn/com.samsung.android.sm.ui.ram.AutoRunActivity",
        "com.samsung.android.sm_cn/com.samsung.android.sm.ui.appmanagement.AppManagementActivity",
        "com.samsung.android.sm_cn/com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity",
        "com.samsung.android.sm_cn/.ui.ram.RamActivity",
        "com.samsung.android.sm_cn/.app.dashboard.SmartManagerDashBoardActivity",

        "com.samsung.android.sm/com.samsung.android.sm.ui.ram.AutoRunActivity",
        "com.samsung.android.sm/com.samsung.android.sm.ui.appmanagement.AppManagementActivity",
        "com.samsung.android.sm/com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity",
        "com.samsung.android.sm/.ui.ram.RamActivity",
        "com.samsung.android.sm/.app.dashboard.SmartManagerDashBoardActivity",

        "com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.samsung.android.sm_cn",
        "com.samsung.android.sm"
      ));


      put("HUAWEI", Arrays.asList(
        "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",//EMUI9.1.0(方舟,9.0)
        "com.huawei.systemmanager/.appcontrol.activity.StartupAppControlActivity",
        "com.huawei.systemmanager/.optimize.process.ProtectActivity",
        "com.huawei.systemmanager/.optimize.bootstart.BootStartActivity",
        "com.huawei.systemmanager"//最后一行可以写包名, 这样如果签名的类路径在某些新版本的ROM中没找到 就直接跳转到对应的安全中心/手机管家 首页.
      ));

      put("vivo", Arrays.asList(
        "com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager",
        "com.iqoo.secure/.safeguard.PurviewTabActivity",
        "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity",
//                    "com.iqoo.secure/.ui.phoneoptimize.AddWhiteListActivity", //这是白名单, 不是自启动
        "com.iqoo.secure",
        "com.vivo.permissionmanager"
      ));

      put("Meizu", Arrays.asList(
        "com.meizu.safe/.permission.SmartBGActivity",//Flyme7.3.0(7.1.2)
        "com.meizu.safe/.permission.PermissionMainActivity",//网上的
        "com.meizu.safe"
      ));

      put("OPPO", Arrays.asList(
        "com.coloros.safecenter/.startupapp.StartupAppListActivity",
        "com.coloros.safecenter/.permission.startup.StartupAppListActivity",
        "com.oppo.safe/.permission.startup.StartupAppListActivity",
        "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
        "com.coloros.safecenter/com.coloros.privacypermissionsentry.PermissionTopActivity",
        "com.coloros.safecenter",
        "com.oppo.safe",
        "com.coloros.oppoguardelf"
      ));

      put("oneplus", Arrays.asList(
        "com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity",
        "com.oneplus.security"
      ));
      put("letv", Arrays.asList(
        "com.letv.android.letvsafe/.AutobootManageActivity",
        "com.letv.android.letvsafe/.BackgroundAppManageActivity",//应用保护
        "com.letv.android.letvsafe"
      ));
      put("zte", Arrays.asList(
        "com.zte.heartyservice/.autorun.AppAutoRunManager",
        "com.zte.heartyservice"
      ));
      //金立
      put("F", Arrays.asList(
        "com.gionee.softmanager/.MainActivity",
        "com.gionee.softmanager"
      ));

      //以下为未确定(厂商名也不确定)
      put("smartisanos", Arrays.asList(
        "com.smartisanos.security/.invokeHistory.InvokeHistoryActivity",
        "com.smartisanos.security"
      ));
      //360
      put("360", Arrays.asList(
        "com.yulong.android.coolsafe/.ui.activity.autorun.AutoRunListActivity",
        "com.yulong.android.coolsafe"
      ));
      //360
      put("ulong", Arrays.asList(
        "com.yulong.android.coolsafe/.ui.activity.autorun.AutoRunListActivity",
        "com.yulong.android.coolsafe"
      ));
      //酷派
      put("coolpad"/*厂商名称不确定是否正确*/, Arrays.asList(
        "com.yulong.android.security/com.yulong.android.seccenter.tabbarmain",
        "com.yulong.android.security"
      ));
      //联想
      put("lenovo"/*厂商名称不确定是否正确*/, Arrays.asList(
        "com.lenovo.security/.purebackground.PureBackgroundActivity",
        "com.lenovo.security"
      ));
      put("htc"/*厂商名称不确定是否正确*/, Arrays.asList(
        "com.htc.pitroad/.landingpage.activity.LandingPageActivity",
        "com.htc.pitroad"
      ));
      //华硕
      put("asus"/*厂商名称不确定是否正确*/, Arrays.asList(
        "com.asus.mobilemanager/.MainActivity",
        "com.asus.mobilemanager"
      ));

    }
  };

  public void startToAutoStartSetting() {

    Activity context = cordova.getActivity();

    Set<Map.Entry<String, List<String>>> entries = hashMap.entrySet();
    boolean has = false;
    for (Map.Entry<String, List<String>> entry : entries) {
      String manufacturer = entry.getKey();
      List<String> actCompatList = entry.getValue();
      if (Build.MANUFACTURER.equalsIgnoreCase(manufacturer)) {
        for (String act : actCompatList) {
          try {
            Intent intent;
            if (act.contains("/")) {
              intent = new Intent();
              intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              ComponentName componentName = ComponentName.unflattenFromString(act);
              intent.setComponent(componentName);
            } else {
              //找不到? 网上的做法都是跳转到设置... 这基本上是没意义的 基本上自启动这个功能是第三方厂商自己写的安全管家类app
              //所以我是直接跳转到对应的安全管家/安全中心
              intent = context.getPackageManager().getLaunchIntentForPackage(act);
            }
            context.startActivity(intent);
            has = true;
            break;
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    }
    if (!has) {
      Toast.makeText(context, "兼容方案", Toast.LENGTH_SHORT).show();
      try {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        context.startActivity(intent);
      } catch (Exception e) {
        e.printStackTrace();
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
      }
    }

  }
}
