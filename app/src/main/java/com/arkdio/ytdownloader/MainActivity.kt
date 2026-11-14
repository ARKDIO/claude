package com.arkdio.ytdownloader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.arkdio.ytdownloader.ui.MainScreen
import com.arkdio.ytdownloader.ui.AppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingMode: Mode? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val mode = pendingMode
            pendingMode = null
            if (granted && mode != null) viewModel.start(mode)
            else if (!granted) Toast.makeText(this, "저장 권한이 필요해요", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        askNotificationPermission()

        setContent {
            AppTheme {
                MainScreen(
                    viewModel = viewModel,
                    onDownload = ::startDownload,
                    onOpen = ::openFile,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            viewModel.onSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startDownload(mode: Mode) {
        // Android 9 and below need the classic storage permission to write to Movies/Download.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingMode = mode
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        viewModel.start(mode)
    }

    private fun openFile(done: DownloadStatus.Done) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(done.uri, done.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "파일을 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
        }
    }
}
