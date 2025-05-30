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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.dialogs.RequestIgnoreBatteryOptimizationDialog;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesServiceActions;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ModulesStatusBroadcaster;
import pan.alexander.tordnscrypt.proxy.ProxyHelper;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnTextFileOperationsCompleteListener;
import pan.alexander.tordnscrypt.utils.session.AppSessionStore;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment.ISOLATE_DEST_ADDRESS;
import static pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment.ISOLATE_DEST_PORT;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS_IPv6;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALWAYS_ON_VPN;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_BLOCK_INTERNET;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_DETECTION;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_NOT_SUPPORTED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.COMPATIBILITY_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNS_REBIND_PROTECTION;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIX_TTL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ITPD_TETHERING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.KILL_SWITCH;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MAIN_ACTIVITY_RECREATE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MULTI_USER_SUPPORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REFRESH_RULES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_CONTROL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.RUN_MODULES_WITH_ROOT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_IPTABLES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.WAIT_IPTABLES;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.utils.session.SessionKeys.MULTIPLE_USERS_EXISTS;

import javax.inject.Inject;
import javax.inject.Named;


public class PreferencesCommonFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
        OnTextFileOperationsCompleteListener {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CoroutineExecutor executor;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<ProxyHelper> proxyHelper;
    @Inject
    public Lazy<Verifier> verifierLazy;
    @Inject
    public Lazy<ModulesStatusBroadcaster> modulesStatusBroadcaster;
    @Inject
    public AppSessionStore sessionStore;

    private static final int ARP_SCANNER_CHANGE_STATE_DELAY_SEC = 5;

    private String torTransPort;
    private String torSocksPort;
    private String torHTTPTunnelPort;
    private boolean allowTorTether = false;
    private boolean allowITPDtether = false;
    private String torConfPath = "";
    private String itpdConfPath = "";
    private String itpdTunnelsPath = "";

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    public PreferencesCommonFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_common);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        FileManager.setOnFileOperationCompleteListener(this);

        Activity activity = getActivity();

        if (activity == null) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        activity.setTitle(R.string.drawer_menu_commonSettings);

        PreferenceCategory otherCategory = findPreference("common_other");
        Preference swShowNotification = findPreference("swShowNotification");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (otherCategory != null && swShowNotification != null) {
                otherCategory.removePreference(swShowNotification);
            }
        } else {
            if (swShowNotification != null) {
                swShowNotification.setOnPreferenceChangeListener(this);
            }
        }

        Preference alwaysOnVPN = findPreference(ALWAYS_ON_VPN);
        if (modulesStatus.getMode() == VPN_MODE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && alwaysOnVPN != null) {
            alwaysOnVPN.setOnPreferenceClickListener(this);
        } else if (otherCategory != null && alwaysOnVPN != null) {
            otherCategory.removePreference(alwaysOnVPN);
        }

        Preference swCompatibilityMode = findPreference(COMPATIBILITY_MODE);
        if (modulesStatus.getMode() != VPN_MODE && otherCategory != null && swCompatibilityMode != null) {
            otherCategory.removePreference(swCompatibilityMode);
        } else if (swCompatibilityMode != null) {
            swCompatibilityMode.setOnPreferenceChangeListener(this);
        }


        boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();
        PreferenceScreen preferenceScreen = findPreference("pref_common");
        PreferenceCategory proxySettingsCategory = findPreference("categoryCommonProxy");
        Preference swUseProxy = findPreference(USE_PROXY);
        if (preferenceScreen != null && proxySettingsCategory != null) {
            if ((modulesStatus.getMode() == VPN_MODE || fixTTL) && swUseProxy != null) {
                swUseProxy.setOnPreferenceClickListener(this);
            } else {
                preferenceScreen.removePreference(proxySettingsCategory);
            }
        }

        Preference tetheringSettings = findPreference("pref_common_tethering_settings");
        if (tetheringSettings != null) {
            if (modulesStatus.getMode() == VPN_MODE || modulesStatus.getMode() == ROOT_MODE) {
                tetheringSettings.setOnPreferenceClickListener(this);
            }
        }

        Preference multiUser = findPreference(MULTI_USER_SUPPORT);
        boolean singleUser = Boolean.FALSE.equals(sessionStore.restore(MULTIPLE_USERS_EXISTS));
        if (otherCategory != null && multiUser != null) {
            if (modulesStatus.getMode() == VPN_MODE && singleUser
                    || modulesStatus.getMode() == PROXY_MODE) {
                otherCategory.removePreference(multiUser);
            } else {
                multiUser.setOnPreferenceChangeListener(this);
            }
        }

        PreferenceCategory mitmCategory = findPreference("pref_common_mitm_categ");
        Preference mitmDetection = findPreference(ARP_SPOOFING_DETECTION);
        Preference mitmBlockInternet = findPreference(ARP_SPOOFING_BLOCK_INTERNET);
        if (mitmCategory != null && mitmDetection != null && mitmBlockInternet != null) {
            mitmDetection.setOnPreferenceChangeListener(this);

            if (preferenceRepository.get().getBoolPreference(ARP_SPOOFING_NOT_SUPPORTED)) {
                mitmDetection.setTitle(R.string.pref_common_rogue_dhcp_detection);
                mitmDetection.setSummary(R.string.pref_common_rogue_dhcp_detection_summ);
            } else {
                mitmDetection.setTitle(R.string.pref_common_arp_spoofing_detection);
                mitmDetection.setSummary(R.string.pref_common_arp_spoofing_detection_summ);
            }

            if (modulesStatus.getMode() == PROXY_MODE) {
                mitmCategory.removePreference(mitmBlockInternet);
            } else {
                mitmBlockInternet.setOnPreferenceChangeListener(this);
            }
        }

        Preference rebindDetection = findPreference(DNS_REBIND_PROTECTION);
        if (mitmCategory != null && rebindDetection != null) {
            if (modulesStatus.getMode() == VPN_MODE || fixTTL) {
                rebindDetection.setOnPreferenceChangeListener(this);
            } else {
                mitmCategory.removePreference(rebindDetection);
            }
        }

        if (modulesStatus.getMode() == ROOT_MODE) {
            registerPreferences();
        } else {
            removePreferences();
        }

        manageLANDeviceAddressPreference(fixTTL);

        Preference shellControl = findPreference(REMOTE_CONTROL);
        if (shellControl != null) {
            shellControl.setSummary(String.format(getString(R.string.pref_common_shell_control_summ), activity.getPackageName()));
            shellControl.setOnPreferenceChangeListener(this);
        }

        if (pathVars.get().getAppVersion().startsWith("g")) {
            PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");
            Preference blockHTTP = findPreference("pref_common_block_http");
            if (hotspotSettingsCategory != null && blockHTTP != null) {
                hotspotSettingsCategory.removePreference(blockHTTP);
            }
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();

        Context context = getActivity();

        if (context == null) {
            return;
        }

        torTransPort = pathVars.get().getTorTransPort();
        torSocksPort = pathVars.get().getTorSOCKSPort();
        torHTTPTunnelPort = pathVars.get().getTorHTTPTunnelPort();
        torConfPath = pathVars.get().getTorConfPath();
        itpdConfPath = pathVars.get().getItpdConfPath();
        itpdTunnelsPath = pathVars.get().getItpdTunnelsPath();

        executor.submit("PreferencesCommonFragment verifier", () -> {
            try {
                Verifier verifier = verifierLazy.get();
                String appSign = verifier.getAppSignature();
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(verifier.getWrongSign(), appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, getString(R.string.verifier_error), "5889");
                    if (notificationHelper != null && isAdded()) {
                        handler.get().post(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        context, getString(R.string.verifier_error), "5804");
                if (notificationHelper != null && isAdded()) {
                    handler.get().post(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                }
                loge("PreferencesCommonFragment fault", e, true);
            }
            return null;
        });

    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {

        Context context = getActivity();

        if (context == null) {
            return false;
        }

        switch (preference.getKey()) {
            case "swShowNotification":
                if (!Boolean.parseBoolean(newValue.toString())) {
                    Intent intent = new Intent(context, ModulesService.class);
                    intent.setAction(ModulesServiceActions.ACTION_DISMISS_NOTIFICATION);
                    context.startService(intent);
                }
                break;
            case TOR_TETHERING:
                allowTorTether = Boolean.parseBoolean(newValue.toString());
                readTorConf(context);
                break;
            case ITPD_TETHERING:
                allowITPDtether = Boolean.parseBoolean(newValue.toString());
                readITPDConf(context);
                readITPDTunnelsConf(context);
                break;
            case "pref_common_tor_route_all":
                Preference prefTorSiteUnlockTether = findPreference("prefTorSiteUnlockTether");
                if (prefTorSiteUnlockTether != null) {
                    prefTorSiteUnlockTether.setEnabled(!Boolean.parseBoolean(newValue.toString()));
                }

                if (ModulesAux.isTorSavedStateRunning()) {
                    modulesStatus.setIptablesRulesUpdateRequested(context, true);
                }
                break;
            case "pref_common_block_http":
                if (ModulesAux.isDnsCryptSavedStateRunning()
                        || ModulesAux.isTorSavedStateRunning()) {
                    modulesStatus.setIptablesRulesUpdateRequested(context, true);
                }
                break;
            case RUN_MODULES_WITH_ROOT:
                ModulesAux.stopModulesIfRunning(context);
                boolean newOptionValue = Boolean.parseBoolean(newValue.toString());
                modulesStatus.setUseModulesWithRoot(newOptionValue);
                modulesStatus.setContextUIDUpdateRequested(true);
                ModulesAux.makeModulesStateExtraLoop(context);

                Preference fixTTLPreference = findPreference(FIX_TTL);
                if (fixTTLPreference != null) {
                    fixTTLPreference.setEnabled(!newOptionValue);
                }

                logi("PreferencesCommonFragment switch to "
                        + (Boolean.parseBoolean(newValue.toString()) ? "Root" : "No Root"));
                break;
            case FIX_TTL:
                boolean fixed = Boolean.parseBoolean(newValue.toString());
                modulesStatus.setFixTTL(fixed);
                modulesStatus.setIptablesRulesUpdateRequested(context, true);

                activityCurrentRecreate();
                break;
            case MULTI_USER_SUPPORT:
                if (Boolean.parseBoolean(newValue.toString())) {
                    Utils.allowInteractAcrossUsersPermissionIfRequired(context, pathVars.get());
                }
                modulesStatus.setIptablesRulesUpdateRequested(context, true);
                break;
            case "pref_common_local_eth_device_addr":
            case COMPATIBILITY_MODE:
            case DNS_REBIND_PROTECTION:
            case KILL_SWITCH:
                modulesStatus.setIptablesRulesUpdateRequested(context, true);
                break;
            case "swWakelock":
                ModulesAux.requestModulesStatusUpdate(context);
                break;
            case ARP_SPOOFING_DETECTION:
                if (Boolean.parseBoolean(newValue.toString())) {
                    ModulesAux.startArpDetection(context);
                } else {
                    ModulesAux.stopArpDetection(context);
                }
                handler.get().postDelayed(() -> {
                    boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                            && !modulesStatus.isUseModulesWithRoot();
                    if (fixTTL) {
                        //Manually reload the VPN service because setIptablesRulesUpdateRequested does not do this in case of an ARP attack detected
                        ServiceVPNHelper.reload("Internet blocking settings for ARP attacks changed", context);
                    }
                    modulesStatus.setIptablesRulesUpdateRequested(context, true);

                }, ARP_SCANNER_CHANGE_STATE_DELAY_SEC * 1000);
                break;
            case ARP_SPOOFING_BLOCK_INTERNET:
                boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                        && !modulesStatus.isUseModulesWithRoot();
                if (fixTTL) {
                    //Manually reload the VPN service because setIptablesRulesUpdateRequested does not do this in case of an ARP attack detected
                    ServiceVPNHelper.reload("Internet blocking settings for ARP attacks changed", context);
                }
                modulesStatus.setIptablesRulesUpdateRequested(context, true);
                break;
            case REMOTE_CONTROL:
                boolean enabled = Boolean.parseBoolean(newValue.toString());
                modulesStatusBroadcaster.get().onRemoteControlChanged(enabled);
                if (enabled) {
                    showDisableBatteryOptimisationDialog();
                }
                break;
        }
        return true;
    }

    private void showDisableBatteryOptimisationDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        DialogFragment batteryOptimizationDialog = RequestIgnoreBatteryOptimizationDialog.getInstance(
                context, preferenceRepository.get(), true
        );

        FragmentManager fragmentManager = getParentFragmentManager();
        if (batteryOptimizationDialog != null && !fragmentManager.isStateSaved()) {
            batteryOptimizationDialog.show(fragmentManager, RequestIgnoreBatteryOptimizationDialog.TAG);
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        Context context = getActivity();

        if (context == null) {
            return false;
        }

        if ("pref_common_tethering_settings".equals(preference.getKey())) {
            Intent intent_tether = new Intent(Intent.ACTION_MAIN, null);
            intent_tether.addCategory(Intent.CATEGORY_LAUNCHER);
            ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
            intent_tether.setComponent(cn);
            intent_tether.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent_tether);
            } catch (Exception e) {
                loge("PreferencesCommonFragment startHOTSPOT", e);
            }
        } else if (ALWAYS_ON_VPN.equals(preference.getKey())) {
            Intent vpnSettingsIntent = new Intent("android.settings.VPN_SETTINGS");
            try {
                getActivity().startActivity(vpnSettingsIntent);
            } catch (Exception e) {
                loge("PreferencesCommonFragment ALWAYS_ON_VPN", e);
            }
        } else if (USE_PROXY.equals(preference.getKey())) {
            openProxySettings();
        }
        return false;
    }

    private void openProxySettings() {
        Context context = getActivity();

        if (context == null) {
            return;
        }

        Intent intent = new Intent(context, SettingsActivity.class);
        intent.setAction("use_proxy");
        context.startActivity(intent);
    }

    private void activityCurrentRecreate() {

        Activity activity = getActivity();

        if (activity == null || activity.isFinishing()) {
            return;
        }

        Intent intent = activity.getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        activity.overridePendingTransition(0, 0);
        activity.finish();

        activity.overridePendingTransition(0, 0);
        startActivity(intent);

        preferenceRepository.get().setBoolPreference(MAIN_ACTIVITY_RECREATE, true);
    }

    private void readTorConf(Context context) {
        FileManager.readTextFile(context, torConfPath, SettingsActivity.tor_conf_tag);
    }

    private void allowTorTethering(List<String> torConf) {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        SharedPreferences sharedPreferences = defaultPreferences.get();
        boolean isolateDestAddress = sharedPreferences.getBoolean("pref_tor_isolate_dest_address", false);
        boolean isolateDestPort = sharedPreferences.getBoolean("pref_tor_isolate_dest_port", false);

        String line;
        for (int i = 0; i < torConf.size(); i++) {
            line = torConf.get(i);
            if (line.contains("TransPort") && !line.contains(LOOPBACK_ADDRESS_IPv6)) {
                line = "TransPort " + addIsolateFlags(torTransPort, allowTorTether, isolateDestAddress, isolateDestPort);
                torConf.set(i, line);
            } else if (line.contains("SOCKSPort") && !line.contains(LOOPBACK_ADDRESS_IPv6)) {
                line = "SOCKSPort " + addIsolateFlags(torSocksPort, allowTorTether, isolateDestAddress, isolateDestPort);
                torConf.set(i, line);
            } else if (line.contains("HTTPTunnelPort") && !line.contains(LOOPBACK_ADDRESS_IPv6)) {
                line = "HTTPTunnelPort " + addIsolateFlags(torHTTPTunnelPort, allowTorTether, isolateDestAddress, isolateDestPort);
                torConf.set(i, line);
            }
        }

        FileManager.writeToTextFile(context, torConfPath, torConf, "ignored");

        if (ModulesAux.isTorSavedStateRunning()) {
            ModulesRestarter.restartTor(context);
            modulesStatus.setIptablesRulesUpdateRequested(context, true);
        }
    }

    private String addIsolateFlags(String port, boolean allowTorTethering, boolean isolateDestinationAddress, boolean isolateDestinationPort) {
        String value = LOOPBACK_ADDRESS + ":" + port;
        if (allowTorTethering && value.contains(LOOPBACK_ADDRESS)) {
            value = value.replace(LOOPBACK_ADDRESS, META_ADDRESS);
        } else if (allowTorTethering && !value.contains(LOOPBACK_ADDRESS_IPv6)) {
            value = "0.0.0.0:" + value;
        }
        if (isolateDestinationAddress) {
            value += " " + ISOLATE_DEST_ADDRESS;
        }
        if (isolateDestinationPort) {
            value += " " + ISOLATE_DEST_PORT;
        }
        return value;
    }

    private void readITPDConf(Context context) {
        FileManager.readTextFile(context, itpdConfPath, SettingsActivity.itpd_conf_tag);
    }

    private void allowITPDTethering(List<String> itpdConf) {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        String line;
        String head = "";
        for (int i = 0; i < itpdConf.size(); i++) {
            line = itpdConf.get(i);
            if (line.matches("\\[.+]"))
                head = line.replace("[", "").replace("]", "");
            if (head.equals("httpproxy") && line.contains("address")) {
                if (allowITPDtether) {
                    line = line.replace(LOOPBACK_ADDRESS, META_ADDRESS);
                } else {
                    line = line.replace(META_ADDRESS, LOOPBACK_ADDRESS);
                }
                itpdConf.set(i, line);
            } else if (head.equals("socksproxy") && line.contains("address")) {
                if (allowITPDtether) {
                    line = line.replace(LOOPBACK_ADDRESS, META_ADDRESS);
                } else {
                    line = line.replace(META_ADDRESS, LOOPBACK_ADDRESS);
                }
                itpdConf.set(i, line);
            }
        }

        FileManager.writeToTextFile(context, itpdConfPath, itpdConf, "ignored");

        if (ModulesAux.isITPDSavedStateRunning()) {
            ModulesRestarter.restartITPD(context);
            modulesStatus.setIptablesRulesUpdateRequested(context, true);
        }
    }

    private void readITPDTunnelsConf(Context context) {
        FileManager.readTextFile(context, itpdTunnelsPath, SettingsActivity.itpd_tunnels_tag);
    }

    private void allowITPDTunnelsTethering(List<String> itpdTunnels) {

        Context context = getActivity();

        if (getActivity() == null) {
            return;
        }

        String line;
        for (int i = 0; i < itpdTunnels.size(); i++) {
            line = itpdTunnels.get(i);
            if (line.contains("address")) {
                if (allowITPDtether) {
                    line = line.replace(LOOPBACK_ADDRESS, META_ADDRESS);
                } else {
                    line = line.replace(META_ADDRESS, LOOPBACK_ADDRESS);
                }
                itpdTunnels.set(i, line);
            }
        }

        FileManager.writeToTextFile(context, itpdTunnelsPath, itpdTunnels, "ignored");
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation,
                                        boolean fileOperationResult, String path, final String tag, final List<String> lines) {

        if (fileOperationResult && currentFileOperation == readTextFile) {
            if (lines != null) {
                switch (tag) {
                    case SettingsActivity.tor_conf_tag -> allowTorTethering(lines);
                    case SettingsActivity.itpd_conf_tag -> allowITPDTethering(lines);
                    case SettingsActivity.itpd_tunnels_tag -> allowITPDTunnelsTethering(lines);
                }
            }
        }
    }

    private void registerPreferences() {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        Preference swFixTTL = findPreference(FIX_TTL);
        if (swFixTTL != null) {
            swFixTTL.setOnPreferenceChangeListener(this);

            SharedPreferences sharedPreferences = defaultPreferences.get();
            swFixTTL.setEnabled(!sharedPreferences.getBoolean(RUN_MODULES_WITH_ROOT, false));
        }

        Preference prefTorSiteUnlockTether = findPreference("prefTorSiteUnlockTether");
        if (prefTorSiteUnlockTether != null) {
            SharedPreferences shPref = defaultPreferences.get();
            prefTorSiteUnlockTether.setEnabled(!shPref.getBoolean("pref_common_tor_route_all", false));
        }

        if (!defaultPreferences.get().getBoolean(RUN_MODULES_WITH_ROOT, false)) {
            PreferenceScreen preferenceScreen = findPreference("pref_common");
            PreferenceCategory categoryUseModulesRoot = findPreference("categoryUseModulesRoot");
            if (preferenceScreen != null && categoryUseModulesRoot != null) {
                preferenceScreen.removePreference(categoryUseModulesRoot);
            }
        }

        ArrayList<Preference> preferences = new ArrayList<>();
        preferences.add(findPreference(TOR_TETHERING));
        preferences.add(findPreference("pref_common_tor_route_all"));
        preferences.add(findPreference(ITPD_TETHERING));
        preferences.add(findPreference("pref_common_block_http"));
        preferences.add(findPreference(RUN_MODULES_WITH_ROOT));
        preferences.add(findPreference("swWakelock"));
        preferences.add(findPreference("pref_common_local_eth_device_addr"));
        preferences.add(findPreference(KILL_SWITCH));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            }
        }
    }

    private void removePreferences() {
        PreferenceScreen preferenceScreen = findPreference("pref_common");

        if (modulesStatus.getMode() != VPN_MODE) {
            PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");
            if (preferenceScreen != null && hotspotSettingsCategory != null) {
                preferenceScreen.removePreference(hotspotSettingsCategory);
            }
        } else {
            PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");

            ArrayList<Preference> preferencesHOTSPOT = new ArrayList<>();
            preferencesHOTSPOT.add(findPreference("pref_common_tor_route_all"));
            preferencesHOTSPOT.add(findPreference("prefTorSiteUnlockTether"));
            preferencesHOTSPOT.add(findPreference("prefTorSiteExcludeTether"));
            preferencesHOTSPOT.add(findPreference("pref_common_block_http"));
            preferencesHOTSPOT.add(findPreference(FIX_TTL));
            preferencesHOTSPOT.add(findPreference("pref_common_local_eth_device_addr"));

            if (hotspotSettingsCategory != null) {
                for (Preference preference : preferencesHOTSPOT) {
                    if (preference != null) {
                        hotspotSettingsCategory.removePreference(preference);
                    }
                }
            }

            Preference pref_common_tor_tethering = findPreference(TOR_TETHERING);
            if (pref_common_tor_tethering != null) {
                pref_common_tor_tethering.setSummary(String.format(getString(R.string.vpn_tor_tether_summ), pathVars.get().getTorHTTPTunnelPort()));
                pref_common_tor_tethering.setOnPreferenceChangeListener(this);
            }

            Preference pref_common_itpd_tethering = findPreference(ITPD_TETHERING);
            if (pref_common_itpd_tethering != null) {
                pref_common_itpd_tethering.setSummary(String.format(getString(R.string.vpn_tor_tether_summ), pathVars.get().getITPDHttpProxyPort()));
                pref_common_itpd_tethering.setOnPreferenceChangeListener(this);
            }
        }


        if (modulesStatus.isRootAvailable()
                && modulesStatus.getMode() != VPN_MODE
                && defaultPreferences.get().getBoolean(RUN_MODULES_WITH_ROOT, false)) {
            Preference pref_common_use_modules_with_root = findPreference(RUN_MODULES_WITH_ROOT);
            if (pref_common_use_modules_with_root != null) {
                pref_common_use_modules_with_root.setOnPreferenceChangeListener(this);
            }
        } else {
            PreferenceCategory categoryUseModulesRoot = findPreference("categoryUseModulesRoot");
            if (preferenceScreen != null && categoryUseModulesRoot != null) {
                preferenceScreen.removePreference(categoryUseModulesRoot);
            }
        }

        PreferenceCategory categoryOther = findPreference("common_other");
        Preference refreshRules = findPreference(REFRESH_RULES);
        Preference selectIptables = findPreference(USE_IPTABLES);
        Preference waitIptables = findPreference(WAIT_IPTABLES);
        Preference selectBusybox = findPreference("pref_common_use_busybox");
        Preference killSwitch = findPreference(KILL_SWITCH);

        if (categoryOther != null && refreshRules != null) {
            categoryOther.removePreference(refreshRules);
        }
        if (categoryOther != null && selectIptables != null) {
            categoryOther.removePreference(selectIptables);
        }
        if (categoryOther != null && waitIptables != null) {
            categoryOther.removePreference(waitIptables);
        }
        if (categoryOther != null && selectBusybox != null) {
            categoryOther.removePreference(selectBusybox);
        }
        if (categoryOther != null && killSwitch != null) {
            categoryOther.removePreference(killSwitch);
        }
    }

    private void manageLANDeviceAddressPreference(boolean fixTTL) {

        Activity activity = getActivity();
        PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");
        Preference localEthernetDeviceAddress = findPreference("pref_common_local_eth_device_addr");

        if (activity == null || hotspotSettingsCategory == null || localEthernetDeviceAddress == null) {
            return;
        }

        if (!fixTTL
                || Utils.INSTANCE.getScreenOrientation(activity) == Configuration.ORIENTATION_PORTRAIT
                || !Utils.INSTANCE.isLANInterfaceExist()) {
            hotspotSettingsCategory.removePreference(localEthernetDeviceAddress);
        } else {
            String deviceIP = Utils.INSTANCE.getDeviceIP();
            String summary = String.format(getString(R.string.pref_common_local_eth_device_addr_summ), deviceIP, deviceIP);
            localEthernetDeviceAddress.setSummary(summary);
        }


    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        FileManager.deleteOnFileOperationCompleteListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
