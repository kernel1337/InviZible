package pan.alexander.tordnscrypt.modules;

/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.utils.AuxNotificationSender;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.InternetSharingChecker;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Preferences;
import pan.alexander.tordnscrypt.vpn.Util;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

public class ModulesBroadcastReceiver extends BroadcastReceiver {

    private final static int DELAY_BEFORE_CHECKING_INTERNET_SHARING_SEC = 5;
    private final static int DELAY_BEFORE_UPDATING_IPTABLES_RULES_SEC = 5;

    private final Context context;
    private boolean receiverRegistered = false;
    private Object networkCallback;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private volatile boolean lock = false;
    private static final String apStateFilterAction = "android.net.wifi.WIFI_AP_STATE_CHANGED";
    private static final String tetherStateFilterAction = "android.net.conn.TETHER_STATE_CHANGED";
    private static final String shutdownFilterAction = "android.intent.action.ACTION_SHUTDOWN";
    private static final String powerOFFFilterAction = "android.intent.action.QUICKBOOT_POWEROFF";
    private final ArpScanner arpScanner;

    public ModulesBroadcastReceiver(Context context, ArpScanner arpScanner) {
        this.context = context;
        this.arpScanner = arpScanner;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action == null) {
            return;
        }

        if (action.equalsIgnoreCase(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            Log.i(LOG_TAG, "ModulesBroadcastReceiver Received " + intent);

            idleStateChanged(context);

        } else if (action.equalsIgnoreCase(ConnectivityManager.CONNECTIVITY_ACTION)) {
            connectivityStateChanged(intent);
        } else if (action.equalsIgnoreCase(apStateFilterAction)) {
            checkInternetSharingState();
        } else if (action.equalsIgnoreCase(tetherStateFilterAction)) {
            checkInternetSharingState();
        } else if (action.equalsIgnoreCase(powerOFFFilterAction) || action.equalsIgnoreCase(shutdownFilterAction)) {
            powerOFFDetected();
        } else if (action.equalsIgnoreCase(Intent.ACTION_PACKAGE_ADDED) || action.equalsIgnoreCase(Intent.ACTION_PACKAGE_REMOVED)) {
            packageChanged();
        }
    }

    void registerReceivers() {
        registerIdleStateChanged();
        registerConnectivityChanges();
        registerAPisOn();
        registerUSBModemIsOn();
        registerPowerOFF();
        registerPackageChanged();
    }

    void unregisterReceivers() {
        if (context == null) {
            return;
        }

        if (receiverRegistered) {
            context.unregisterReceiver(this);
            receiverRegistered = false;
        }

        if (networkCallback != null) {
            unlistenNetworkChanges();
            networkCallback = null;
        }
    }

    private void registerIdleStateChanged() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IntentFilter ifIdle = new IntentFilter();
            ifIdle.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
            context.registerReceiver(this, ifIdle);
            receiverRegistered = true;
        }
    }

    private void registerConnectivityChanges() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                listenNetworkChanges();
            } catch (Throwable ex) {
                Log.w(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                listenConnectivityChanges();
            }
        } else {
            listenConnectivityChanges();
        }
    }

    private void registerAPisOn() {
        IntentFilter apStateChanged = new IntentFilter();
        apStateChanged.addAction(apStateFilterAction);
        context.registerReceiver(this, apStateChanged);
        receiverRegistered = true;
    }

    private void registerUSBModemIsOn() {
        IntentFilter apStateChanged = new IntentFilter();
        apStateChanged.addAction(tetherStateFilterAction);
        context.registerReceiver(this, apStateChanged);
        receiverRegistered = true;
    }

    private void registerPowerOFF() {
        IntentFilter powerOFF = new IntentFilter();
        powerOFF.addAction(shutdownFilterAction);
        powerOFF.addAction(powerOFFFilterAction);
        context.registerReceiver(this, powerOFF);
        receiverRegistered = true;
    }

    private void registerPackageChanged() {
        // Listen for added/removed applications
        IntentFilter ifPackage = new IntentFilter();
        ifPackage.addAction(Intent.ACTION_PACKAGE_ADDED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_REMOVED);
        ifPackage.addDataScheme("package");
        context.registerReceiver(this, ifPackage);
        receiverRegistered = true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void listenNetworkChanges() {
        // Listen for network changes
        Log.i(LOG_TAG, "ModulesBroadcastReceiver Starting listening to network changes");
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }

        ConnectivityManager.NetworkCallback nc = new ConnectivityManager.NetworkCallback() {
            private List<InetAddress> last_dns = null;
            private int last_network = 0;

            @Override
            public void onAvailable(@NonNull Network network) {
                Log.i(LOG_TAG, "ModulesBroadcastReceiver Available network=" + network);
                updateIptablesRules(false);
                resetArpScanner(true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && last_network != network.hashCode()) {
                    AuxNotificationSender.INSTANCE.checkPrivateDNSAndProxy(
                            context, null
                    );
                }

                last_network = network.hashCode();

            }

            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, LinkProperties linkProperties) {
                // Make sure the right DNS servers are being used
                List<InetAddress> dns = linkProperties.getDnsServers();

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !same(last_dns, dns)) {
                    Log.i(LOG_TAG, "ModulesBroadcastReceiver Changed link properties=" + linkProperties +
                            "ModulesBroadcastReceiver cur=" + TextUtils.join(",", dns) +
                            "ModulesBroadcastReceiver prv=" + (last_dns == null ? null : TextUtils.join(",", last_dns)));
                    last_dns = dns;
                    Log.i(LOG_TAG, "ModulesBroadcastReceiver Changed link properties=" + linkProperties);
                    updateIptablesRules(false);
                }

                if (network.hashCode() != last_network) {
                    last_network = network.hashCode();
                    resetArpScanner();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        AuxNotificationSender.INSTANCE.checkPrivateDNSAndProxy(
                                context, linkProperties
                        );
                    }
                }
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                if (last_network != network.hashCode()) {
                    updateIptablesRules(false);
                    resetArpScanner();
                    last_network = network.hashCode();

                    Log.i(LOG_TAG, "ModulesBroadcastReceiver Changed capabilities=" + network);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.i(LOG_TAG, "ModulesBroadcastReceiver Lost network=" + network);
                updateIptablesRules(false);
                resetArpScanner(false);
                last_network = 0;
            }

            boolean same(List<InetAddress> last, List<InetAddress> current) {
                if (last == null || current == null)
                    return false;
                if (last.size() != current.size())
                    return false;

                for (int i = 0; i < current.size(); i++)
                    if (!last.get(i).equals(current.get(i)))
                        return false;

                return true;
            }
        };
        if (cm != null) {
            cm.registerNetworkCallback(builder.build(), nc);
            networkCallback = nc;
        }
    }

    private void listenConnectivityChanges() {
        // Listen for connectivity updates
        Log.i(LOG_TAG, "ModulesBroadcastReceiver Starting listening to connectivity changes");
        IntentFilter ifConnectivity = new IntentFilter();
        ifConnectivity.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(this, ifConnectivity);
        receiverRegistered = true;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void idleStateChanged(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            Log.i(LOG_TAG, "ModulesBroadcastReceiver device idle=" + pm.isDeviceIdleMode());
        }

        // Reload rules when coming from idle mode
        if (pm != null && !pm.isDeviceIdleMode()) {
            updateIptablesRules(false);
            resetArpScanner();
        }
    }

    private void connectivityStateChanged(Intent intent) {
        // Reload rules
        Log.i(LOG_TAG, "ModulesBroadcastReceiver connectivityStateChanged Received " + intent);
        updateIptablesRules(false);

        resetArpScanner();
    }

    private void checkInternetSharingState() {
        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            boolean wifiAccessPointOn = false;
            boolean usbTetherOn = false;

            try {
                TimeUnit.SECONDS.sleep(DELAY_BEFORE_CHECKING_INTERNET_SHARING_SEC);

                InternetSharingChecker checker = new InternetSharingChecker();
                checker.updateData();
                wifiAccessPointOn = checker.isApOn();
                usbTetherOn = checker.isUsbTetherOn();

            } catch (Exception e) {
                Log.e(LOG_TAG, "ModulesBroadcastReceiver checkInternetSharingState exception", e);
            }

            if (wifiAccessPointOn && !new PrefManager(context).getBoolPref(Preferences.WIFI_ACCESS_POINT_IS_ON)) {
                new PrefManager(context).setBoolPref(Preferences.WIFI_ACCESS_POINT_IS_ON, true);
                modulesStatus.setIptablesRulesUpdateRequested(context, true);
            } else if (!wifiAccessPointOn && new PrefManager(context).getBoolPref(Preferences.WIFI_ACCESS_POINT_IS_ON)) {
                new PrefManager(context).setBoolPref(Preferences.WIFI_ACCESS_POINT_IS_ON, false);
                modulesStatus.setIptablesRulesUpdateRequested(context, true);
            }

            if (usbTetherOn && !new PrefManager(context).getBoolPref(Preferences.USB_MODEM_IS_ON)) {
                new PrefManager(context).setBoolPref(Preferences.USB_MODEM_IS_ON, true);
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
            } else if (!usbTetherOn && new PrefManager(context).getBoolPref(Preferences.USB_MODEM_IS_ON)) {
                new PrefManager(context).setBoolPref(Preferences.USB_MODEM_IS_ON, false);
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
            }

            Log.i(LOG_TAG, "ModulesBroadcastReceiver " +
                    "WiFi Access Point state is " + (wifiAccessPointOn ? "ON" : "OFF") + "\n"
                            + " USB modem state is " + (usbTetherOn ? "ON" : "OFF"));
        });
    }

    private void powerOFFDetected() {
        ModulesAux.saveDNSCryptStateRunning(context, false);
        ModulesAux.saveTorStateRunning(context, false);
        ModulesAux.saveITPDStateRunning(context, false);

        ModulesAux.stopModulesIfRunning(context);
    }

    private void packageChanged() {
        Log.i(LOG_TAG, "ModulesBroadcastReceiver packageChanged");
        updateIptablesRules(true);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unlistenNetworkChanges() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            cm.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) networkCallback);
        }
    }

    private void updateIptablesRules(boolean forceUpdate) {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean refreshRules = sharedPreferences.getBoolean("swRefreshRules", false);

        if (!refreshRules && !forceUpdate) {
            return;
        }

        if (modulesStatus.getMode() == ROOT_MODE
                && !modulesStatus.isUseModulesWithRoot()
                && !lock) {

            CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
                if (!lock) {

                    lock = true;

                    try {
                        TimeUnit.SECONDS.sleep(DELAY_BEFORE_UPDATING_IPTABLES_RULES_SEC);
                    } catch (InterruptedException e) {
                        Log.w(LOG_TAG, "ModulesBroadcastReceiver sleep interruptedException " + e.getMessage());
                    }

                    if (modulesStatus.getMode() == ROOT_MODE && !modulesStatus.isUseModulesWithRoot()) {
                        modulesStatus.setIptablesRulesUpdateRequested(context, true);
                    }

                    lock = false;
                }

            });
        }
    }

    private void resetArpScanner(boolean connectionAvailable) {
        if (arpScanner != null) {
            arpScanner.reset(context, connectionAvailable);
        }
    }

    private void resetArpScanner() {
        if (arpScanner != null && context != null) {
            arpScanner.reset(context, Util.isConnected(context));
        }
    }
}
