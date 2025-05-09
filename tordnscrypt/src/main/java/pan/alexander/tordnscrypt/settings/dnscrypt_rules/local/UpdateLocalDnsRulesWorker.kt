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

package pan.alexander.tordnscrypt.settings.dnscrypt_rules.local

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.UpdateLocalRulesWorkManager.Companion.LOCAL_RULES_PATH_ARG
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.UpdateLocalRulesWorkManager.Companion.LOCAL_RULES_TYPE_ARG
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.UpdateLocalRulesWorkManager.Companion.LOCAL_RULES_URI_ARG
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote.UpdateRemoteRulesWorkManager.Companion.REFRESH_REMOTE_DNS_BLACKLIST_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote.UpdateRemoteRulesWorkManager.Companion.REFRESH_REMOTE_DNS_CLOAKING_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote.UpdateRemoteRulesWorkManager.Companion.REFRESH_REMOTE_DNS_FORWARDING_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote.UpdateRemoteRulesWorkManager.Companion.REFRESH_REMOTE_DNS_IP_BLACKLIST_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote.UpdateRemoteRulesWorkManager.Companion.REFRESH_REMOTE_DNS_WHITELIST_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_BLACKLIST_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_CLOAKING_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_FORWARDING_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_IP_BLACKLIST_WORK
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_WHITELIST_WORK
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import javax.inject.Inject
import javax.inject.Named

class UpdateLocalDnsRulesWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    init {
        App.instance.daggerComponent.inject(this)
    }

    @Inject
    @Named(CoroutinesModule.DISPATCHER_IO)
    lateinit var dispatcherIo: CoroutineDispatcher

    override suspend fun doWork(): Result {
        try {
            val ruleType = inputData.getString(LOCAL_RULES_TYPE_ARG)?.let {
                DnsRuleType.valueOf(it)
            } ?: return Result.failure()
            val filesWithPath = inputData.getStringArray(LOCAL_RULES_PATH_ARG)
            val filesWithUri = inputData.getStringArray(LOCAL_RULES_URI_ARG)
            val files = filesWithPath ?: (filesWithUri?.map { Uri.parse(it) }?.toTypedArray()
                ?: return Result.failure())

            while (isRemoteDnsRulesImportingInProgress(ruleType)
                || isMixDnsRulesInProgress(ruleType)
            ) {
                delay(500)
            }

            importRulesFromFiles(files, ruleType)

            return Result.success()
        } catch (e: Exception) {
            loge("UpdateLocalDnsRulesWorker doWork", e)
        }
        return Result.failure()
    }

    private suspend fun importRulesFromFiles(
        files: Array<*>,
        ruleType: DnsRuleType,
    ) = runInterruptible(dispatcherIo) {
        ImportRulesManager(
            context = appContext,
            rulesVariant = ruleType,
            importType = ImportRulesManager.ImportType.LOCAL_RULES,
            filePathToImport = files
        ).run()
    }

    private fun isRemoteDnsRulesImportingInProgress(type: DnsRuleType): Boolean =
        WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(getRemoteWorkName(type)).get()
            .firstOrNull()?.state == WorkInfo.State.RUNNING


    private fun getRemoteWorkName(type: DnsRuleType) =
        when (type) {
            DnsRuleType.BLACKLIST -> REFRESH_REMOTE_DNS_BLACKLIST_WORK
            DnsRuleType.WHITELIST -> REFRESH_REMOTE_DNS_WHITELIST_WORK
            DnsRuleType.IP_BLACKLIST -> REFRESH_REMOTE_DNS_IP_BLACKLIST_WORK
            DnsRuleType.FORWARDING -> REFRESH_REMOTE_DNS_FORWARDING_WORK
            DnsRuleType.CLOAKING -> REFRESH_REMOTE_DNS_CLOAKING_WORK
        }

    private fun isMixDnsRulesInProgress(type: DnsRuleType): Boolean =
        WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(getMixWorkName(type)).get()
            .firstOrNull()?.state == WorkInfo.State.RUNNING

    private fun getMixWorkName(type: DnsRuleType) =
        when (type) {
            DnsRuleType.BLACKLIST -> MIX_DNS_BLACKLIST_WORK
            DnsRuleType.WHITELIST -> MIX_DNS_WHITELIST_WORK
            DnsRuleType.IP_BLACKLIST -> MIX_DNS_IP_BLACKLIST_WORK
            DnsRuleType.FORWARDING -> MIX_DNS_FORWARDING_WORK
            DnsRuleType.CLOAKING -> MIX_DNS_CLOAKING_WORK
        }

}
