package org.uniapk.upgrader.glass

import org.uniapk.upgrader.data.ui.AppUpdate

/** Manifest facts replace provider claims; the selected artifact identity stays stable. */
fun AppUpdate.withInspection(report: ApkAnalyzer.Report, checkedAt: Long) = copy(
    verdict = report.verdict,
    sha256 = report.sha256,
    updateOwner = report.owner,
    version = report.candidate.versionName,
    versionCode = report.candidate.versionCode ?: versionCode,
    minSdk = report.candidate.minSdk,
    targetSdk = report.targetSdk,
    abis = report.candidate.abis,
    verifiedAt = checkedAt,
    fileReady = true,
    verificationError = null
)
