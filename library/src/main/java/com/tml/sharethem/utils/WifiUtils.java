package com.tml.sharethem.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;


/**
 * Created by Sri on 18/12/16.
 */

public class WifiUtils {

    public static final String SENDER_WIFI_NAMING_SALT = "stl";
    private static final String TAG = "WifiUtils";

    public static boolean isConnectToHotSpotRunning = false;

    /*
 *  Max priority of network to be associated.
 */
    private static final int MAX_PRIORITY = 999999;

    /**
     * Method for Connecting  to WiFi Network (hotspot)
     *
     * @param netSSID       of WiFi Network (hotspot)
     * @param disableOthers
     */
    public static boolean connectToOpenHotspot(WifiManager mWifiManager, String netSSID, boolean disableOthers) {

        isConnectToHotSpotRunning = true;
        WifiConfiguration wifiConf = new WifiConfiguration();
        if (mWifiManager.isWifiEnabled()) {
            wifiConf.SSID = "\"" + netSSID + "\"";
            wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            int res = mWifiManager.addNetwork(wifiConf);
            Log.d(TAG, "added network id: " + res);
            mWifiManager.disconnect();
            if (disableOthers)
                enableNetworkAndDisableOthers(mWifiManager, netSSID);
            else
                enableShareThemNetwork(mWifiManager, netSSID);
            isConnectToHotSpotRunning = false;
            return mWifiManager.setWifiEnabled(true);
        }
        isConnectToHotSpotRunning = false;
        return false;
    }

    public static boolean enableNetworkAndDisableOthers(WifiManager wifiManager, String ssid) {
        boolean state = false;
        List<WifiConfiguration> networks = wifiManager.getConfiguredNetworks();
        Iterator<WifiConfiguration> iterator = networks.iterator();
        while (iterator.hasNext()) {
            WifiConfiguration wifiConfig = iterator.next();
            if (wifiConfig.SSID.equals("\"" + ssid + "\""))
                state = wifiManager.enableNetwork(wifiConfig.networkId, true);
            else
                wifiManager.disableNetwork(wifiConfig.networkId);
        }
        Log.d(TAG, "enableShareThemHotspot wifi result: " + state);
        wifiManager.reconnect();
        return state;
    }

    public static boolean removeSTWifiAndEnableOthers(WifiManager wifiManager, String ssid) {
        boolean state = false;
        List<WifiConfiguration> networks = wifiManager.getConfiguredNetworks();
        Iterator<WifiConfiguration> iterator = networks.iterator();
        while (iterator.hasNext()) {
            WifiConfiguration wifiConfig = iterator.next();
            if (wifiConfig.SSID.equals("\"" + ssid + "\"")) {
                wifiManager.removeNetwork(wifiConfig.networkId);
                wifiManager.disableNetwork(wifiConfig.networkId);
            } else
                // if targetSdkVersion > 20
                //      If an application's target SDK version is LOLLIPOP or newer, network communication may not use Wi-Fi even if Wi-Fi is connected;
                //      For more info: https://developer.android.com/reference/android/net/wifi/WifiManager.html#enableNetwork(int, boolean)
                state = wifiManager.enableNetwork(wifiConfig.networkId, true);
        }
        wifiManager.saveConfiguration();
        wifiManager.reconnect();
        return state;
    }

    public static boolean enableShareThemNetwork(WifiManager wifiManager, String ssid) {
        boolean state = false;
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();

        if (list != null && list.size() > 0) {
            for (WifiConfiguration i : list) {
                if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                    int newPri = getMaxPriority(wifiManager) + 1;
                    if (newPri >= MAX_PRIORITY) {
                        // We have reached a rare situation.
                        newPri = shiftPriorityAndSave(wifiManager);
                    }
                    i.priority = newPri;
                    wifiManager.updateNetwork(i);
                    wifiManager.saveConfiguration();
                    // if targetSdkVersion > 20
                    //      If an application's target SDK version is LOLLIPOP or newer, network communication may not use Wi-Fi even if Wi-Fi is connected;
                    //      For more info: https://developer.android.com/reference/android/net/wifi/WifiManager.html#enableNetwork(int, boolean)
                    state = wifiManager.enableNetwork(i.networkId, true);
                    wifiManager.reconnect();
                    break;
                }
            }
        }

        return state;
    }

    /**
     * removes Configured wifi Network By SSID
     *
     * @param ssid of wifi Network
     */
    public static void removeWifiNetwork(WifiManager wifiManager, String ssid) {
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (config.SSID.contains(ssid)) {
                    wifiManager.disableNetwork(config.networkId);
                    wifiManager.removeNetwork(config.networkId);
                    Log.d(TAG, "removed wifi network with ssid: " + ssid);
                }
            }
        }
        wifiManager.saveConfiguration();
    }

    private static int getMaxPriority(WifiManager wifiManager) {
        final List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        int pri = 0;
        for (final WifiConfiguration config : configurations) {
            if (config.priority > pri) {
                pri = config.priority;
            }
        }
        return pri;
    }

    private static void sortByPriority(final List<WifiConfiguration> configurations) {
        Collections.sort(configurations,
                new Comparator<WifiConfiguration>() {
                    @Override
                    public int compare(WifiConfiguration object1, WifiConfiguration object2) {
                        return object1.priority - object2.priority;
                    }
                });
    }

    private static int shiftPriorityAndSave(WifiManager wifiManager) {
        final List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        sortByPriority(configurations);
        final int size = configurations.size();
        for (int i = 0; i < size; i++) {
            final WifiConfiguration config = configurations.get(i);
            config.priority = i;
            wifiManager.updateNetwork(config);
        }
        wifiManager.saveConfiguration();
        return size;
    }

    /**
     * Method to Get Network Security Mode
     *
     * @param scanResult
     * @return OPEN PSK EAP OR WEP
     */
    public static String getSecurityMode(ScanResult scanResult) {
        final String cap = scanResult.capabilities;
        final String[] modes = {"WPA", "EAP", "WEP"};
        for (int i = modes.length - 1; i >= 0; i--) {
            if (cap.contains(modes[i])) {
                return modes[i];
            }
        }
        return "OPEN";
    }

    public static boolean isWifiConnectedToSTAccessPoint(Context context) {
        return isConnectedOnWifi(context, true) && isShareThemSSID(((WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getSSID());
    }

    public static boolean isShareThemSSID(String ssid) {
        Log.d(TAG, "is this ssid mathcing to ST hotspot: " + ssid);
        String[] splits = ssid.split("-");
        if (splits.length != 2)
            return false;
        try {
            String[] names = new String(Base64.decode(splits[1], Base64.DEFAULT)).split("\\|");
            return names.length == 3 && names[1].equals(SENDER_WIFI_NAMING_SALT);
        } catch(Exception e){
            return false;
        }
    }

    public static int getSenderSocketPortFromSSID(String ssid) {
        String[] splits = ssid.split("-");
        if (splits.length != 2)
            return -1;
        String[] names = new String(Base64.decode(splits[1], Base64.DEFAULT)).split("\\|");
        if (names.length == 3 && names[1].equals(SENDER_WIFI_NAMING_SALT))
            return Integer.parseInt(names[2]);
        return -1;
    }

    public static String[] getSenderInfoFromSSID(String ssid) {
        String[] splits = ssid.split("-");
        if (splits.length != 2)
            return null;
        String[] names = new String(Base64.decode(splits[1], Base64.DEFAULT)).split("\\|");
        if (names.length == 3 && names[1].equals(SENDER_WIFI_NAMING_SALT))
            return new String[]{names[0], names[2]};
        return null;
    }

    public static String getThisDeviceIp(Context context) {
        WifiManager wifiMan = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        int ipAddress = wifiInf.getIpAddress();
        return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));

    }

    /**
     * Check if there is any connectivity to a Wifi network
     *
     * @param context
     * @param includeConnectingStatus
     * @return
     */
    public static boolean isConnectedOnWifi(Context context, boolean includeConnectingStatus) {
        if (Build.VERSION.SDK_INT >= 21) {
            boolean isWifiConnected = false;
            ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService
                    (Context.CONNECTIVITY_SERVICE));
            Network[] networks = connectivityManager.getAllNetworks();
            if (networks == null) {
                isWifiConnected = false;
            } else {
                for (Network network : networks) {
                    NetworkInfo info = connectivityManager.getNetworkInfo(network);
                    if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI && info.isAvailable() && (!includeConnectingStatus || info.isConnectedOrConnecting())) {
                        isWifiConnected = true;
                        break;
                    }
                }
            }
            return isWifiConnected;
        } else {
            NetworkInfo info = getNetworkInfo(context);
            return (info != null && info.isConnectedOrConnecting() && info.getType() == ConnectivityManager.TYPE_WIFI);
        }
    }

    /**
     * Get the network info
     *
     * @param context
     * @return
     */
    public static NetworkInfo getNetworkInfo(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo();
    }

    public static String getAccessPointIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        byte[] ipAddress = convert2Bytes(dhcpInfo.serverAddress);
        try {
            String ip = InetAddress.getByAddress(ipAddress).getHostAddress();
            return ip.replace("/", "");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] convert2Bytes(int hostAddress) {
        byte[] addressBytes = {(byte) (0xff & hostAddress),
                (byte) (0xff & (hostAddress >> 8)),
                (byte) (0xff & (hostAddress >> 16)),
                (byte) (0xff & (hostAddress >> 24))};
        return addressBytes;
    }

    public static String getHostIpAddress() {
        try {
            for (final Enumeration<NetworkInterface> enumerationNetworkInterface = NetworkInterface.getNetworkInterfaces(); enumerationNetworkInterface.hasMoreElements(); ) {
                final NetworkInterface networkInterface = enumerationNetworkInterface.nextElement();
                for (Enumeration<InetAddress> enumerationInetAddress = networkInterface.getInetAddresses(); enumerationInetAddress.hasMoreElements(); ) {
                    final InetAddress inetAddress = enumerationInetAddress.nextElement();
                    final String ipAddress = inetAddress.getHostAddress();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return ipAddress;
                    }
                }
            }
            return null;
        } catch (final Exception e) {
            Log.e("WifiUtils", "exception in fetching inet address: " + e.getMessage());
            return null;
        }
    }

    public static int inetAddressToInt(InetAddress inetAddr)
            throws IllegalArgumentException {
        byte[] addr = inetAddr.getAddress();
        if (addr.length != 4) {
            throw new IllegalArgumentException("Not an IPv4 address");
        }
        return ((addr[3] & 0xff) << 24) | ((addr[2] & 0xff) << 16) |
                ((addr[1] & 0xff) << 8) | (addr[0] & 0xff);
    }

    public static boolean isOpenWifi(ScanResult result) {
        return !result.capabilities.contains("WEP") && !result.capabilities.contains("PSK") && !result.capabilities.contains("EAP");
    }

    static String getDeviceName(WifiManager wifiManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "For version >= MM inaccessible mac - falling back to the default device name: " + HotspotControl.DEFAULT_DEVICE_NAME);
            return HotspotControl.DEFAULT_DEVICE_NAME;
        }
        String macString = wifiManager.getConnectionInfo().getMacAddress();
        if (macString == null) {
            Log.d(TAG, "MAC Address not found - Wi-Fi disabled? Falling back to the default device name: " + HotspotControl.DEFAULT_DEVICE_NAME);
            return HotspotControl.DEFAULT_DEVICE_NAME;
        }
        byte[] macBytes = Utils.macAddressToByteArray(macString);

        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();

                byte[] hardwareAddress = iface.getHardwareAddress();
                if (hardwareAddress != null && Arrays.equals(macBytes, hardwareAddress)) {
                    return iface.getName();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "exception in retrieving device name: " + e.getMessage());
        }

        Log.w(TAG, "None found - falling back to the default device name: " + HotspotControl.DEFAULT_DEVICE_NAME);
        return HotspotControl.DEFAULT_DEVICE_NAME;
    }
}
