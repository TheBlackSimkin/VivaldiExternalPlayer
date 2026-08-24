package com.example.vivaldiplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Opens a reviewable GitHub issue draft with minimal build metadata only.
 *
 * Privacy boundary:
 * - No operations-log content is read or attached here.
 * - No page/media/request data is included.
 * - Nothing is sent until the user confirms and then submits on GitHub.
 */
object GitHubIssueReporter {
    private const val NEW_ISSUE_URL =
        "https://github.com/TheBlackSimkin/VivaldiExternalPlayer/issues/new"

    fun showReview(activity: AppCompatActivity) {
        val preview = activity.getString(
            R.string.github_report_preview,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.GIT_COMMIT,
            BuildConfig.BUILD_RUN
        )

        AlertDialog.Builder(activity)
            .setTitle(R.string.github_report_preview_title)
            .setMessage(preview)
            .setPositiveButton(R.string.open_github) { _, _ -> openDraft(activity) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openDraft(activity: AppCompatActivity) {
        val title = activity.getString(
            R.string.github_report_issue_title,
            BuildConfig.VERSION_NAME
        )
        val body = activity.getString(
            R.string.github_report_issue_body,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.GIT_COMMIT,
            BuildConfig.BUILD_RUN
        )
        val uri = Uri.parse(NEW_ISSUE_URL)
            .buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body)
            .build()

        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.github_report_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
