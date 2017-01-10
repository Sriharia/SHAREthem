package com.tml.sharethem.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.provider.Settings;

import com.tml.sharethem.sender.SHAREthemService;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Random;

/**
 * Created by Sri on 18/12/16.
 */

public class Utils {

    public static boolean isShareServiceRunning(Context ctx) {
        ActivityManager manager = (ActivityManager) ctx
                .getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager
                .getRunningServices(Integer.MAX_VALUE)) {
            if (SHAREthemService.class.getCanonicalName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public final static long ONE_SECOND = 1000;
    public final static long ONE_MINUTE = ONE_SECOND * 60;
    public final static long ONE_HOUR = ONE_MINUTE * 60;
    public final static long ONE_DAY = ONE_HOUR * 24;

    /**
     * converts time (in milliseconds) to human-readable format
     * "<w> days, <x> hours, <y> minutes and (z) seconds"
     */
    public static String millisToLongDHMS(long duration) {
        StringBuffer res = new StringBuffer();
        boolean isDay = false, isHr = false, isMin = false;
        long temp = 0;
        if (duration >= ONE_SECOND) {
            temp = duration / ONE_DAY;
            if (temp > 0) {
                isDay = true;
                duration -= temp * ONE_DAY;
                res.append(temp >= 10 ? temp : "0" + temp).append("d ");
            }
            temp = duration / ONE_HOUR;
            if (temp > 0) {
                isHr = true;
                duration -= temp * ONE_HOUR;
                res.append(temp >= 10 ? temp : "0" + temp).append("h ");
            }
            if (isDay)
                return res.toString() + ((temp > 0) ? "" : "00h");
            temp = duration / ONE_MINUTE;
            if (temp > 0) {
                isMin = true;
                duration -= temp * ONE_MINUTE;
                res.append(temp >= 10 ? temp : "0" + temp).append("m ");
            }
            if (isHr)
                return res.toString() + ((temp > 0) ? "" : "00m");

            temp = duration / ONE_SECOND;
            if (temp > 0) {
                res.append(temp >= 10 ? temp : "0" + temp).append("s");
            }
            return res.toString() + ((temp > 0) ? "" : "00s");
        } else {
            return "0s";
        }
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = !si ? 1000 : 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1)
                + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static int getRandomColor() {
        Random rnd = new Random();
        return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
    }

    public static String[] toStringArray(String jArray) {
        if (jArray == null)
            return null;
        try {
            JSONArray array = new JSONArray(jArray);
            String[] arr = new String[array.length()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = array.optString(i);
            }
            return arr;
        } catch (JSONException jse) {
            return null;
        }
    }

    public static int getTargetSDKVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.applicationInfo.targetSdkVersion;
        } catch (PackageManager.NameNotFoundException nnf) {
            return -1;
        }
    }

    public static boolean isMobileDataEnabled(Context context) {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 ?
                Settings.Global.getInt(context.getContentResolver(), "mobile_data", 1) == 1 :
                Settings.Secure.getInt(context.getContentResolver(), "mobile_data", 1) == 1;
    }

    public static byte[] macAddressToByteArray(String macString) {
        String[] mac = macString.split("[:\\s-]");
        byte[] macAddress = new byte[6];
        for (int i = 0; i < mac.length; i++) {
            macAddress[i] = Integer.decode("0x" + mac[i]).byteValue();
        }
        return macAddress;
    }
}
