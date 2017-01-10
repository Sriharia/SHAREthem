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
package com.tml.sharethem.sender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tml.sharethem.R;
import com.tml.sharethem.utils.DividerItemDecoration;
import com.tml.sharethem.utils.RecyclerViewArrayAdapter;
import com.tml.sharethem.utils.Utils;
import com.tml.sharethem.utils.HotspotControl;
import com.tml.sharethem.utils.WifiUtils;

import org.json.JSONArray;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tml.sharethem.sender.SHAREthemActivity.ShareUIHandler.LIST_API_CLIENTS;
import static com.tml.sharethem.sender.SHAREthemActivity.ShareUIHandler.UPDATE_AP_STATUS;
import static com.tml.sharethem.utils.Utils.getRandomColor;

/**
 * Controls Hotspot service to share files passed through intent.<br>
 * Displays sender IP and name for receiver to connect to when turned on
 * <p>
 * Created by Sri on 18/12/16.
 */
public class SHAREthemActivity extends AppCompatActivity {

    public static final String TAG = "ShareActivity";
    /*
     * Saves the shared files list in preferences. Useful when activity is opened(from notification or hotspot is already ON) without setting files info on intent
     */
    public static final String PREFERENCES_KEY_SHARED_FILE_PATHS = "sharethem_shared_file_paths";
    public static final String PREFERENCES_KEY_DATA_WARNING_SKIP = "sharethem_data_warning_skip";
    private static final int REQUEST_WRITE_SETTINGS = 1;

    TextView m_sender_wifi_info;
    TextView m_noReceiversText;
    RelativeLayout m_receivers_list_layout;
    RecyclerView m_receiversList;
    SwitchCompat m_apControlSwitch;
    TextView m_showShareList;
    Toolbar m_toolbar;

    private ReceiversListingAdapter m_receiversListAdapter;
    private CompoundButton.OnCheckedChangeListener m_sender_ap_switch_listener;

    private ShareUIHandler m_uiUpdateHandler;
    private BroadcastReceiver m_p2pServerUpdatesListener;

    private HotspotControl hotspotControl;
    private boolean isApEnabled = false;
    private boolean shouldAutoConnect = true;

    private String[] m_sharedFilePaths = null;

    //region: Activity Methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender);

        //init UI
        m_sender_wifi_info = (TextView) findViewById(R.id.p2p_sender_wifi_hint);
        m_noReceiversText = (TextView) findViewById(R.id.p2p_no_receivers_text);
        m_showShareList = (TextView) findViewById(R.id.p2p_sender_items_label);
        m_showShareList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSharedFilesDialog();
            }
        });

        m_receivers_list_layout = (RelativeLayout) findViewById(R.id.p2p_receivers_list_layout);
        m_receiversList = (RecyclerView) findViewById(R.id.p2p_receivers_list);
        m_apControlSwitch = (SwitchCompat) findViewById(R.id.p2p_sender_ap_switch);

        m_toolbar = (Toolbar) findViewById(R.id.toolbar);
        m_toolbar.setTitle(getString(R.string.send_title));
        setSupportActionBar(m_toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        hotspotControl = HotspotControl.getInstance(getApplicationContext());
        m_receiversList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        m_receiversList.addItemDecoration(new DividerItemDecoration(getResources().getDrawable(R.drawable.list_divider)));

        //if file paths are found, save'em into preferences. OR find them in prefs
        if (null != getIntent() && getIntent().hasExtra(SHAREthemService.EXTRA_FILE_PATHS))
            m_sharedFilePaths = getIntent().getStringArrayExtra(SHAREthemService.EXTRA_FILE_PATHS);
        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        if (null == m_sharedFilePaths)
            m_sharedFilePaths = Utils.toStringArray(prefs.getString(PREFERENCES_KEY_SHARED_FILE_PATHS, null));
        else
            prefs.edit().putString(PREFERENCES_KEY_SHARED_FILE_PATHS, new JSONArray(Arrays.asList(m_sharedFilePaths)).toString()).commit();
        m_receiversListAdapter = new ReceiversListingAdapter(new ArrayList<HotspotControl.WifiScanResult>(), m_sharedFilePaths);
        m_receiversList.setAdapter(m_receiversListAdapter);
        m_sender_ap_switch_listener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                shouldAutoConnect = isChecked;
                if (isChecked) {
                    //If target version is MM and beyond, you need to check System Write permissions to proceed.
                    if (Build.VERSION.SDK_INT >= 23 &&
                            // if targetSdkVersion >= 23
                            //     ShareActivity has to check for System Write permissions to proceed
                            Utils.getTargetSDKVersion(getApplicationContext()) >= 23 && !Settings.System.canWrite(SHAREthemActivity.this)) {
                        changeApControlCheckedStatus(false);
                        showMessageDialogWithListner(getString(R.string.p2p_sender_system_settings_permission_prompt), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivityForResult(intent, REQUEST_WRITE_SETTINGS);
                            }
                        }, false, true);
                        return;
                    } else if (!getSharedPreferences(getPackageName(), Context.MODE_PRIVATE).getBoolean(PREFERENCES_KEY_DATA_WARNING_SKIP, false) && Utils.isMobileDataEnabled(getApplicationContext())) {
                        changeApControlCheckedStatus(false);
                        showDataWarningDialog();
                        return;
                    }
                    m_sender_wifi_info.setText(getString(R.string.p2p_sender_hint_connecting));
                    enableAp();
                } else {
                    changeApControlCheckedStatus(true);
                    showMessageDialogWithListner(getString(R.string.p2p_sender_close_warning), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "sending intent to service to stop p2p..");
                            resetSenderUi(true);
                        }
                    }, true, false);
                }
            }
        };
        m_apControlSwitch.setOnCheckedChangeListener(m_sender_ap_switch_listener);
        m_p2pServerUpdatesListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isFinishing() || null == intent)
                    return;
                int intentType = intent.getIntExtra(SHAREthemService.ShareIntents.TYPE, 0);
                if (intentType == SHAREthemService.ShareIntents.Types.FILE_TRANSFER_STATUS) {
                    String fileName = intent.getStringExtra(SHAREthemService.ShareIntents.SHARE_SERVER_UPDATE_FILE_NAME);
                    updateReceiverListItem(intent.getStringExtra(SHAREthemService.ShareIntents.SHARE_CLIENT_IP), intent.getIntExtra(SHAREthemService.ShareIntents.SHARE_TRANSFER_PROGRESS, -1), intent.getStringExtra(SHAREthemService.ShareIntents.SHARE_SERVER_UPDATE_TEXT), fileName);
                } else if (intentType == SHAREthemService.ShareIntents.Types.AP_DISABLED_ACKNOWLEDGEMENT) {
                    shouldAutoConnect = false;
                    resetSenderUi(false);
                }
            }
        };
        registerReceiver(m_p2pServerUpdatesListener, new IntentFilter(SHAREthemService.ShareIntents.SHARE_SERVER_UPDATES_INTENT_ACTION));
    }

    @Override
    protected void onResume() {
        super.onResume();
        //If service is already running, change UI and display info for receiver
        if (Utils.isShareServiceRunning(getApplicationContext())) {
            if (!m_apControlSwitch.isChecked()) {
                Log.e(TAG, "p2p service running, changing switch status and start handler for ui changes");
                changeApControlCheckedStatus(true);
            }
            refreshApData();
            m_receivers_list_layout.setVisibility(View.VISIBLE);
        } else if (m_apControlSwitch.isChecked()) {
            changeApControlCheckedStatus(false);
            resetSenderUi(false);
        }
        //switch on sender mode if not already
        else if (shouldAutoConnect) m_apControlSwitch.setChecked(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != m_p2pServerUpdatesListener)
            unregisterReceiver(m_p2pServerUpdatesListener);
        if (null != m_uiUpdateHandler)
            m_uiUpdateHandler.removeCallbacksAndMessages(null);
        m_uiUpdateHandler = null;
    }
    //endregion: Activity Methods

    //region: Dialog utilities
    public void showMessageDialogWithListner(String message,
                                             DialogInterface.OnClickListener listner, boolean showNegavtive,
                                             final boolean finishCurrentActivity) {
        if (isFinishing())
            return;
        final AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setCancelable(false);
        builder.setMessage(Html.fromHtml(message));
        builder.setPositiveButton(getString(R.string.Action_Ok), listner);
        if (showNegavtive)
            builder.setNegativeButton(getString(R.string.Action_cancel),
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (finishCurrentActivity)
                                finish();
                            else dialog.dismiss();
                        }
                    });
        builder.show();
    }

    public void showDataWarningDialog() {
        if (isFinishing())
            return;
        final AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setCancelable(false);
        builder.setMessage(getString(R.string.sender_data_on_warning));
        builder.setPositiveButton(getString(R.string.label_settings), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                startActivity(new Intent(
                        Settings.ACTION_SETTINGS));
            }
        });
        builder.setNegativeButton(getString(R.string.label_thats_ok),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        changeApControlCheckedStatus(true);
                        m_sender_wifi_info.setText(getString(R.string.p2p_sender_hint_connecting));
                        enableAp();
                    }
                });
        builder.setNeutralButton(getString(R.string.label_dont_ask), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
                prefs.edit().putBoolean(PREFERENCES_KEY_DATA_WARNING_SKIP, true).apply();
                changeApControlCheckedStatus(true);
                m_sender_wifi_info.setText(getString(R.string.p2p_sender_hint_connecting));
                enableAp();
            }
        });
        builder.show();
    }

    /**
     * Shows shared File urls
     */
    void showSharedFilesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Shared Files");
        builder.setItems(m_sharedFilePaths, null);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }
    //endregion: Activity Methods

    //region: Hotspot Control
    private void enableAp() {
        startP2pSenderWatchService();
        refreshApData();
        m_receivers_list_layout.setVisibility(View.VISIBLE);
    }

    private void disableAp() {
        //Send STOP action to service
        Intent p2pServiceIntent = new Intent(getApplicationContext(), SHAREthemService.class);
        p2pServiceIntent.setAction(SHAREthemService.WIFI_AP_ACTION_STOP);
        startService(p2pServiceIntent);
        isApEnabled = false;
    }

    /**
     * Starts {@link SHAREthemService} with intent action {@link SHAREthemService#WIFI_AP_ACTION_START} to enableShareThemHotspot Hotspot and start {@link SHAREthemServer}.
     */
    private void startP2pSenderWatchService() {
        Intent p2pServiceIntent = new Intent(getApplicationContext(), SHAREthemService.class);
        p2pServiceIntent.putExtra(SHAREthemService.EXTRA_FILE_PATHS, m_sharedFilePaths);
        if (null != getIntent()) {
            p2pServiceIntent.putExtra(SHAREthemService.EXTRA_PORT, getIntent().getIntExtra(SHAREthemService.EXTRA_PORT, 0));
            p2pServiceIntent.putExtra(SHAREthemService.EXTRA_SENDER_NAME, getIntent().getStringExtra(SHAREthemService.EXTRA_SENDER_NAME));
        }
        p2pServiceIntent.setAction(SHAREthemService.WIFI_AP_ACTION_START);
        startService(p2pServiceIntent);
    }

    /**
     * Starts {@link SHAREthemService} with intent action {@link SHAREthemService#WIFI_AP_ACTION_START_CHECK} to make {@link SHAREthemService} constantly check for Hotspot status. (Sometimes Hotspot tend to stop if stayed idle for long enough. So this check makes sure {@link SHAREthemService} is only alive if Hostspot is enaled.)
     */
    private void startHostspotCheckOnService() {
        Intent p2pServiceIntent = new Intent(getApplicationContext(), SHAREthemService.class);
        p2pServiceIntent.setAction(SHAREthemService.WIFI_AP_ACTION_START_CHECK);
        startService(p2pServiceIntent);
    }

    /**
     * Calls methods - {@link SHAREthemActivity#updateApStatus()} & {@link SHAREthemActivity#listApClients()} which are responsible for displaying Hotpot information and Listing connected clients to the same
     */
    private void refreshApData() {
        if (null == m_uiUpdateHandler)
            m_uiUpdateHandler = new ShareUIHandler(this);
        updateApStatus();
        listApClients();
    }

    /**
     * Updates Hotspot configuration info like Name, IP if enabled.<br> Posts a message to {@link ShareUIHandler} to call itself every 1500ms
     */
    private void updateApStatus() {
        if (!HotspotControl.isSupported()) {
            m_sender_wifi_info.setText("Warning: Hotspot mode not supported!\n");
        }
        if (hotspotControl.isEnabled()) {
            if (!isApEnabled) {
                isApEnabled = true;
                startHostspotCheckOnService();
            }
            WifiConfiguration config = hotspotControl.getConfiguration();
            String ip = Build.VERSION.SDK_INT >= 23 ? WifiUtils.getHostIpAddress() : hotspotControl.getHostIpAddress();
            if (TextUtils.isEmpty(ip))
                ip = "";
            else
                ip = ip.replace("/", "");
            m_toolbar.setSubtitle(getString(R.string.p2p_sender_subtitle));
            m_sender_wifi_info.setText(getString(R.string.p2p_sender_hint_wifi_connected, config.SSID, "http://" + ip + ":" + hotspotControl.getShareServerListeningPort()));
            if (m_showShareList.getVisibility() == View.GONE) {
                m_showShareList.append(String.valueOf(m_sharedFilePaths.length));
                m_showShareList.setVisibility(View.VISIBLE);
            }
        }
        if (null != m_uiUpdateHandler) {
            m_uiUpdateHandler.removeMessages(UPDATE_AP_STATUS);
            m_uiUpdateHandler.sendEmptyMessageDelayed(UPDATE_AP_STATUS, 1500);
        }
    }

    /**
     * Calls {@link HotspotControl#getConnectedWifiClients(int, HotspotControl.WifiClientConnectionListener)} to get Clients connected to Hotspot.<br>
     * Constantly adds/updates receiver items on {@link SHAREthemActivity#m_receiversList}
     * <br> Posts a message to {@link ShareUIHandler} to call itself every 1000ms
     */
    private synchronized void listApClients() {
        if (hotspotControl == null) {
            return;
        }
        hotspotControl.getConnectedWifiClients(2000,
                new HotspotControl.WifiClientConnectionListener() {
                    public void onClientConnectionAlive(final HotspotControl.WifiScanResult wifiScanResult) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                addReceiverListItem(wifiScanResult);
                            }
                        });
                    }

                    @Override
                    public void onClientConnectionDead(final HotspotControl.WifiScanResult c) {
                        Log.e(TAG, "onClientConnectionDead: " + c.ip);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onReceiverDisconnected(c.ip);
                            }
                        });
                    }

                    public void onWifiClientsScanComplete() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (null != m_uiUpdateHandler) {
                                    m_uiUpdateHandler.removeMessages(LIST_API_CLIENTS);
                                    m_uiUpdateHandler.sendEmptyMessageDelayed(LIST_API_CLIENTS, 1000);
                                }
                            }
                        });
                    }
                }

        );
    }

    private void resetSenderUi(boolean disableAP) {
        m_uiUpdateHandler.removeCallbacksAndMessages(null);
        m_sender_wifi_info.setText(getString(R.string.p2p_sender_hint_text));
        m_receivers_list_layout.setVisibility(View.GONE);
        m_showShareList.setVisibility(View.GONE);
        m_toolbar.setSubtitle("");
        if (disableAP)
            disableAp();
        else {
            changeApControlCheckedStatus(false);
        }
        if (null != m_receiversListAdapter)
            m_receiversListAdapter.clear();
        m_noReceiversText.setVisibility(View.VISIBLE);
    }

    /**
     * Changes checked status without invoking listener. Removes @{@link android.widget.CompoundButton.OnCheckedChangeListener} on @{@link SwitchCompat} button before changing checked status
     *
     * @param checked if <code>true</code>, sets @{@link SwitchCompat} checked.
     */
    private void changeApControlCheckedStatus(boolean checked) {
        m_apControlSwitch.setOnCheckedChangeListener(null);
        m_apControlSwitch.setChecked(checked);
        m_apControlSwitch.setOnCheckedChangeListener(m_sender_ap_switch_listener);
    }
    //endregion: Hotspot Control

    //region: Wifi Clients Listing

    /**
     * Finds the {@link View} tagged with <code>ip</code> and updates file transfer status of a shared File
     *
     * @param ip         Receiver IP
     * @param progress   File transfer progress
     * @param updatetext Text to be displayed. Could be Speed, Transferred size or an Error Status
     * @param fileName   name of File shared
     */
    private void updateReceiverListItem(String ip, int progress, String updatetext, String fileName) {
        View taskListItem = m_receiversList.findViewWithTag(ip);
        if (null != taskListItem) {
            ReceiversListItemHolder holder = new ReceiversListItemHolder(taskListItem);
            if (updatetext.contains("Error in file transfer")) {
                holder.resetTransferInfo(fileName);
                return;
            }
            holder.update(fileName, updatetext, progress);
        } else {
            Log.e(TAG, "no list item found with this IP******");
        }
    }

    /**
     * Adds a {@link HotspotControl.WifiScanResult} item to {@link SHAREthemActivity#m_receiversListAdapter} if not already added
     *
     * @param wifiScanResult Connected Receiver item
     */
    private void addReceiverListItem(HotspotControl.WifiScanResult wifiScanResult) {
        List<HotspotControl.WifiScanResult> wifiScanResults = m_receiversListAdapter.getObjects();
        if (null != wifiScanResults && wifiScanResults.indexOf(wifiScanResult) != -1) {
            Log.e(TAG, "duplicate client, try updating connection status");
            View taskListItem = m_receiversList.findViewWithTag(wifiScanResult.ip);
            if (null == taskListItem)
                return;
            ReceiversListItemHolder holder = new ReceiversListItemHolder(taskListItem);
            if (holder.isDisconnected()) {
                Log.d(TAG, "changing disconnected ui to connected: " + wifiScanResult.ip);
                holder.setConnectedUi(wifiScanResult);
            }
        } else {
            m_receiversListAdapter.add(wifiScanResult);
            if (m_noReceiversText.getVisibility() == View.VISIBLE)
                m_noReceiversText.setVisibility(View.GONE);
        }
    }

    private void onReceiverDisconnected(String ip) {
        View taskListItem = m_receiversList.findViewWithTag(ip);
        if (null != taskListItem) {
            ReceiversListItemHolder holder = new ReceiversListItemHolder(taskListItem);
            if (!holder.isDisconnected())
                holder.setDisconnectedUi();
//            m_receiversListAdapter.remove(new WifiApControl.Client(ip, null, null));
        }
        if (m_receiversListAdapter.getItemCount() == 0)
            m_noReceiversText.setVisibility(View.VISIBLE);
    }

    static class ReceiversListItemHolder extends RecyclerView.ViewHolder {
        TextView title, connection_status;

        ReceiversListItemHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.p2p_receiver_title);
            connection_status = (TextView) itemView.findViewById(R.id.p2p_receiver_connection_status);
        }

        void setConnectedUi(HotspotControl.WifiScanResult wifiScanResult) {
            title.setText(wifiScanResult.ip);
            connection_status.setText("Connected");
            connection_status.setTextColor(Color.GREEN);
        }

        void resetTransferInfo(String fileName) {
            View v = itemView.findViewWithTag(fileName);
            if (null == v) {
                Log.e(TAG, "resetTransferInfo - no view found with file name tag!!");
                return;
            }
            ((TextView) v).setText("");
        }

        void update(String fileName, String transferData, int progress) {
            View v = itemView.findViewWithTag(fileName);
            if (null == v) {
                Log.e(TAG, "update - no view found with file name tag!!");
                return;
            }
            if (v.getVisibility() == View.GONE)
                v.setVisibility(View.VISIBLE);
            ((TextView) v).setText(transferData);
        }

        void setDisconnectedUi() {
            connection_status.setText("Disconnected");
            connection_status.setTextColor(Color.RED);
        }

        boolean isDisconnected() {
            return "Disconnected".equalsIgnoreCase(connection_status.getText().toString());
        }
    }

    private static class ReceiversListingAdapter extends RecyclerViewArrayAdapter<HotspotControl.WifiScanResult, ReceiversListItemHolder> {
        String[] sharedFiles;

        ReceiversListingAdapter(List<HotspotControl.WifiScanResult> objects, String[] sharedFiles) {
            super(objects);
            this.sharedFiles = sharedFiles;
        }

        @Override
        public ReceiversListItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout itemView = (LinearLayout) LayoutInflater.from(parent.getContext()).
                    inflate(R.layout.listitem_receivers, parent, false);
            //Add at least those many textviews of shared files list size so that if a receiver decides to download them all at once, list item can manage to show status of all file downloads
            if (null != sharedFiles && sharedFiles.length > 0)
                for (int i = 0; i < sharedFiles.length; i++) {
                    TextView statusView = (TextView) LayoutInflater.from(parent.getContext()).
                            inflate(R.layout.include_sender_list_item, parent, false);
                    statusView.setTag(sharedFiles[i].substring(sharedFiles[i].lastIndexOf('/') + 1, sharedFiles[i].length()));
                    statusView.setVisibility(View.GONE);
                    statusView.setTextColor(getRandomColor());
                    itemView.addView(statusView);
                }
            return new ReceiversListItemHolder(itemView);
        }

        @Override
        public void onBindViewHolder(ReceiversListItemHolder holder, int position) {
            HotspotControl.WifiScanResult receiver = mObjects.get(position);
            if (null == receiver)
                return;
            holder.itemView.setTag(receiver.ip);
            holder.setConnectedUi(receiver);
        }
    }
    //endregion: Wifi Clients Listing

    //region: UI Handler
    static class ShareUIHandler extends Handler {
        WeakReference<SHAREthemActivity> mActivity;

        static final int LIST_API_CLIENTS = 100;
        static final int UPDATE_AP_STATUS = 101;

        ShareUIHandler(SHAREthemActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            SHAREthemActivity activity = mActivity.get();
            if (null == activity || msg == null || !activity.m_apControlSwitch.isChecked())
                return;
            if (msg.what == LIST_API_CLIENTS) {
                activity.listApClients();
            } else if (msg.what == UPDATE_AP_STATUS) {
                activity.updateApStatus();
            }
        }
    }
    //endregion: UI Handler

}
