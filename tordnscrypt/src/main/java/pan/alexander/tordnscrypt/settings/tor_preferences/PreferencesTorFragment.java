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

package pan.alexander.tordnscrypt.settings.tor_preferences;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.ConfigEditorFragment;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.tor_bridges.SnowflakeConfigurator;
import pan.alexander.tordnscrypt.settings.tor_countries.CountrySelectFragment;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.Constants.HOST_NAME_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX_WITH_MASK;
import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX_WITH_PORT;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX_NO_BOUNDS;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS_IPv6;
import static pan.alexander.tordnscrypt.utils.Constants.MAX_PORT_NUMBER;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.TOR_VIRTUAL_ADDR_NETWORK_IPV6;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DORMANT_CLIENT_TIMEOUT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SNOWFLAKE_RENDEZVOUS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.STUN_SERVERS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_DNS_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_ENTRY_NODES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_FASCIST_FIREWALL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_FASCIST_FIREWALL_LOCK;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_HTTP_TUNNEL_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_ISOLATE_UID;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY_ADDRESS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_SOCKS_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TRANS_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_USE_IPV6;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_DEFAULT_BRIDGES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_OWN_BRIDGES;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

import javax.inject.Inject;
import javax.inject.Named;


public class PreferencesTorFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    public static final String ISOLATE_DEST_ADDRESS = "IsolateDestAddr";
    public static final String ISOLATE_DEST_PORT = "IsolateDestPort";
    private ArrayList<String> key_tor;
    private ArrayList<String> val_tor;
    private ArrayList<String> key_tor_orig;
    private ArrayList<String> val_tor_orig;
    private String appDataDir;
    public String entryNodes;
    public String excludeNodes;
    public String excludeExitNodes;
    public String exitNodes;
    private boolean isChanged;

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CoroutineExecutor executor;
    @Inject
    public Lazy<SnowflakeConfigurator> snowflakeConfigurator;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);

        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        setDefaultStunServers();

        addPreferencesFromResource(R.xml.preferences_tor);

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (pathVars.get().getAppVersion().endsWith("p")) {
            changePreferencesForGPVersion();
        }

        ArrayList<Preference> preferences = new ArrayList<>();

        if (ModulesStatus.getInstance().getMode() == VPN_MODE) {
            preferences.add(findPreference(TOR_ISOLATE_UID));
        } else {
            PreferenceCategory isolationCategory = findPreference("tor_isolation_settings");
            Preference isolateUid = findPreference(TOR_ISOLATE_UID);
            if (isolationCategory != null && isolateUid != null) {
                isolationCategory.removePreference(isolateUid);
            }
        }

        preferences.add(findPreference("VirtualAddrNetwork"));
        preferences.add(findPreference("HardwareAccel"));
        preferences.add(findPreference("AvoidDiskWrites"));
        preferences.add(findPreference("ConnectionPadding"));
        preferences.add(findPreference("ReducedConnectionPadding"));
        preferences.add(findPreference("ExcludeExitNodes"));
        preferences.add(findPreference("ExitNodes"));
        preferences.add(findPreference("ExcludeNodes"));
        preferences.add(findPreference("StrictNodes"));
        preferences.add(findPreference(TOR_FASCIST_FIREWALL));
        preferences.add(findPreference("NewCircuitPeriod"));
        preferences.add(findPreference("MaxCircuitDirtiness"));
        preferences.add(findPreference("EnforceDistinctSubnets"));
        preferences.add(findPreference("Enable SOCKS proxy"));
        preferences.add(findPreference(TOR_SOCKS_PORT));
        preferences.add(findPreference("Enable HTTPTunnel"));
        preferences.add(findPreference(TOR_HTTP_TUNNEL_PORT));
        preferences.add(findPreference("Enable Transparent proxy"));
        preferences.add(findPreference(TOR_TRANS_PORT));
        preferences.add(findPreference("Enable DNS"));
        preferences.add(findPreference(TOR_DNS_PORT));
        preferences.add(findPreference("ClientUseIPv4"));
        preferences.add(findPreference(TOR_USE_IPV6));
        preferences.add(findPreference(STUN_SERVERS));
        preferences.add(findPreference(TOR_OUTBOUND_PROXY));
        preferences.add(findPreference(TOR_OUTBOUND_PROXY_ADDRESS));
        preferences.add(findPreference("pref_tor_isolate_dest_address"));
        preferences.add(findPreference("pref_tor_isolate_dest_port"));
        preferences.add(findPreference(SNOWFLAKE_RENDEZVOUS));
        preferences.add(findPreference("Enable TrackHostExits"));
        preferences.add(findPreference(DORMANT_CLIENT_TIMEOUT));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            } else if (!pathVars.get().getAppVersion().startsWith("g")) {
                loge("PreferencesTorFragment preference is null exception");
            }
        }


        Preference entryNodesPref = findPreference(TOR_ENTRY_NODES);
        boolean useDefaultBridges = preferenceRepository.get().getBoolPreference(USE_DEFAULT_BRIDGES);
        boolean useOwnBridges = preferenceRepository.get().getBoolPreference(USE_OWN_BRIDGES);
        boolean entryNodesActive = defaultPreferences.get().getBoolean(TOR_ENTRY_NODES, false);
        if (entryNodesPref != null) {
            if (useDefaultBridges || useOwnBridges) {
                if (entryNodesActive) {
                    entryNodesPref.setOnPreferenceChangeListener(this);
                } else {
                    entryNodesPref.setEnabled(false);
                }
                entryNodesPref.setSummary(R.string.pref_tor_entry_nodes_alt_summ);
            } else {
                entryNodesPref.setOnPreferenceChangeListener(this);
            }
        }

        Preference fascistFirewall = findPreference(TOR_FASCIST_FIREWALL);
        boolean fascistFirewallLock = preferenceRepository.get().getBoolPreference(TOR_FASCIST_FIREWALL_LOCK);
        if (fascistFirewall != null && fascistFirewallLock) {
            fascistFirewall.setSummary(R.string.pref_tor_fascist_firewall_lock_summ);
            fascistFirewall.setEnabled(false);
        }

        Preference editTorConfDirectly = findPreference("editTorConfDirectly");
        if (editTorConfDirectly != null) {
            editTorConfDirectly.setOnPreferenceClickListener(this);
        }

        Preference cleanTorFolder = findPreference("cleanTorFolder");
        if (cleanTorFolder != null) {
            cleanTorFolder.setOnPreferenceClickListener(this);
        }

        entryNodes = null;
        excludeNodes = null;
        excludeExitNodes = null;
        exitNodes = null;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        activity.setTitle(R.string.drawer_menu_TorSettings);

        appDataDir = pathVars.get().getAppDataDir();

        isChanged = false;

        if (getArguments() != null) {
            key_tor = getArguments().getStringArrayList("key_tor");
            val_tor = getArguments().getStringArrayList("val_tor");
            key_tor_orig = new ArrayList<>(key_tor);
            val_tor_orig = new ArrayList<>(val_tor);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (key_tor == null || val_tor == null
                || key_tor_orig == null || val_tor_orig == null) {
            return;
        }

        if (entryNodes != null && key_tor.contains("EntryNodes")) {
            val_tor.set(key_tor.indexOf("EntryNodes"), entryNodes);
            entryNodes = null;
        } else if (entryNodes != null && key_tor.contains("#EntryNodes")) {
            val_tor.set(key_tor.indexOf("#EntryNodes"), entryNodes);
            entryNodes = null;
        } else if (excludeNodes != null && key_tor.contains("ExcludeNodes")) {
            val_tor.set(key_tor.indexOf("ExcludeNodes"), excludeNodes);
            excludeNodes = null;
        } else if (excludeNodes != null && key_tor.contains("#ExcludeNodes")) {
            val_tor.set(key_tor.indexOf("#ExcludeNodes"), excludeNodes);
            excludeNodes = null;
        } else if (excludeExitNodes != null && key_tor.contains("ExcludeExitNodes")) {
            val_tor.set(key_tor.indexOf("ExcludeExitNodes"), excludeExitNodes);
            excludeExitNodes = null;
        } else if (excludeExitNodes != null && key_tor.contains("#ExcludeExitNodes")) {
            val_tor.set(key_tor.indexOf("#ExcludeExitNodes"), excludeExitNodes);
            excludeExitNodes = null;
        } else if (exitNodes != null && key_tor.contains("ExitNodes")) {
            val_tor.set(key_tor.indexOf("ExitNodes"), exitNodes);
            exitNodes = null;
        } else if (exitNodes != null && key_tor.contains("#ExitNodes")) {
            val_tor.set(key_tor.indexOf("#ExitNodes"), exitNodes);
            exitNodes = null;
        }

        List<String> tor_conf = new LinkedList<>();
        for (int i = 0; i < key_tor.size(); i++) {

            if (!isChanged
                    && (key_tor_orig.size() != key_tor.size()
                    || !key_tor_orig.get(i).equals(key_tor.get(i))
                    || !val_tor_orig.get(i).equals(val_tor.get(i)))) {
                isChanged = true;
            }

            if (!key_tor.get(i).isEmpty() && val_tor.get(i).isEmpty()) {
                tor_conf.add(key_tor.get(i));
            } else if (!key_tor.get(i).isEmpty()) {
                String val = val_tor.get(i);
                if (val.equals("true")) val = "1";
                if (val.equals("false")) val = "0";
                tor_conf.add(key_tor.get(i) + " " + val);
            }

        }

        if (!isChanged) {
            return;
        }

        FileManager.writeToTextFile(context, appDataDir + "/app_data/tor/tor.conf", tor_conf, SettingsActivity.tor_conf_tag);

        boolean torRunning = ModulesAux.isTorSavedStateRunning();

        if (torRunning) {
            ModulesRestarter.restartTor(context);
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
        }

    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {

        Context context = getActivity();
        if (context == null || key_tor == null || val_tor == null) {
            return false;
        }

        try {
            return tryPreferenceChange(context, preference, newValue);
        } catch (Exception e) {
            loge("PreferencesTorFragment onPreferenceChange", e);
            Toast.makeText(context, R.string.wrong, Toast.LENGTH_LONG).show();
        }

        return false;
    }

    private boolean tryPreferenceChange(
            @NonNull Context context,
            @NonNull Preference preference,
            Object newValue
    ) {
        SharedPreferences sharedPreferences = defaultPreferences.get();
        boolean isolateDestAddress = sharedPreferences.getBoolean("pref_tor_isolate_dest_address", false);
        boolean isolateDestPort = sharedPreferences.getBoolean("pref_tor_isolate_dest_port", false);
        boolean allowTorTethering = sharedPreferences.getBoolean(TOR_TETHERING, false);

        if (Objects.equals(preference.getKey(), "ExcludeExitNodes")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#ExcludeExitNodes")) {
                key_tor.set(key_tor.indexOf("#ExcludeExitNodes"), "ExcludeExitNodes");
                openCountrySelectFragment(CountrySelectFragment.excludeExitNodes, "ExcludeExitNodes");
            } else if (key_tor.contains("ExcludeExitNodes")) {
                key_tor.set(key_tor.indexOf("ExcludeExitNodes"), "#ExcludeExitNodes");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "ExitNodes")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#ExitNodes")) {
                key_tor.set(key_tor.indexOf("#ExitNodes"), "ExitNodes");
                openCountrySelectFragment(CountrySelectFragment.exitNodes, "ExitNodes");
            } else if (key_tor.contains("ExitNodes")) {
                key_tor.set(key_tor.indexOf("ExitNodes"), "#ExitNodes");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "ExcludeNodes")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#ExcludeNodes")) {
                key_tor.set(key_tor.indexOf("#ExcludeNodes"), "ExcludeNodes");
                openCountrySelectFragment(CountrySelectFragment.excludeNodes, "ExcludeNodes");
            } else if (key_tor.contains("ExcludeNodes")) {
                key_tor.set(key_tor.indexOf("ExcludeNodes"), "#ExcludeNodes");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), TOR_ENTRY_NODES)) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#EntryNodes")) {
                key_tor.set(key_tor.indexOf("#EntryNodes"), "EntryNodes");
                openCountrySelectFragment(CountrySelectFragment.entryNodes, TOR_ENTRY_NODES);
            } else if (key_tor.contains("EntryNodes")) {
                key_tor.set(key_tor.indexOf("EntryNodes"), "#EntryNodes");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "HardwareAccel")) {
            if (Boolean.parseBoolean(newValue.toString())
                    && !key_tor.contains("HardwareAccel") && key_tor.contains("Schedulers")) {
                key_tor.add(key_tor.indexOf("Schedulers"), "HardwareAccel");
                val_tor.add(key_tor.indexOf("HardwareAccel"), newValue.toString());
            }
        } else if (Objects.equals(preference.getKey(), "Enable SOCKS proxy")) {
            boolean enable = Boolean.parseBoolean(newValue.toString());
            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                if (enable && key.equals("#SOCKSPort")) {
                    key_tor.set(i, "SOCKSPort");
                } else if (!enable && key.equals("SOCKSPort")) {
                    key_tor.set(i, "#SOCKSPort");
                }
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Enable HTTPTunnel")) {
            boolean enable = Boolean.parseBoolean(newValue.toString());
            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                if (enable && key.equals("#HTTPTunnelPort")) {
                    key_tor.set(i, "HTTPTunnelPort");
                } else if (!enable && key.equals("HTTPTunnelPort")) {
                    key_tor.set(i, "#HTTPTunnelPort");
                }
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Enable Transparent proxy")) {
            boolean enable = Boolean.parseBoolean(newValue.toString());
            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                if (enable && key.equals("#TransPort")) {
                    key_tor.set(i, "TransPort");
                } else if (!enable && key.equals("TransPort")) {
                    key_tor.set(i, "#TransPort");
                }
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Enable DNS")) {
            boolean enable = Boolean.parseBoolean(newValue.toString());
            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                if (enable && key.equals("#DNSPort")) {
                    key_tor.set(i, "DNSPort");
                } else if (!enable && key.equals("DNSPort")) {
                    key_tor.set(i, "#DNSPort");
                }
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Enable TrackHostExits")) {
            boolean enable = Boolean.parseBoolean(newValue.toString());
            if (!key_tor.contains("TrackHostExits") && !key_tor.contains("#TrackHostExits")) {
                int index = key_tor.indexOf("ConnectionPadding");
                if (index > 0) {
                    key_tor.add(index, "TrackHostExits");
                    val_tor.add(index, ".");
                }
            }
            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                if (enable && key.equals("#TrackHostExits")) {
                    key_tor.set(i, "TrackHostExits");
                } else if (!enable && key.equals("TrackHostExits")) {
                    key_tor.set(i, "#TrackHostExits");
                }
            }
            return true;
        } else if (Objects.equals(preference.getKey(), TOR_DNS_PORT)) {
            String dnsPort = newValue.toString();

            boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                    && ModulesStatus.getInstance().isUseModulesWithRoot();
            if (!dnsPort.matches("^\\d{1,5}$") || Long.parseLong(newValue.toString()) > MAX_PORT_NUMBER
                    || (!useModulesWithRoot && Integer.parseInt(newValue.toString()) < 1024)) {
                return false;
            }

            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                String val = val_tor.get(i);
                if (key.equals("DNSPort") && val.contains(LOOPBACK_ADDRESS)) {
                    val_tor.set(i, LOOPBACK_ADDRESS + ":" + dnsPort);
                } else if (key.equals("DNSPort") && val.contains(LOOPBACK_ADDRESS_IPv6)) {
                    val_tor.set(i, "[" + LOOPBACK_ADDRESS_IPv6 + "]:" + dnsPort);
                } else if (key.equals("DNSPort")) {
                    val_tor.set(i, dnsPort);
                }
            }

            ModifyForwardingRules modifyForwardingRules = new ModifyForwardingRules(context,
                    "onion 127.0.0.1:" + dnsPort.trim());
            executor.submit("PreferencesTorFragment modifyForwardingRules", () -> {
                modifyForwardingRules.getRunnable().run();
                return null;
            });
            return true;
        } else if (Objects.equals(preference.getKey(), SNOWFLAKE_RENDEZVOUS)) {
            for (int i = 0; i < key_tor.size(); i++) {
                if (key_tor.get(i).contains("Bridge") && val_tor.get(i).contains("snowflake")) {
                    String bridgeBaseRegex = getBridgeBaseRegex(val_tor.get(i));
                    Pattern bridgePattern = Pattern.compile("snowflake +" + bridgeBaseRegex + "( +fingerprint=\\w+)?");
                    Matcher matcher = bridgePattern.matcher(val_tor.get(i));
                    if (matcher.find()) {
                        String bridgeBase = matcher.group();
                        val_tor.set(i, snowflakeConfigurator.get()
                                .getConfiguration(bridgeBase, Integer.parseInt(newValue.toString())));
                    } else {
                        return false;
                    }
                }
            }
            return true;
        } else if (Objects.equals(preference.getKey(), STUN_SERVERS)) {

            String serversStr = newValue.toString().trim();

            if (serversStr.isEmpty()) {
                serversStr = TextUtils.join(
                        ",", context.getResources().getStringArray(R.array.tor_snowflake_stun_servers)
                );
                updateCurrentSnowflakeBridges(serversStr);
                sharedPreferences.edit().putString(STUN_SERVERS, serversStr).apply();
                return false;
            }

            String[] servers = serversStr.split(", ?");
            for (String server : servers) {
                if (!server.matches(HOST_NAME_REGEX + ":\\d{1,5}$")) {
                    return false;
                }
            }

            updateCurrentSnowflakeBridges(serversStr);

            return true;
        } else if (Objects.equals(preference.getKey(), TOR_SOCKS_PORT)
                || Objects.equals(preference.getKey(), TOR_HTTP_TUNNEL_PORT)
                || Objects.equals(preference.getKey(), TOR_TRANS_PORT)) {

            String proxyPort = newValue.toString();
            String proxyType = preference.getKey();

            boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                    && ModulesStatus.getInstance().isUseModulesWithRoot();
            if (!proxyPort.matches("^\\d{1,5}$") || Long.parseLong(newValue.toString()) > MAX_PORT_NUMBER
                    || (!useModulesWithRoot && Integer.parseInt(proxyPort) < 1024)) {
                return false;
            }

            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                String val = val_tor.get(i);

                if (key.equals(proxyType) && val.contains(LOOPBACK_ADDRESS)) {
                    String proxyLine = LOOPBACK_ADDRESS + ":" + proxyPort;
                    val_tor.set(i, addIsolateFlags(
                            proxyLine,
                            allowTorTethering,
                            isolateDestAddress,
                            isolateDestPort));
                } else if (key.equals(proxyType) && val.contains(LOOPBACK_ADDRESS_IPv6)) {
                    String proxyLine = "[" + LOOPBACK_ADDRESS_IPv6 + "]:" + proxyPort;
                    val_tor.set(i, addIsolateFlags(
                            proxyLine,
                            allowTorTethering,
                            isolateDestAddress,
                            isolateDestPort));
                } else if (key.equals(proxyType)) {
                    val_tor.set(i, addIsolateFlags(
                            proxyPort,
                            allowTorTethering,
                            isolateDestAddress,
                            isolateDestPort));
                }
            }

            return true;
        } else if (Objects.equals(preference.getKey(), TOR_ISOLATE_UID)) {
            isChanged = true;
            return true;
        } else if (Objects.equals(preference.getKey(), "pref_tor_isolate_dest_address")) {

            boolean isolate = Boolean.parseBoolean(newValue.toString());

            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                String val = val_tor.get(i);

                String proxyType = switch (key) {
                    case "SOCKSPort" -> "SOCKSPort";
                    case "HTTPTunnelPort" -> "HTTPTunnelPort";
                    case "TransPort" -> "TransPort";
                    default -> "";
                };

                if (proxyType.isEmpty()) {
                    continue;
                }

                String proxyPort = val.split(" ")[0]
                        .replaceAll(".+:", "")
                        .replaceAll("\\D+", "");

                if (val.contains(LOOPBACK_ADDRESS)) {
                    String proxyLine = LOOPBACK_ADDRESS + ":" + proxyPort;
                    val_tor.set(i, addIsolateFlags(
                            proxyLine,
                            allowTorTethering,
                            isolate,
                            isolateDestPort));
                } else if (val.contains(LOOPBACK_ADDRESS_IPv6)) {
                    String proxyLine = "[" + LOOPBACK_ADDRESS_IPv6 + "]:" + proxyPort;
                    val_tor.set(i, addIsolateFlags(
                            proxyLine,
                            allowTorTethering,
                            isolate,
                            isolateDestPort));
                } else {
                    val_tor.set(i, addIsolateFlags(
                            proxyPort,
                            allowTorTethering,
                            isolate,
                            isolateDestPort));
                }
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "pref_tor_isolate_dest_port")) {

            boolean isolate = Boolean.parseBoolean(newValue.toString());

            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                String val = val_tor.get(i);

                String proxyType = switch (key) {
                    case "SOCKSPort" -> "SOCKSPort";
                    case "HTTPTunnelPort" -> "HTTPTunnelPort";
                    case "TransPort" -> "TransPort";
                    default -> "";
                };

                if (proxyType.isEmpty()) {
                    continue;
                }

                String proxyPort = val.split(" ")[0]
                        .replaceAll(".+:", "")
                        .replaceAll("\\D+", "");

                if (val.contains(LOOPBACK_ADDRESS)) {
                    String proxyLine = LOOPBACK_ADDRESS + ":" + proxyPort;
                    val_tor.set(i, addIsolateFlags(
                            proxyLine,
                            allowTorTethering,
                            isolateDestAddress,
                            isolate));
                } else if (val.contains(LOOPBACK_ADDRESS_IPv6)) {
                    String proxyLine = "[" + LOOPBACK_ADDRESS_IPv6 + "]:" + proxyPort;
                    val_tor.set(i, addIsolateFlags(
                            proxyLine,
                            allowTorTethering,
                            isolateDestAddress,
                            isolate));
                } else {
                    val_tor.set(i, addIsolateFlags(
                            proxyPort,
                            allowTorTethering,
                            isolateDestAddress,
                            isolate));
                }
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "VirtualAddrNetwork")) {

            String value = newValue.toString();

            if (!value.matches(IPv4_REGEX_WITH_MASK)) {
                return false;
            }

            int i = key_tor.indexOf("VirtualAddrNetwork");
            if (i >= 0) {
                key_tor.set(i, "VirtualAddrNetworkIPv4");
            }

            int k = key_tor.indexOf("VirtualAddrNetworkIPv4");
            if (k >= 0) {
                val_tor.set(k, value);
            }

            return true;
        } else if ((Objects.equals(preference.getKey(), "NewCircuitPeriod")
                || Objects.equals(preference.getKey(), "MaxCircuitDirtiness"))
                && !newValue.toString().matches("\\d+")) {
            return false;
        } else if ((Objects.equals(preference.getKey(), DORMANT_CLIENT_TIMEOUT))) {
            if (newValue.toString().matches("\\d+")
                    && Long.parseLong(newValue.toString()) <= Integer.MAX_VALUE) {
                int value = Integer.parseInt(newValue.toString());
                if (value < 10) {
                    return false;
                }
                int i = key_tor.indexOf("DormantCanceledByStartup");
                if (!key_tor.contains("DormantClientTimeout") && i >= 0) {
                    key_tor.add(i + 1, "DormantClientTimeout");
                    val_tor.add(i + 1, newValue + " minutes");
                }
                int k = key_tor.indexOf("DormantClientTimeout");
                if (k >= 0) {
                    val_tor.set(k, newValue + " minutes");
                }
                return true;
            } else {
                return false;
            }
        } else if ((Objects.equals(preference.getKey(), TOR_OUTBOUND_PROXY))) {
            if (Boolean.parseBoolean(newValue.toString())) {
                if (key_tor.contains("#Socks5Proxy")) {
                    key_tor.set(key_tor.indexOf("#Socks5Proxy"), "Socks5Proxy");
                } else if (key_tor.contains("ClientOnly") && !key_tor.contains("Socks5Proxy")) {
                    int index = key_tor.indexOf("ClientOnly");
                    key_tor.add(index, "Socks5Proxy");
                    val_tor.add(index, "127.0.0.1:1080");
                }
            } else if (key_tor.contains("Socks5Proxy")) {
                key_tor.set(key_tor.indexOf("Socks5Proxy"), "#Socks5Proxy");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Socks5Proxy")
                && !newValue.toString().matches(IPv4_REGEX_WITH_PORT)) {
            return false;
        } else if (Objects.equals(preference.getKey(), "ClientUseIPv4") && !Boolean.parseBoolean(newValue.toString())) {
            if (!defaultPreferences.get().getBoolean(TOR_USE_IPV6, true)) {
                return false;
            }
        } else if (Objects.equals(preference.getKey(), TOR_USE_IPV6)) {

            if (!defaultPreferences.get().getBoolean("ClientUseIPv4", true)
                    && !Boolean.parseBoolean(newValue.toString())) {
                return false;
            }

            boolean useIPv6 = Boolean.parseBoolean(newValue.toString());

            for (int i = 0; i < key_tor.size(); i++) {
                String key = key_tor.get(i);
                String val = val_tor.get(i);

                if (useIPv6 && key.equals("SOCKSPort") && !val.contains(LOOPBACK_ADDRESS_IPv6)) {
                    String proxyLine = LOOPBACK_ADDRESS + ":" + pathVars.get().getTorSOCKSPort();
                    val_tor.set(i, addIsolateFlags(
                            proxyLine,
                            allowTorTethering,
                            isolateDestAddress,
                            isolateDestPort));
                    if (i < key_tor.size() - 1 && !key_tor.get(i + 1).equals("SOCKSPort")) {
                        key_tor.add(i + 1, "SOCKSPort");
                        proxyLine = "[" + LOOPBACK_ADDRESS_IPv6 + "]:" + pathVars.get().getTorSOCKSPort();
                        val_tor.add(i + 1, addIsolateFlags(
                                proxyLine,
                                allowTorTethering,
                                isolateDestAddress,
                                isolateDestPort));
                    }
                } else if (!useIPv6 && key.equals("SOCKSPort") && val.contains(LOOPBACK_ADDRESS_IPv6)) {
                    key_tor.set(i, "");
                    val_tor.set(i, "");
                } else if (useIPv6 && key.equals("DNSPort") && !val.contains(LOOPBACK_ADDRESS_IPv6)) {
                    val_tor.set(i, LOOPBACK_ADDRESS + ":" + pathVars.get().getTorDNSPort());
                    if (i < key_tor.size() - 1 && !key_tor.get(i + 1).equals("DNSPort")) {
                        key_tor.add(i + 1, "DNSPort");
                        val_tor.add(
                                i + 1,
                                "[" + LOOPBACK_ADDRESS_IPv6 + "]:" + pathVars.get().getTorDNSPort()
                        );
                    }
                } else if (!useIPv6 && key.equals("DNSPort") && val.contains(LOOPBACK_ADDRESS_IPv6)) {
                    key_tor.set(i, "");
                    val_tor.set(i, "");
                }
            }

            if (useIPv6
                    && key_tor.contains("VirtualAddrNetwork")
                    && !key_tor.contains("VirtualAddrNetworkIPv6")) {
                int index = key_tor.indexOf("VirtualAddrNetwork");
                key_tor.set(index, "VirtualAddrNetworkIPv4");
                key_tor.add(index + 1, "VirtualAddrNetworkIPv6");
                val_tor.add(index + 1, TOR_VIRTUAL_ADDR_NETWORK_IPV6);
            } else if (useIPv6
                    && key_tor.contains("VirtualAddrNetworkIPv4")
                    && !key_tor.contains("VirtualAddrNetworkIPv6")) {
                int index = key_tor.indexOf("VirtualAddrNetworkIPv4");
                key_tor.add(index + 1, "VirtualAddrNetworkIPv6");
                val_tor.add(index + 1, TOR_VIRTUAL_ADDR_NETWORK_IPV6);
            } else if (!useIPv6 && key_tor.contains("VirtualAddrNetworkIPv6")) {
                int index = key_tor.indexOf("VirtualAddrNetworkIPv6");
                key_tor.set(index, "");
                val_tor.set(index, "");
            }
        } else if (Objects.equals(preference.getKey(), TOR_FASCIST_FIREWALL)) {
            if (Boolean.parseBoolean(newValue.toString())) {
                int indexReachableAddresses = key_tor.indexOf("#ReachableAddresses");
                if (indexReachableAddresses >= 0) {
                    key_tor.set(indexReachableAddresses, "ReachableAddresses");
                } else if (!key_tor.contains("ReachableAddresses")) {
                    int indexFascistFirewall = key_tor.indexOf("FascistFirewall");
                    if (indexFascistFirewall >= 0) {
                        key_tor.set(indexFascistFirewall, "ReachableAddresses");
                        val_tor.set(indexFascistFirewall, "*:80,*:443");
                    }
                }
            } else {
                int indexReachableAddresses = key_tor.indexOf("ReachableAddresses");
                if (indexReachableAddresses >= 0) {
                    key_tor.set(indexReachableAddresses, "#ReachableAddresses");
                } else if (!key_tor.contains("#ReachableAddresses")) {
                    int indexFascistFirewall = key_tor.indexOf("FascistFirewall");
                    if (indexFascistFirewall >= 0) {
                        key_tor.set(indexFascistFirewall, "#ReachableAddresses");
                        val_tor.set(indexFascistFirewall, "*:80,*:443");
                    }
                }
            }
            return true;
        }

        if (key_tor.contains(preference.getKey().trim())) {
            val_tor.set(key_tor.indexOf(preference.getKey()), newValue.toString());
            return true;
        } else {
            Toast.makeText(context, R.string.pref_tor_not_exist, Toast.LENGTH_SHORT).show();
        }


        return false;
    }

    private String addIsolateFlags(Object val, boolean allowTorTethering, boolean isolateDestinationAddress, boolean isolateDestinationPort) {
        String value = val.toString();
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

    private void openCountrySelectFragment(int nodesType, String keyStr) {
        if (!isAdded() || key_tor == null || val_tor == null) {
            return;
        }

        FragmentTransaction fTrans = getParentFragmentManager().beginTransaction();
        fTrans.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        Fragment frg = new CountrySelectFragment();
        Bundle bndl = new Bundle();
        bndl.putInt("nodes_type", nodesType);
        bndl.putString("countries", val_tor.get(key_tor.indexOf(keyStr)));
        frg.setArguments(bndl);
        fTrans.replace(R.id.fragment_container, frg, "CountrySelectFragment");
        fTrans.addToBackStack("CountrySelectFragmentTag");
        fTrans.commit();
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        Context context = getActivity();
        if (context == null || !isAdded()) {
            return false;
        }

        if ("cleanTorFolder".equals(preference.getKey())) {

            if (ModulesStatus.getInstance().getTorState() != STOPPED) {
                Toast.makeText(context, R.string.btnTorStop, Toast.LENGTH_SHORT).show();
                return true;
            }

            executor.submit("PreferencesTorFragment cleanTorFolder", () -> {
                Activity activity = getActivity();
                if (activity == null) {
                    return null;
                }

                boolean successfully = FileManager.deleteDirSynchronous(activity, appDataDir + "/tor_data");

                if (successfully) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show());
                } else {
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.wrong, Toast.LENGTH_SHORT).show());
                }
                return null;
            });


            return true;
        } else if ("editTorConfDirectly".equals(preference.getKey())) {
            ConfigEditorFragment.openEditorFragment(getParentFragmentManager(), "tor.conf");
            return true;
        }
        return false;
    }

    private void changePreferencesForGPVersion() {
        removePreferencesFromTorSettingsCategory();

        removeDependenciesFromTorProxySettingsCategory();

        removePreferencesFromTorProxySettingsCategory();

        removePreferencesFromTorSnowflakeCategory();

        removePreferencesFromTorOtherCategory();
    }

    private void removePreferencesFromTorSettingsCategory() {
        PreferenceCategory category = findPreference("tor_settings");
        if (category != null) {
            ArrayList<Preference> preferences = new ArrayList<>();
            preferences.add(findPreference("AvoidDiskWrites"));
            preferences.add(findPreference("ConnectionPadding"));
            preferences.add(findPreference("ReducedConnectionPadding"));

            for (Preference preference : preferences) {
                if (preference != null) {
                    category.removePreference(preference);
                }
            }
        }
    }

    private void removeDependenciesFromTorProxySettingsCategory() {
        ArrayList<Preference> preferences = new ArrayList<>();
        preferences.add(findPreference("SOCKSPort"));
        preferences.add(findPreference("HTTPTunnelPort"));
        preferences.add(findPreference("TransPort"));
        preferences.add(findPreference("DNSPort"));
        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setDependency(null);
            }
        }
    }

    private void removePreferencesFromTorProxySettingsCategory() {
        PreferenceCategory category = findPreference("tor_proxy_settings");
        if (category != null) {
            ArrayList<Preference> preferences = new ArrayList<>();
            preferences.add(findPreference("Enable SOCKS proxy"));
            preferences.add(findPreference("Enable HTTPTunnel"));
            preferences.add(findPreference("Enable Transparent proxy"));
            preferences.add(findPreference("Enable DNS"));

            for (Preference preference : preferences) {
                if (preference != null) {
                    category.removePreference(preference);
                }
            }
        }
    }

    private void removePreferencesFromTorSnowflakeCategory() {
        PreferenceCategory category = findPreference("pref_tor_snowflake_categ");
        Preference preference = findPreference("pref_tor_snowflake_stun");
        if (category != null && preference != null) {
            category.removePreference(preference);
        }
    }

    private void removePreferencesFromTorOtherCategory() {
        PreferenceCategory otherCategory = findPreference("pref_tor_other");
        Preference editTorConfDirectly = findPreference("editTorConfDirectly");
        if (otherCategory != null && editTorConfDirectly != null) {
            otherCategory.removePreference(editTorConfDirectly);
        }
    }

    private void setDefaultStunServers() {
        if (defaultPreferences.get().getString(STUN_SERVERS, "").isEmpty()) {
            String defaultServers = TextUtils.join(
                    ",", getResources().getStringArray(R.array.tor_snowflake_stun_servers)
            );
            defaultPreferences.get().edit().putString(STUN_SERVERS, defaultServers).apply();
        }
    }

    private void updateCurrentSnowflakeBridges(String servers) {

        boolean useDefaultBridges = preferenceRepository.get().getBoolPreference(USE_DEFAULT_BRIDGES);
        if (!useDefaultBridges) {
            return;
        }

        for (int i = 0; i < key_tor.size(); i++) {
            if (key_tor.get(i).contains("Bridge") && val_tor.get(i).contains("snowflake")) {
                String bridgeBaseRegex = getBridgeBaseRegex(val_tor.get(i));
                Pattern bridgePattern = Pattern.compile("snowflake +" + bridgeBaseRegex + "( +fingerprint=\\w+)?");
                Matcher matcher = bridgePattern.matcher(val_tor.get(i));
                if (matcher.find()) {
                    String bridgeBase = matcher.group();
                    val_tor.set(i, snowflakeConfigurator.get()
                            .getConfiguration(bridgeBase, servers));
                }
            }
        }
    }

    private String getBridgeBaseRegex(String bridge) {
        String ipv4BridgeBase = "(\\d{1,3}\\.){3}\\d{1,3}:\\d+( +\\w+)?";
        String ipv6BridgeBase = "\\[" + IPv6_REGEX_NO_BOUNDS + "]" + ":\\d+( +\\w+)?";

        if (bridge.contains("[") && bridge.contains("]")) {
            return ipv6BridgeBase;
        } else {
            return ipv4BridgeBase;
        }
    }
}
