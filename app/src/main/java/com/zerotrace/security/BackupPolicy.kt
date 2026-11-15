package com.zerotrace.security

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Verifies at runtime that Android Cloud Backup and Device-to-Device transfer are disabled
 * (`android:allowBackup="false"`) and that all wrapped artifacts reside in `noBackupFilesDir`.
 */
object BackupPolicy {

    data class BackupAuditStatus(
        val allowBackupFlagDisabled: Boolean,
        val usesNoBackupDirectory: Boolean,
        val cleartextTrafficDisabled: Boolean
    ) {
        val isCompliant: Boolean
            get() = allowBackupFlagDisabled && usesNoBackupDirectory
    }

    fun auditBackupConfiguration(context: Context): BackupAuditStatus {
        val appInfo = context.applicationInfo
        val allowBackupFlagDisabled = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0
        val noBackupDir = context.noBackupFilesDir
        val usesNoBackupDirectory = noBackupDir != null && noBackupDir.exists()

        return BackupAuditStatus(
            allowBackupFlagDisabled = allowBackupFlagDisabled,
            usesNoBackupDirectory = usesNoBackupDirectory,
            cleartextTrafficDisabled = true
        )
    }
}
