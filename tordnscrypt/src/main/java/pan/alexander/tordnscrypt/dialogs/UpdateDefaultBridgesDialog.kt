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

package pan.alexander.tordnscrypt.dialogs

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AlertDialog
import dalvik.system.ZipPathValidator
import kotlinx.coroutines.Job
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.settings.SettingsActivity
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class UpdateDefaultBridgesDialog private constructor() {

    companion object DIALOG {
        fun getDialog(activity: Activity?, useDefaultBridges: Boolean): AlertDialog? {

            if (activity == null || activity.isFinishing) {
                return null
            }

            val builder = AlertDialog.Builder(activity)

            builder.setTitle(R.string.helper_dialog_title)
            builder.setMessage(R.string.dialog_new_tor_default_bridges_available)

            builder.setPositiveButton(R.string.ok) { _, _ ->
                if (!activity.isFinishing) {
                    updateDefaultBridges(activity, useDefaultBridges)
                }
            }

            builder.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }

            builder.setNeutralButton(R.string.dont_show) { _, _ ->
                App.instance.daggerComponent.getPreferenceRepository().get()
                    .setBoolPreference("doNotShowNewDefaultBridgesDialog", true)
            }

            return builder.create()
        }

        private fun updateDefaultBridges(activity: Activity, useDefaultBridges: Boolean): Job {
            return App.instance.daggerComponent
                .getCoroutineExecutor().submit("UpdateDefaultBridgesDialog updateDefaultBridges") {
                val pathVars = App.instance.daggerComponent.getPathVars().get()
                val outputFile = File(pathVars.appDataDir + "/app_data/tor/bridges_default.lst")
                val installedBridgesSize = outputFile.length()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ZipPathValidator.clearCallback()
                    }

                    ZipInputStream(activity.assets.open("tor.mp3")).use { zipInputStream ->
                        var zipEntry = zipInputStream.nextEntry
                        while (zipEntry != null) {
                            val fileName = zipEntry.name
                            if (fileName.endsWith("bridges_default.lst") && zipEntry.size != installedBridgesSize) {
                                FileOutputStream(outputFile).use { outputStream ->
                                    copyData(zipInputStream, outputStream)
                                    logi("Tor default bridges were updated!")

                                    if (!activity.isFinishing && useDefaultBridges) {
                                        activity.runOnUiThread {
                                            if (!activity.isFinishing) {
                                                val intent = Intent(activity, SettingsActivity::class.java)
                                                intent.action = "tor_bridges"
                                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                                        or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                                                activity.overridePendingTransition(0, 0)
                                                activity.finish()

                                                activity.overridePendingTransition(0, 0)
                                                activity.startActivity(intent)
                                            }
                                        }
                                    }
                                }
                                break
                            }
                            zipEntry = zipInputStream.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    loge("UpdateDefaultBridgesDialog updateDefaultBridges", e)
                }
            }
        }

        @Throws(java.lang.Exception::class)
        private fun copyData(inputStream: InputStream, outputStream: OutputStream) {
            val buffer = ByteArray(8 * 1024)
            var len: Int
            while (inputStream.read(buffer).also { len = it } > 0) {
                outputStream.write(buffer, 0, len)
            }
        }
    }
}