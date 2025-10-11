package org.kenjinx.android.views

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import org.kenjinx.android.MainActivity
import org.kenjinx.android.providers.DocumentProvider
import org.kenjinx.android.viewmodels.DataImportState
import org.kenjinx.android.viewmodels.DataResetState
import org.kenjinx.android.viewmodels.FirmwareInstallState
import org.kenjinx.android.viewmodels.KeyInstallState
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.MemoryConfiguration
import org.kenjinx.android.viewmodels.SettingsViewModel
import org.kenjinx.android.viewmodels.MemoryManagerMode
import org.kenjinx.android.viewmodels.VSyncMode
import org.kenjinx.android.widgets.ActionButton
import org.kenjinx.android.widgets.DropdownSelector
import org.kenjinx.android.widgets.ExpandableView
import org.kenjinx.android.widgets.SimpleAlertDialog
import org.kenjinx.android.widgets.SwitchSelector
import org.kenjinx.android.R
// >>> QuickSettings + OrientationPreference
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.QuickSettings.OrientationPreference
import org.kenjinx.android.viewmodels.QuickSettings.OverlayMenuPosition // ← NEU
// Import enums
import org.kenjinx.android.SystemLanguage
import org.kenjinx.android.RegionCode

class SettingViews {
    companion object {
        const val EXPANSTION_TRANSITION_DURATION = 450
        const val IMPORT_CODE = 12341

        @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
        @Composable
        fun Main(settingsViewModel: SettingsViewModel, mainViewModel: MainViewModel) {
            val loaded = remember { mutableStateOf(false) }
            val memoryManagerMode = remember { mutableStateOf(MemoryManagerMode.HostMappedUnsafe) }
            val useNce = remember { mutableStateOf(false) }
            val memoryConfiguration = remember { mutableStateOf(MemoryConfiguration.MemoryConfiguration4GiB) }
            val vSyncMode = remember { mutableStateOf(VSyncMode.Switch) }
            val enableDocked = remember { mutableStateOf(false) }
            val enablePptc = remember { mutableStateOf(false) }
            val enableLowPowerPptc = remember { mutableStateOf(false) }
            val enableJitCacheEviction = remember { mutableStateOf(false) }
            val enableFsIntegrityChecks = remember { mutableStateOf(false) }
            val fsGlobalAccessLogMode = remember { mutableIntStateOf(0) }
            val ignoreMissingServices = remember { mutableStateOf(false) }
            val enableShaderCache = remember { mutableStateOf(false) }
            val enableTextureRecompression = remember { mutableStateOf(false) }
            val enableMacroHLE = remember { mutableStateOf(false) }
            val stretchToFullscreen = remember { mutableStateOf(false) }
            val resScale = remember { mutableFloatStateOf(1f) }
            val maxAnisotropy = remember { mutableFloatStateOf(0f) }
            val useVirtualController = remember { mutableStateOf(true) }
            val showKeyDialog = remember { mutableStateOf(false) }
            val keyInstallState = remember { mutableStateOf(KeyInstallState.File) }
            val showFirwmareDialog = remember { mutableStateOf(false) }
            val firmwareInstallState = remember { mutableStateOf(FirmwareInstallState.File) }
            val firmwareVersion = remember { mutableStateOf(mainViewModel.firmwareVersion) }
            val showDataResetDialog = remember { mutableStateOf(false) }
            val showDataImportDialog = remember { mutableStateOf(false) }
            val dataResetState = remember { mutableStateOf(DataResetState.Query) }
            val dataImportState = remember { mutableStateOf(DataImportState.File) }
            val dataFile = remember { mutableStateOf<DocumentFile?>(null) }
            val isGrid = remember { mutableStateOf(true) }
            val useSwitchLayout = remember { mutableStateOf(true) }
            val enableMotion = remember { mutableStateOf(true) }
            val enablePerformanceMode = remember { mutableStateOf(true) }
            val controllerStickSensitivity = remember { mutableFloatStateOf(1.0f) }
            val enableStubLogs = remember { mutableStateOf(true) }
            val enableInfoLogs = remember { mutableStateOf(true) }
            val enableWarningLogs = remember { mutableStateOf(true) }
            val enableErrorLogs = remember { mutableStateOf(true) }
            val enableGuestLogs = remember { mutableStateOf(true) }
            val enableFsAccessLogs = remember { mutableStateOf(true) }
            val enableTraceLogs = remember { mutableStateOf(true) }
            val enableDebugLogs = remember { mutableStateOf(true) }
            val enableGraphicsLogs = remember { mutableStateOf(true) }
            val isNavigating = remember { mutableStateOf(false) }
            val useControllerSensor = remember { mutableStateOf(false) }
            // Load orientation from QuickSettings
            val orientationPref = remember {
                mutableStateOf(QuickSettings(mainViewModel.activity).orientationPreference)
            }
            // Language & Region States
            val systemLanguage = remember { mutableStateOf(SystemLanguage.AmericanEnglish) }
            val regionCode = remember { mutableStateOf(RegionCode.USA) }
            // Load overlay settings from QuickSettings
            val overlayMenuPosition = remember {
                mutableStateOf(QuickSettings(mainViewModel.activity).overlayMenuPosition)
            }
            val overlayOpacity = remember {
                mutableFloatStateOf(QuickSettings(mainViewModel.activity).overlayMenuOpacity.coerceIn(0f, 1f))
            }

            if(!loaded.value) {
                settingsViewModel.initializeState(
                    memoryManagerMode,
                    useNce,
                    memoryConfiguration,
                    vSyncMode,
                    enableDocked,
                    enablePptc,
                    enableLowPowerPptc,
                    enableJitCacheEviction,
                    enableFsIntegrityChecks,
                    fsGlobalAccessLogMode,
                    ignoreMissingServices,
                    enableShaderCache,
                    enableTextureRecompression,
                    enableMacroHLE,
                    stretchToFullscreen,
                    resScale,
                    maxAnisotropy,
                    useVirtualController,
                    isGrid,
                    useSwitchLayout,
                    enableMotion,
                    enablePerformanceMode,
                    controllerStickSensitivity,
                    enableStubLogs,
                    enableInfoLogs,
                    enableWarningLogs,
                    enableErrorLogs,
                    enableGuestLogs,
                    enableFsAccessLogs,
                    enableTraceLogs,
                    enableDebugLogs,
                    enableGraphicsLogs,
                    systemLanguage,
                    regionCode,
                    useControllerSensor
                )
                loaded.value = true
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(title = {
                        Text(text = stringResource(R.string.settings))
                    },
                        navigationIcon = {
                            IconButton(onClick = {
                                settingsViewModel.save(
                                    memoryManagerMode,
                                    useNce,
                                    memoryConfiguration,
                                    vSyncMode,
                                    enableDocked,
                                    enablePptc,
                                    enableLowPowerPptc,
                                    enableJitCacheEviction,
                                    enableFsIntegrityChecks,
                                    fsGlobalAccessLogMode,
                                    ignoreMissingServices,
                                    enableShaderCache,
                                    enableTextureRecompression,
                                    enableMacroHLE,
                                    stretchToFullscreen,
                                    resScale,
                                    maxAnisotropy,
                                    useVirtualController,
                                    isGrid,
                                    useSwitchLayout,
                                    enableMotion,
                                    enablePerformanceMode,
                                    controllerStickSensitivity,
                                    enableStubLogs,
                                    enableInfoLogs,
                                    enableWarningLogs,
                                    enableErrorLogs,
                                    enableGuestLogs,
                                    enableFsAccessLogs,
                                    enableTraceLogs,
                                    enableDebugLogs,
                                    enableGraphicsLogs,
                                    systemLanguage,
                                    regionCode,
                                    useControllerSensor
                                )

                                if(!isNavigating.value) {
                                    isNavigating.value = true
                                    mainViewModel.navController?.popBackStack()

                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(500)
                                        isNavigating.value = false
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        })
                }) { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    ExpandableView(onCardArrowClick = { }, title = stringResource(R.string.settings_user_interface),
                        icon = Icons.Outlined.BarChart, isFirst = true) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Screen Orientation
                            OrientationDropdown(
                                selectedOrientation = orientationPref.value,
                                onOrientationSelected = { sel ->
                                    orientationPref.value = sel
                                    // Save and use immediately
                                    val qs = QuickSettings(mainViewModel.activity)
                                    qs.orientationPreference = sel
                                    qs.save()
                                    // 1) Set activity alignment
                                    val act = mainViewModel.activity
                                    act.requestedOrientation = sel.value
                                    // 2) Submit rotation/size immediately to the rendering
                                    val rot = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        act.display?.rotation
                                    } else {
                                        TODO("VERSION.SDK_INT < R")
                                    }
                                    mainViewModel.gameHost?.onOrientationOrSizeChanged(rot)
                                }
                            )
                            // Overlay Menu Position (DropdownSelector as usual)
                            OverlayPositionDropdown(
                                selectedPosition = overlayMenuPosition.value,
                                onPositionSelected = { pos ->
                                    overlayMenuPosition.value = pos
                                    val qs = QuickSettings(mainViewModel.activity)
                                    qs.overlayMenuPosition = pos
                                    qs.save()
                                }
                            )
                            // Overlay transparency slider – identical style as controller stick sensitivity
                            val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.overlay_transparency),
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Slider(
                                    modifier = Modifier.width(250.dp),
                                    value = overlayOpacity.floatValue,
                                    onValueChange = {
                                        val clamped = it.coerceIn(0f, 1f)
                                        overlayOpacity.floatValue = clamped
                                        val qs = QuickSettings(mainViewModel.activity)
                                        qs.overlayMenuOpacity = clamped
                                        qs.save()
                                    },
                                    valueRange = 0f..1f,
                                    steps = 20,
                                    interactionSource = interactionSource,
                                    thumb = {
                                        Label(
                                            label = {
                                                PlainTooltip(
                                                    modifier = Modifier
                                                        .sizeIn(45.dp, 25.dp)
                                                        .wrapContentWidth()
                                                ) {
                                                    Text("${(overlayOpacity.floatValue * 100f).toInt()}%")
                                                }
                                            },
                                            interactionSource = interactionSource
                                        ) {
                                            Icon(
                                                imageVector = org.kenjinx.android.Icons.circle(
                                                    color = MaterialTheme.colorScheme.primary
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }

                            isGrid.SwitchSelector(stringResource(R.string.use_grid))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.system_firmware),
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Text(
                                    text = firmwareVersion.value,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        showKeyDialog.value = true
                                    },
                                    text = stringResource(R.string.install_keys),
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        showFirwmareDialog.value = true
                                    },
                                    text = stringResource(R.string.install_firmware),
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        settingsViewModel.openGameFolder()
                                    },
                                    text = stringResource(R.string.add_game_folder),
                                    icon = Icons.Default.Add,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        showDataResetDialog.value = true
                                    },
                                    text = stringResource(R.string.reinit_app_data),
                                    icon = Icons.Default.Create,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        showDataImportDialog.value = true
                                    },
                                    text = stringResource(R.string.import_app_data),
                                    icon = Icons.Default.FileDownload,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        fun createIntent(action: String): Intent {
                                            val intent = Intent(action)
                                            intent.addCategory(Intent.CATEGORY_DEFAULT)
                                            intent.data = DocumentsContract.buildRootUri(
                                                DocumentProvider.AUTHORITY,
                                                DocumentProvider.ROOT_ID
                                            )
                                            intent.addFlags(
                                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                            return intent
                                        }
                                        try {
                                            mainViewModel.activity.startActivity(createIntent(Intent.ACTION_VIEW))
                                            return@ActionButton
                                        } catch(_: ActivityNotFoundException) {
                                        }
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("android.provider.action.BROWSE"))
                                            return@ActionButton
                                        } catch(_: ActivityNotFoundException) {
                                        }
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("com.google.android.documentsui"))
                                            return@ActionButton
                                        } catch(_: ActivityNotFoundException) {
                                        }
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("com.android.documentsui"))
                                            return@ActionButton
                                        } catch(_: ActivityNotFoundException) {
                                        }
                                    },
                                    text = stringResource(R.string.open_app_folder),
                                    icon = Icons.Default.Home,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                        }
                    }
                    SimpleAlertDialog.Custom(
                        showDialog = showKeyDialog,
                        onDismissRequest = {
                            if(keyInstallState.value != KeyInstallState.Install) {
                                showKeyDialog.value = false
                                settingsViewModel.clearKeySelection(keyInstallState)
                                keyInstallState.value = KeyInstallState.File
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.key_installation),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when(keyInstallState.value) {
                                KeyInstallState.File -> {
                                    Text(
                                        text = stringResource(R.string.select_key_file),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.selectKey(
                                                    keyInstallState
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.select_file))
                                        }
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.cancel))
                                        }
                                    }
                                }

                                KeyInstallState.Query -> {
                                    Text(
                                        text = stringResource(R.string.confirm_install_key),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.installKey(
                                                    keyInstallState
                                                )

                                                if(keyInstallState.value == KeyInstallState.File) {
                                                    showKeyDialog.value = false
                                                    settingsViewModel.clearKeySelection(keyInstallState)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.confirm_yes))
                                        }
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.confirm_no))
                                        }
                                    }
                                }

                                KeyInstallState.Install -> {
                                    Text(
                                        text = stringResource(R.string.installing_key_file),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                KeyInstallState.Done -> {
                                    Text(
                                        text = stringResource(R.string.key_file_installed),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.close))
                                        }
                                    }
                                }

                                KeyInstallState.Cancelled -> {
                                    val file = settingsViewModel.selectedKeyFile
                                    if(file != null) {
                                        if(file.extension == "key") {
                                            Text(
                                                text = stringResource(R.string.unknown_error_occurred),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.file_type_not_supported),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "File type is not supported.",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, bottom = 8.dp),
                                            textAlign = TextAlign.Start
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.close))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SimpleAlertDialog.Custom(
                        showDialog = showFirwmareDialog,
                        onDismissRequest = {
                            if(firmwareInstallState.value != FirmwareInstallState.Install) {
                                showFirwmareDialog.value = false
                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                firmwareInstallState.value = FirmwareInstallState.File
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.firmware_installation),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when(firmwareInstallState.value) {
                                FirmwareInstallState.File -> {
                                    Text(
                                        text = stringResource(R.string.select_firmware_file),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.selectFirmware(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.select_file))
                                        }
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.cancel))
                                        }
                                    }
                                }

                                FirmwareInstallState.Query -> {
                                    Text(
                                        text = stringResource(R.string.confirm_install_firmware, settingsViewModel.selectedFirmwareVersion),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.installFirmware(firmwareInstallState)

                                                if(firmwareInstallState.value == FirmwareInstallState.File) {
                                                    showFirwmareDialog.value = false
                                                    settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.confirm_yes))
                                        }
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.confirm_no))
                                        }
                                    }
                                }

                                FirmwareInstallState.Verifying -> {
                                    Text(
                                        text = stringResource(R.string.verifying_selected_file),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                FirmwareInstallState.Install -> {
                                    Text(
                                        text = stringResource(R.string.installing_firmware, settingsViewModel.selectedFirmwareVersion),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                FirmwareInstallState.Done -> {
                                    Text(
                                        text = stringResource(R.string.firmware_version_display, settingsViewModel.selectedFirmwareVersion),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    firmwareVersion.value = mainViewModel.firmwareVersion

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.close))
                                        }
                                    }
                                }

                                FirmwareInstallState.Cancelled -> {
                                    val file = settingsViewModel.selectedFirmwareFile
                                    if(file != null) {
                                        if(file.extension == "xci" || file.extension == "zip") {
                                            if(settingsViewModel.selectedFirmwareVersion.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.version_not_found_in_file),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 8.dp, bottom = 8.dp),
                                                    textAlign = TextAlign.Start
                                                )
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.unknown_error_occurred),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 8.dp, bottom = 8.dp),
                                                    textAlign = TextAlign.Start
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = stringResource(R.string.file_type_not_supported),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = stringResource(R.string.file_type_not_supported),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, bottom = 8.dp),
                                            textAlign = TextAlign.Start
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.close))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SimpleAlertDialog.Custom(
                        showDialog = showDataResetDialog,
                        onDismissRequest = {
                            if(dataResetState.value != DataResetState.Reset) {
                                showDataResetDialog.value = false
                                dataResetState.value = DataResetState.Query
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.app_data_reinit),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when(dataResetState.value) {
                                DataResetState.Query -> {
                                    Text(
                                        text = stringResource(R.string.confirm_reinit_app_data),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                thread {
                                                    settingsViewModel.resetAppData(dataResetState)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.confirm_yes))
                                        }
                                        Button(
                                            onClick = {
                                                showDataResetDialog.value = false
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.confirm_no))
                                        }
                                    }
                                }

                                DataResetState.Reset -> {
                                    Text(
                                        text = stringResource(R.string.resetting_app_data),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                DataResetState.Done -> {
                                    Text(
                                        text = stringResource(R.string.data_reset_completed),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showDataResetDialog.value = false
                                                firmwareVersion.value = mainViewModel.firmwareVersion
                                                dataResetState.value = DataResetState.Query
                                                mainViewModel.userViewModel.refreshUsers()
                                                mainViewModel.homeViewModel.requestReload()
                                                mainViewModel.activity.shutdownAndRestart()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.close))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SimpleAlertDialog.Custom(
                        showDialog = showDataImportDialog,
                        onDismissRequest = {
                            if(dataImportState.value != DataImportState.Import) {
                                showDataImportDialog.value = false
                                dataFile.value = null
                                dataImportState.value = DataImportState.File
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.app_data_import),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when(dataImportState.value) {
                                DataImportState.File -> {
                                    Text(
                                        text = stringResource(R.string.select_import_file),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val storage = MainActivity.StorageHelper
                                                storage?.apply {
                                                    val callBack = this.onFileSelected
                                                    onFileSelected = { requestCode, files ->
                                                        run {
                                                            onFileSelected = callBack
                                                            if(requestCode == IMPORT_CODE) {
                                                                val file = files.firstOrNull()
                                                                file?.apply {
                                                                    if(this.extension == "zip") {
                                                                        dataFile.value = this
                                                                        dataImportState.value = DataImportState.Query
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    openFilePicker(
                                                        IMPORT_CODE,
                                                        filterMimeTypes = arrayOf("application/zip")
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.select_file))
                                        }
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                                dataImportState.value = DataImportState.File
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.cancel))
                                        }
                                    }
                                }

                                DataImportState.Query -> {
                                    Text(
                                        text = stringResource(R.string.confirm_import_app_data),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val file = dataFile.value
                                                dataFile.value = null
                                                file?.apply {
                                                    thread {
                                                        settingsViewModel.importAppData(this, dataImportState)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.confirm_yes))
                                        }
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.confirm_no))
                                        }
                                    }
                                }

                                DataImportState.Import -> {
                                    Text(
                                        text = stringResource(R.string.importing_app_data),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                DataImportState.Done -> {
                                    Text(
                                        text = stringResource(R.string.data_import_completed),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                                firmwareVersion.value = mainViewModel.firmwareVersion
                                                dataImportState.value = DataImportState.File
                                                mainViewModel.userViewModel.refreshUsers()
                                                mainViewModel.homeViewModel.requestReload()
                                                mainViewModel.activity.shutdownAndRestart()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = stringResource(R.string.close))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ExpandableView(onCardArrowClick = { }, title = stringResource(R.string.settings_input),
                        icon = Icons.Outlined.VideogameAsset) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            useVirtualController.SwitchSelector(label = stringResource(R.string.use_virtual_controller))
                            useSwitchLayout.SwitchSelector(label = stringResource(R.string.use_switch_controller_layout))
                            val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.controller_stick_sensitivity),
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Slider(modifier = Modifier.width(250.dp), value = controllerStickSensitivity.floatValue, onValueChange = {
                                    controllerStickSensitivity.floatValue = it
                                }, valueRange = 0.1f..2f,
                                    steps = 20,
                                    interactionSource = interactionSource,
                                    thumb = {
                                        Label(
                                            label = {
                                                PlainTooltip(modifier = Modifier
                                                    .sizeIn(45.dp, 25.dp)
                                                    .wrapContentWidth()) {
                                                    Text("%.2f".format(controllerStickSensitivity.floatValue))
                                                }
                                            },
                                            interactionSource = interactionSource
                                        ) {
                                            Icon(
                                                imageVector = org.kenjinx.android.Icons.circle(
                                                    color = MaterialTheme.colorScheme.primary
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }

                            enableDocked.SwitchSelector(label = stringResource(R.string.docked_mode))
                            enableMotion.SwitchSelector(label = stringResource(R.string.motion_sensor))
                            useControllerSensor.SwitchSelector(label = stringResource(R.string.use_controller_motion_sensor))
                        }
                    }
                    ExpandableView(onCardArrowClick = { }, title = stringResource(R.string.system), icon = Icons.Outlined.Settings) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Language & Region
                            LanguageDropdown(
                                selectedLanguage = systemLanguage.value,
                                onLanguageSelected = { lang -> systemLanguage.value = lang }
                            )
                            RegionDropdown(
                                selectedRegion = regionCode.value,
                                onRegionSelected = { reg -> regionCode.value = reg }
                            )

                            VSyncDropdown(
                                selectedVSyncMode = vSyncMode.value,
                                onModeSelected = { mode ->
                                    vSyncMode.value = mode
                                }
                            )
                            MemoryDropdown(
                                selectedMemoryConfiguration = memoryConfiguration.value,
                                onConfigurationSelected = { configuration ->
                                    memoryConfiguration.value = configuration
                                }
                            )

                            enableFsIntegrityChecks.SwitchSelector(label = stringResource(R.string.fs_integrity_check))
                            ignoreMissingServices.SwitchSelector(label = stringResource(R.string.ignore_missing_services))
                            enablePerformanceMode.SwitchSelector(label = stringResource(R.string.performance_mode))
                        }
                    }
                    ExpandableView(onCardArrowClick = { }, title = stringResource(R.string.settings_cpu), icon = Icons.Outlined.Memory) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            useNce.SwitchSelector(label = stringResource(R.string.nce))
                            enablePptc.SwitchSelector(label = stringResource(R.string.pptc))
                            enableLowPowerPptc.SwitchSelector(label = stringResource(R.string.low_power_pptc))
                            enableJitCacheEviction.SwitchSelector(label = stringResource(R.string.jit_cache_eviction))
                            MemoryModeDropdown(
                                selectedMemoryManagerMode = memoryManagerMode.value,
                                onModeSelected = { mode ->
                                    memoryManagerMode.value = mode
                                }
                            )
                        }
                    }
                    ExpandableView(onCardArrowClick = { }, title = stringResource(R.string.graphics), icon = Icons.Outlined.Panorama) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            enableShaderCache.SwitchSelector(label = stringResource(R.string.shader_cache))
                            enableTextureRecompression.SwitchSelector(label = stringResource(R.string.texture_recompression))
                            enableMacroHLE.SwitchSelector(label = stringResource(R.string.macro_hle))
                            stretchToFullscreen.SwitchSelector(label = stringResource(R.string.stretch_to_fullscreen))
                            ResolutionScaleDropdown(
                                selectedScale = resScale.floatValue,
                                onScaleSelected = { scale ->
                                    resScale.floatValue = scale
                                }
                            )
                            AnisotropicFilteringDropdown(
                                selectedAnisotropy = maxAnisotropy.floatValue,
                                onAnisotropySelected = { anisotropy ->
                                    maxAnisotropy.floatValue = anisotropy
                                }
                            )
                            val isDriverSelectorOpen = remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        isDriverSelectorOpen.value = !isDriverSelectorOpen.value
                                    },
                                    text = stringResource(R.string.install_driver),
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }

                            SimpleAlertDialog.Custom(
                                showDialog = isDriverSelectorOpen,
                                onDismissRequest = { isDriverSelectorOpen.value = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                VulkanDriverViews.Main(settingsViewModel.activity, isDriverSelectorOpen)
                            }
                        }
                    }

                    ExpandableView(onCardArrowClick = { }, title = stringResource(R.string.logging), icon = Icons.Outlined.FileOpen) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            enableStubLogs.SwitchSelector(label = stringResource(R.string.stub_logs))
                            enableInfoLogs.SwitchSelector(label = stringResource(R.string.info_logs))
                            enableWarningLogs.SwitchSelector(label = stringResource(R.string.warning_logs))
                            enableErrorLogs.SwitchSelector(label = stringResource(R.string.error_logs))
                            enableGuestLogs.SwitchSelector(label = stringResource(R.string.guest_logs))
                            enableTraceLogs.SwitchSelector(label = stringResource(R.string.trace_logs))
                            enableFsAccessLogs.SwitchSelector(label = stringResource(R.string.fs_access_logs))
                            enableDebugLogs.SwitchSelector(label = stringResource(R.string.debug_logs))
                            enableGraphicsLogs.SwitchSelector(label = stringResource(R.string.graphics_logs))
                            FsGlobalAccessLogModeDropdown(
                                selectedFsGlobalAccess = fsGlobalAccessLogMode.intValue,
                                onFsGlobalAccessSelected = { fsGlobalAccess ->
                                    fsGlobalAccessLogMode.intValue = fsGlobalAccess
                                }
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        mainViewModel.logging.requestExport()
                                    },
                                    text = stringResource(R.string.send_logs),
                                    icon = Icons.Default.MailOutline,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Overlay position dropdown ----
        @Composable
        fun OverlayPositionDropdown(
            selectedPosition: OverlayMenuPosition,
            onPositionSelected: (OverlayMenuPosition) -> Unit
        ) {
            val options = listOf(
                OverlayMenuPosition.BottomMiddle,
                OverlayMenuPosition.BottomLeft,
                OverlayMenuPosition.BottomRight,
                OverlayMenuPosition.TopMiddle,
                OverlayMenuPosition.TopLeft,
                OverlayMenuPosition.TopRight
            )
            val context = LocalContext.current

            DropdownSelector(
                label = stringResource(R.string.overlay_menu_position),
                selectedValue = selectedPosition,
                options = options,
                getDisplayText = { opt ->
                    when(opt) {
                        OverlayMenuPosition.BottomMiddle -> context.getString(R.string.overlay_menu_position_bottom_middle)
                        OverlayMenuPosition.BottomLeft -> context.getString(R.string.overlay_menu_position_bottom_left)
                        OverlayMenuPosition.BottomRight -> context.getString(R.string.overlay_menu_position_bottom_right)
                        OverlayMenuPosition.TopMiddle -> context.getString(R.string.overlay_menu_position_top_middle)
                        OverlayMenuPosition.TopLeft -> context.getString(R.string.overlay_menu_position_top_left)
                        OverlayMenuPosition.TopRight -> context.getString(R.string.overlay_menu_position_top_right)
                    }
                },
                onOptionSelected = onPositionSelected
            )
        }

        // ---- Dropdowns for language & region ----
        @Composable
        fun LanguageDropdown(
            selectedLanguage: SystemLanguage,
            onLanguageSelected: (SystemLanguage) -> Unit
        ) {
            val options = SystemLanguage.entries.toTypedArray()
            val context = LocalContext.current

            DropdownSelector(
                label = stringResource(R.string.system_language),
                selectedValue = selectedLanguage,
                options = options.toList(),
                getDisplayText = { lang ->
                    when(lang) {
                        SystemLanguage.Japanese -> context.getString(R.string.language_japanese)
                        SystemLanguage.AmericanEnglish -> context.getString(R.string.language_english_us)
                        SystemLanguage.French -> context.getString(R.string.language_french)
                        SystemLanguage.German -> context.getString(R.string.language_german)
                        SystemLanguage.Italian -> context.getString(R.string.language_italian)
                        SystemLanguage.Spanish -> context.getString(R.string.language_spanish_eu)
                        SystemLanguage.Chinese -> context.getString(R.string.language_chinese)
                        SystemLanguage.Korean -> context.getString(R.string.language_korean)
                        SystemLanguage.Dutch -> context.getString(R.string.language_dutch)
                        SystemLanguage.Portuguese -> context.getString(R.string.language_portuguese_eu)
                        SystemLanguage.Russian -> context.getString(R.string.language_russian)
                        SystemLanguage.Taiwanese -> context.getString(R.string.language_chinese_taiwan)
                        SystemLanguage.BritishEnglish -> context.getString(R.string.language_english_uk)
                        SystemLanguage.CanadianFrench -> context.getString(R.string.language_french_canada)
                        SystemLanguage.LatinAmericanSpanish -> context.getString(R.string.language_spanish_latam)
                        SystemLanguage.SimplifiedChinese -> context.getString(R.string.language_chinese_simplified)
                        SystemLanguage.TraditionalChinese -> context.getString(R.string.language_chinese_traditional)
                        SystemLanguage.BrazilianPortuguese -> context.getString(R.string.language_portuguese_brazil)
                    }
                },
                onOptionSelected = onLanguageSelected
            )
        }

        @Composable
        fun RegionDropdown(
            selectedRegion: RegionCode,
            onRegionSelected: (RegionCode) -> Unit
        ) {
            val options = RegionCode.entries.toTypedArray()
            val context = LocalContext.current
            DropdownSelector(
                label = stringResource(R.string.region),
                selectedValue = selectedRegion,
                options = options.toList(),
                getDisplayText = { region ->
                    when(region) {
                        RegionCode.Japan -> context.getString(R.string.region_japan)
                        RegionCode.USA -> context.getString(R.string.region_usa)
                        RegionCode.Europe -> context.getString(R.string.region_europe)
                        RegionCode.Australia -> context.getString(R.string.region_australia)
                        RegionCode.China -> context.getString(R.string.region_china)
                        RegionCode.Korea -> context.getString(R.string.region_korea)
                        RegionCode.Taiwan -> context.getString(R.string.region_taiwan)
                    }
                },
                onOptionSelected = onRegionSelected
            )
        }

        // ---- Existing dropdowns ----
        // ---- Dropdown for orientation ----
        @Composable
        fun OrientationDropdown(
            selectedOrientation: OrientationPreference,
            onOrientationSelected: (OrientationPreference) -> Unit
        ) {
            val options = listOf(
                OrientationPreference.Sensor,
                OrientationPreference.SensorLandscape,
                OrientationPreference.SensorPortrait
            )
            val context = LocalContext.current

            DropdownSelector(
                label = stringResource(R.string.screen_orientation),
                selectedValue = selectedOrientation,
                options = options,
                getDisplayText = { opt ->
                    when(opt) {
                        OrientationPreference.Sensor -> context.getString(R.string.orientation_sensor)
                        OrientationPreference.SensorLandscape -> context.getString(R.string.orientation_sensor_landscape)
                        OrientationPreference.SensorPortrait -> context.getString(R.string.orientation_sensor_portrait)
                    }
                },
                onOptionSelected = onOrientationSelected
            )
        }

        // ---- Existing dropdowns ----
        @Composable
        fun MemoryModeDropdown(
            selectedMemoryManagerMode: MemoryManagerMode,
            onModeSelected: (MemoryManagerMode) -> Unit
        ) {
            val modes = MemoryManagerMode.entries.toTypedArray()
            val context = LocalContext.current

            DropdownSelector(
                label = stringResource(R.string.memory_manager_mode),
                selectedValue = selectedMemoryManagerMode,
                options = modes.toList(),
                getDisplayText = { mode ->
                    when(mode) {
                        MemoryManagerMode.SoftwarePageTable -> context.getString(R.string.memory_mode_software)
                        MemoryManagerMode.HostMapped -> context.getString(R.string.memory_mode_host_fast)
                        MemoryManagerMode.HostMappedUnsafe -> context.getString(R.string.memory_mode_host_unchecked)
                    }
                },
                onOptionSelected = onModeSelected
            )
        }

        @Composable
        fun VSyncDropdown(
            selectedVSyncMode: VSyncMode,
            onModeSelected: (VSyncMode) -> Unit
        ) {
            val modes = VSyncMode.entries.toTypedArray()
            val context = LocalContext.current

            DropdownSelector(
                label = stringResource(R.string.vsync),
                selectedValue = selectedVSyncMode,
                options = modes.toList(),
                getDisplayText = { mode ->
                    when(mode) {
                        VSyncMode.Switch -> context.getString(R.string.vsync_switch)
                        VSyncMode.Unbounded -> context.getString(R.string.vsync_unbounded)
                    }
                },
                onOptionSelected = onModeSelected
            )
        }

        @Composable
        fun MemoryDropdown(
            selectedMemoryConfiguration: MemoryConfiguration,
            onConfigurationSelected: (MemoryConfiguration) -> Unit
        ) {
            val modes = MemoryConfiguration.entries.toTypedArray()

            DropdownSelector(
                label = stringResource(R.string.dram_size),
                selectedValue = selectedMemoryConfiguration,
                options = modes.toList(),
                getDisplayText = { configuration ->
                    when(configuration) {
                        MemoryConfiguration.MemoryConfiguration4GiB -> "4GiB"
                        MemoryConfiguration.MemoryConfiguration6GiB -> "6GiB"
                        MemoryConfiguration.MemoryConfiguration8GiB -> "8GiB"
                        MemoryConfiguration.MemoryConfiguration10GiB -> "10GiB"
                        MemoryConfiguration.MemoryConfiguration12GiB -> "12GiB"
                    }
                },
                onOptionSelected = onConfigurationSelected
            )
        }

        @Composable
        fun ResolutionScaleDropdown(
            selectedScale: Float,
            onScaleSelected: (Float) -> Unit
        ) {
            val scaleOptions = listOf(
                0.5f to "0.5x (360p/540p)",
                0.75f to "0.75x (540p/810p)",
                1f to "1.0x (720p/1080p)",
                2f to "2.0x (1440p/2160p)",
                3f to "3.0x (2160p/3240p)",
                4f to "4.0x (2800p/4320p)"
            )

            DropdownSelector(
                label = stringResource(R.string.resolution_scale),
                selectedValue = selectedScale,
                options = scaleOptions.map { it.first },
                getDisplayText = { scale ->
                    scaleOptions.find { it.first == scale }?.second ?: "${scale}x"
                },
                onOptionSelected = onScaleSelected
            )
        }

        @Composable
        fun AnisotropicFilteringDropdown(
            selectedAnisotropy: Float,
            onAnisotropySelected: (Float) -> Unit
        ) {
            val anisotropyOptions = listOf(
                0.0f to "0x",
                1.0f to "2x",
                2.0f to "4x",
                3.0f to "8x",
                4.0f to "16x"
            )

            DropdownSelector(
                label = stringResource(R.string.anisotropic_filtering),
                selectedValue = selectedAnisotropy,
                options = anisotropyOptions.map { it.first },
                getDisplayText = { anisotropy ->
                    anisotropyOptions.find { it.first == anisotropy }?.second ?: "${anisotropy}x"
                },
                onOptionSelected = onAnisotropySelected
            )
        }

        @Composable
        fun FsGlobalAccessLogModeDropdown(
            selectedFsGlobalAccess: Int,
            onFsGlobalAccessSelected: (Int) -> Unit
        ) {
            val fsGlobalAccessOptions = listOf(
                0 to "0",
                1 to "1",
                2 to "2",
                3 to "3"
            )

            DropdownSelector(
                label = stringResource(R.string.fs_global_access_log_mode),
                selectedValue = selectedFsGlobalAccess,
                options = fsGlobalAccessOptions.map { it.first },
                getDisplayText = { fsGlobalAccess ->
                    fsGlobalAccessOptions.find { it.first == fsGlobalAccess }?.second ?: "${fsGlobalAccess}x"
                },
                onOptionSelected = onFsGlobalAccessSelected
            )
        }
    }
}
