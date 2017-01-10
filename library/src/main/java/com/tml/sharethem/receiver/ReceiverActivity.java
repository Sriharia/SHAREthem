/*
 * Copyright 2017 Srihari Yachamaneni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tml.sharethem.receiver;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.tml.sharethem.R;
import com.tml.sharethem.utils.Utils;
import com.tml.sharethem.utils.WifiUtils;

import java.lang.ref.WeakReference;
import java.util.List;

import static com.tml.sharethem.receiver.ReceiverActivity.WifiTasksHandler.SCAN_FOR_WIFI_RESULTS;
import static com.tml.sharethem.receiver.ReceiverActivity.WifiTasksHandler.WAIT_FOR_CONNECT_ACTION_TIMEOUT;
import static com.tml.sharethem.receiver.ReceiverActivity.WifiTasksHandler.WAIT_FOR_RECONNECT_ACTION_TIMEOUT;
import static com.tml.sharethem.utils.WifiUtils.connectToOpenHotspot;

/**
 * Controls
 */
public class ReceiverActivity extends AppCompatActivity {

    public static final String TAG = "ReceiverActivity";

    TextView m_p2p_connection_status;
    SwitchCompat m_receiver_control;
    TextView m_goto_wifi_settings;
    TextView m_sender_files_header;

    private WifiManager wifiManager;
    private SharedPreferences preferences;

    private CompoundButton.OnCheckedChangeListener m_receiver_control_switch_listener;

    private WifiScanner mWifiScanReceiver;
    private WifiScanner mNwChangesReceiver;
    private WifiTasksHandler m_wifiScanHandler;
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 100;
    private String mConnectedSSID;

    private boolean m_areOtherNWsDisabled = false;
    Toolbar m_toolbar;

    private static String TAG_SENDER_FILES_LISTING = "sender_files_listing";

    private static final Long SYNCTIME = 800L;
    private static final String LASTCONNECTEDTIME = "LASTCONNECTEDTIME";
    private static final String LASTDISCONNECTEDTIME = "LASTDISCONNECTEDTIME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);
        if (Utils.isShareServiceRunning(getApplication())) {
            Toast.makeText(this, "Share mode is active, stop Share service to proceed with Receiving files", Toast.LENGTH_SHORT).show();
            return;
        }

        m_p2p_connection_status = (TextView) findViewById(R.id.p2p_receiver_wifi_info);
        m_goto_wifi_settings = (TextView) findViewById(R.id.p2p_receiver_wifi_switch);
        m_sender_files_header = (TextView) findViewById(R.id.p2p_sender_files_header);
        m_receiver_control = (SwitchCompat) findViewById(R.id.p2p_receiver_ap_switch);

        m_goto_wifi_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gotoWifiSettings();
            }
        });

        m_toolbar = (Toolbar) findViewById(R.id.toolbar);
        m_toolbar.setTitle(getString(R.string.send_title));
        setSupportActionBar(m_toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        m_wifiScanHandler = new WifiTasksHandler(this);
        m_receiver_control_switch_listener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (!startSenderScan())
                        changeReceiverControlCheckedStatus(false);
                } else {
                    changeReceiverControlCheckedStatus(true);
                    showOptionsDialogWithListners(getString(R.string.p2p_receiver_close_warning), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            changeReceiverControlCheckedStatus(false);
                            disableReceiverMode();
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }, getString(R.string.Action_Ok), getString(R.string.Action_cancel));
                }
            }
        };
        m_receiver_control.setOnCheckedChangeListener(m_receiver_control_switch_listener);
        if (!wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);
        //start search by default
        m_receiver_control.setChecked(true);
    }

    @TargetApi(23)
    private boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION
            );
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (checkLocationAccess())
                        startSenderScan();
                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        showOptionsDialogWithListners(getString(R.string.p2p_receiver_gps_permission_warning), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkLocationPermission();
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                finish();
                            }
                        }, "Re-Try", "Yes, Im Sure");
                    } else {
                        showOptionsDialogWithListners(getString(R.string.p2p_receiver_gps_no_permission_prompt), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivity(intent);
                                } catch (ActivityNotFoundException anf) {
                                    Toast.makeText(getApplicationContext(), "Settings activity not found", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        }, getString(R.string.label_settings), getString(R.string.Action_cancel));
                    }
                }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean isConnectedToShareThemAp = WifiUtils.isWifiConnectedToSTAccessPoint(getApplicationContext());
        if (isConnectedToShareThemAp) {
            unRegisterForScanResults();
            if (!m_receiver_control.isChecked())
                changeReceiverControlCheckedStatus(true);
            String ssid = wifiManager.getConnectionInfo().getSSID();
            Log.d(TAG, "wifi is connected/connecting to ShareThem ap, ssid: " + ssid);
            mConnectedSSID = ssid;
            addSenderFilesListingFragment(WifiUtils.getAccessPointIpAddress(this), ssid);
        } else if (m_receiver_control.isChecked()) {
            Log.d(TAG, "wifi isn't connected to ShareThem ap, initiating sender search..");
            resetSenderSearch();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unRegisterForScanResults();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unRegisterForNwChanges();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Entry point to start receiver mode. Makes calls to register necessary broadcast receivers to start scanning for SHAREthem Wifi Hotspot.
     *
     * @return
     */
    private boolean startSenderScan() {
        if (Utils.getTargetSDKVersion(getApplicationContext()) >= 23
                &&
                // if targetSdkVersion >= 23
                //      Get Wifi Scan results method needs GPS to be ON and COARSE location permission
                !checkLocationPermission())
            return false;
        changeReceiverControlCheckedStatus(true);
        registerAndScanForWifiResults();
        registerForNwChanges();
        return true;
    }

    /**
     * Disables and removes SHAREthem wifi configuration from Wifi Settings. Also does cleanup work to remove handlers, un-register receivers etc..
     */
    private void disableReceiverMode() {
        if (!TextUtils.isEmpty(mConnectedSSID)) {
            if (m_areOtherNWsDisabled)
                WifiUtils.removeSTWifiAndEnableOthers(wifiManager, mConnectedSSID);
            else
                WifiUtils.removeWifiNetwork(wifiManager, mConnectedSSID);
        }
        m_wifiScanHandler.removeMessages(WAIT_FOR_CONNECT_ACTION_TIMEOUT);
        m_wifiScanHandler.removeMessages(WAIT_FOR_RECONNECT_ACTION_TIMEOUT);
        unRegisterForScanResults();
        unRegisterForNwChanges();
        removeSenderFilesListingFragmentIfExists();
    }

    public void showOptionsDialogWithListners(String message,
                                              DialogInterface.OnClickListener pListner,
                                              DialogInterface.OnClickListener nListener, String pButtonText,
                                              String nButtonText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setCancelable(false);
        builder.setMessage(Html.fromHtml(message));
        builder.setPositiveButton(pButtonText, pListner);
        builder.setNegativeButton(nButtonText, null != nListener ? nListener
                : new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                return;
            }
        });
        builder.show();
    }

    /**
     * Changes checked status without invoking listener. Removes @{@link android.widget.CompoundButton.OnCheckedChangeListener} on @{@link SwitchCompat} button before changing checked status
     *
     * @param checked if <code>true</code>, sets @{@link SwitchCompat} checked.
     */
    private void changeReceiverControlCheckedStatus(boolean checked) {
        m_receiver_control.setOnCheckedChangeListener(null);
        m_receiver_control.setChecked(checked);
        m_receiver_control.setOnCheckedChangeListener(m_receiver_control_switch_listener);
    }

    /**
     * Registers for {@link WifiManager#SCAN_RESULTS_AVAILABLE_ACTION} action and also calls a method to start Wifi Scan action.
     */
    private void registerAndScanForWifiResults() {
        if (null == mWifiScanReceiver)
            mWifiScanReceiver = new WifiScanner();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mWifiScanReceiver, intentFilter);
        m_p2p_connection_status.setText(getString(R.string.p2p_receiver_scanning_hint));
        startWifiScan();
    }

    /**
     * Registers for {@link WifiManager#NETWORK_STATE_CHANGED_ACTION} action
     */
    private void registerForNwChanges() {
        if (null == mNwChangesReceiver)
            mNwChangesReceiver = new WifiScanner();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mNwChangesReceiver, intentFilter);
    }

    private void unRegisterForScanResults() {
        stopWifiScan();
        try {
            if (null != mWifiScanReceiver)
                unregisterReceiver(mWifiScanReceiver);
        } catch (Exception e) {
            Log.e(TAG, "exception while un-registering wifi changes.." + e.getMessage());
        }
    }

    private void unRegisterForNwChanges() {
        try {
            if (null != mNwChangesReceiver)
                unregisterReceiver(mNwChangesReceiver);
        } catch (Exception e) {
            Log.e(TAG, "exception while un-registering NW changes.." + e.getMessage());
        }
    }

    private void startWifiScan() {
        m_wifiScanHandler.removeMessages(SCAN_FOR_WIFI_RESULTS);
        m_wifiScanHandler.sendMessageDelayed(m_wifiScanHandler.obtainMessage(SCAN_FOR_WIFI_RESULTS), 500);
    }

    private void stopWifiScan() {
        if (null != m_wifiScanHandler)
            m_wifiScanHandler.removeMessages(SCAN_FOR_WIFI_RESULTS);
    }

    private boolean checkLocationAccess() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.e(TAG, "GPS not enabled..");
            buildAlertMessageNoGps();
            return false;
        }
        return true;
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, Please enabled it to proceed with p2p movie sharing")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                        finish();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();

    }

    public void resetSenderSearch() {
        removeSenderFilesListingFragmentIfExists();
        startSenderScan();
    }

    private void connectToWifi(String ssid) {
        WifiInfo info = wifiManager.getConnectionInfo();
        unRegisterForScanResults();
        boolean resetWifiScan;
        if (info.getSSID().equals(ssid)) {
            Log.d(TAG, "Already connected to ShareThem, add sender Files listing fragment");
            resetWifiScan = false;
            addSenderFilesListingFragment(WifiUtils.getAccessPointIpAddress(getApplicationContext()), ssid);
        } else {
            m_p2p_connection_status.setText(getString(R.string.p2p_receiver_connecting_hint, ssid));
            resetWifiScan = !connectToOpenHotspot(wifiManager, ssid, false);
            Log.e(TAG, "connection attempt to ShareThem wifi is " + (!resetWifiScan ? "success!!!" : "FAILED..!!!"));
        }
        //if wap isnt successful, start wifi scan
        if (resetWifiScan) {
            Toast.makeText(this, getString(R.string.p2p_receiver_error_in_connecting, ssid), Toast.LENGTH_SHORT).show();
            m_p2p_connection_status.setText(getString(R.string.p2p_receiver_scanning_hint));
            startSenderScan();
        } else {
            Message message = m_wifiScanHandler.obtainMessage(WAIT_FOR_CONNECT_ACTION_TIMEOUT);
            message.obj = ssid;
            m_wifiScanHandler.sendMessageDelayed(message, 7000);
        }
    }

    private class WifiScanner extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) && !WifiUtils.isWifiConnectedToSTAccessPoint(getApplicationContext())) {
                List<ScanResult> mScanResults = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getScanResults();
                boolean foundSTWifi = false;
                for (ScanResult result : mScanResults)
                    if (WifiUtils.isShareThemSSID(result.SSID) && WifiUtils.isOpenWifi(result)) {
                        Log.d(TAG, "signal level: " + result.level);
                        connectToWifi(result.SSID);
                        foundSTWifi = true;
                        break;
                    }
                if (!foundSTWifi) {
                    Log.e(TAG, "no ST wifi found, starting scan again!!");
                    startWifiScan();
                }
            } else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (ConnectivityManager.TYPE_WIFI == netInfo.getType()) {
                    WifiInfo info = wifiManager.getConnectionInfo();
                    SupplicantState supState = info.getSupplicantState();
                    Log.d(TAG, "NETWORK_STATE_CHANGED_ACTION, ssid: " + info.getSSID() + ", ap ip: " + WifiUtils.getAccessPointIpAddress(getApplicationContext()) + ", sup state: " + supState);
                    if (null == preferences)
                        preferences = getSharedPreferences(
                                getPackageName(), Context.MODE_PRIVATE);
                    if (WifiUtils.isShareThemSSID(info.getSSID())) {
                        if (System.currentTimeMillis() - preferences.getLong(LASTCONNECTEDTIME, 0) >= SYNCTIME && supState.equals(SupplicantState.COMPLETED)) {
                            mConnectedSSID = info.getSSID();
                            m_wifiScanHandler.removeMessages(WAIT_FOR_CONNECT_ACTION_TIMEOUT);
                            m_wifiScanHandler.removeMessages(WAIT_FOR_RECONNECT_ACTION_TIMEOUT);
                            final String ip = WifiUtils.getAccessPointIpAddress(getApplicationContext());
                            preferences.edit().putLong(LASTCONNECTEDTIME, System.currentTimeMillis()).commit();
                            Log.d(TAG, "client connected to ShareThem hot spot. AP ip address: " + ip);
                            addSenderFilesListingFragment(ip, info.getSSID());
                        }
//                        else if (!netInfo.isConnectedOrConnecting() && System.currentTimeMillis() - Prefs.getInstance().loadLong(LASTDISCONNECTEDTIME, 0) >= SYNCTIME) {
//                            Prefs.getInstance().saveLong(LASTDISCONNECTEDTIME, System.currentTimeMillis());
//                            if (LogUtil.LOG)
//                                LogUtil.e(TAG, "AP disconnedted..");
//                            Toast.makeText(context, "Sender Wifi Hotspot disconnected. Retrying to connect..", Toast.LENGTH_SHORT).show();
//                            resetSenderSearch();
//                        }
                    }
                }
            }
        }
    }


    private void addSenderFilesListingFragment(String ip, String ssid) {
        String[] senderInfo = setConnectedUi(ssid);
        if (null == senderInfo) {
            Log.e(TAG, "Cant retrieve port and name info from SSID");
            return;
        }
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_SENDER_FILES_LISTING);
        if (null != fragment) {
            if (ip.equals(((FilesListingFragment) fragment).getSenderIp()) && ssid.equals(((FilesListingFragment) fragment).getSenderSSID())) {
                Log.e(TAG, "files fragment exists already!!");
                return;
            } else {
                Log.e(TAG, "fragment with diff tag is found, removing to add a fresh one..");
                removeSenderFilesListingFragmentIfExists();
            }
        }
        Log.d(TAG, "adding files fragment with ip: " + ip);
        FragmentTransaction ft = getSupportFragmentManager()
                .beginTransaction();
        FilesListingFragment files_listing_fragment = FilesListingFragment.getInstance(ip, ssid, senderInfo[0], senderInfo[1]);
//        ft.setCustomAnimations(R.anim.push_left_in, R.anim.push_left_out,
//                R.anim.push_right_in, R.anim.push_right_out);
        ft.add(R.id.sender_files_list_fragment_holder, files_listing_fragment, TAG_SENDER_FILES_LISTING).commitAllowingStateLoss();
    }

    private String[] setConnectedUi(String ssid) {
        String[] senderInfo = WifiUtils.getSenderInfoFromSSID(ssid);
        if (null == senderInfo || senderInfo.length != 2)
            return null;
        String ip = WifiUtils.getThisDeviceIp(getApplicationContext());
        m_p2p_connection_status.setText(getString(R.string.p2p_receiver_connected_hint, ssid, ip));
        m_goto_wifi_settings.setVisibility(View.VISIBLE);
        if (!m_receiver_control.isChecked())
            changeReceiverControlCheckedStatus(true);
        m_sender_files_header.setVisibility(View.VISIBLE);
        return senderInfo;
    }

    protected void gotoWifiSettings() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_WIFI_SETTINGS));
        } catch (ActivityNotFoundException anf) {
            Toast.makeText(this, "No Wifi listings feature found on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeSenderFilesListingFragmentIfExists() {
        m_p2p_connection_status.setText(getString(m_receiver_control.isChecked() ? R.string.p2p_receiver_scanning_hint : R.string.p2p_receiver_hint_text));
        m_goto_wifi_settings.setVisibility(View.GONE);
        m_sender_files_header.setVisibility(View.GONE);
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_SENDER_FILES_LISTING);
        if (null != fragment)
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(fragment).commitAllowingStateLoss();
    }

    static class WifiTasksHandler extends Handler {
        static final int SCAN_FOR_WIFI_RESULTS = 100;
        static final int WAIT_FOR_CONNECT_ACTION_TIMEOUT = 101;
        static final int WAIT_FOR_RECONNECT_ACTION_TIMEOUT = 102;
        private WeakReference<ReceiverActivity> mActivity;

        WifiTasksHandler(ReceiverActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final ReceiverActivity activity = mActivity.get();
            if (null == activity)
                return;
            switch (msg.what) {
                case SCAN_FOR_WIFI_RESULTS:
                    if (null != activity.wifiManager)
                        activity.wifiManager.startScan();
                    break;
                case WAIT_FOR_CONNECT_ACTION_TIMEOUT:
                    Log.e(TAG, "cant connect to sender's hotspot by increasing priority, try the dirty way..");
                    activity.m_areOtherNWsDisabled = WifiUtils.connectToOpenHotspot(activity.wifiManager, (String) msg.obj, true);
                    Message m = obtainMessage(WAIT_FOR_RECONNECT_ACTION_TIMEOUT);
                    m.obj = msg.obj;
                    sendMessageDelayed(m, 6000);
                    break;
                case WAIT_FOR_RECONNECT_ACTION_TIMEOUT:
                    if (WifiUtils.isWifiConnectedToSTAccessPoint(activity) || activity.isFinishing())
                        return;
                    Log.e(TAG, "Even the dirty hack couldn't do it, prompt user to chose it fromWIFI settings..");
                    activity.disableReceiverMode();
                    activity.showOptionsDialogWithListners(activity.getString(R.string.p2p_receiver_connect_timeout_error, msg.obj), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            activity.gotoWifiSettings();
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    }, "Settings", "Cancel");
                    break;
            }
        }
    }
}
