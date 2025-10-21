package org.kenjinx.android.views

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import org.kenjinx.android.R
import org.kenjinx.android.controllers.ControllerDeviceType
import org.kenjinx.android.controllers.ControllerMatchingManager
import org.kenjinx.android.controllers.ControllerMode
import org.kenjinx.android.controllers.ControllerStatus

object ControllerMatchingViews {

    @Composable
    fun Dialog(onClose: () -> Unit) {
        val statuses by ControllerMatchingManager.statusesFlow().collectAsState(emptyList())
        val isPairingActive by ControllerMatchingManager.pairingStateFlow().collectAsState(false)
        val focusRequester = remember { FocusRequester() }

        // --- 主要改动在这里：过滤列表，只保留已连接的设备 ---
        val connectedStatuses = statuses.filter { it.isConnected }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Column(
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    ControllerMatchingManager.handlePairingKey(event.nativeKeyEvent)
                }
                .padding(24.dp)
        ) {
            ConstraintLayout(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)) {
                val (title, closeButton) = createRefs()

                Text(
                    text = stringResource(R.string.controller_matching_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.constrainAs(title) {
                        start.linkTo(parent.start)
                        centerVerticallyTo(parent)
                    }
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.constrainAs(closeButton) {
                        end.linkTo(parent.end)
                        centerVerticallyTo(parent)
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Section: Joy-Con Pairing
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.controller_matching_joycon_pairing_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = if (isPairingActive) stringResource(R.string.controller_matching_pairing_hint_active)
                            else stringResource(R.string.controller_matching_pairing_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(onClick = {
                            if (isPairingActive) {
                                ControllerMatchingManager.cancelJoyConPairing()
                            } else {
                                ControllerMatchingManager.startJoyConPairing()
                            }
                        }) {
                            Text(
                                text = if (isPairingActive) {
                                    stringResource(R.string.controller_matching_cancel_pair)
                                } else {
                                    stringResource(R.string.controller_matching_start_pair)
                                }
                            )
                        }
                    }
                }

                // Bottom Section: Controller List
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.controller_matching_list_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn {
                            // --- 使用过滤后的列表 ---
                            items(connectedStatuses, key = { it.profile.id }) { status ->
                                ControllerEntry(status)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ControllerEntry(status: ControllerStatus) {
        val profile = status.profile
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = profile.deviceName, style = MaterialTheme.typography.titleMedium)
            Text(
                // 由于已经过滤，这里理论上只会显示"已连接"状态
                text = if (status.isConnected) stringResource(R.string.controller_matching_connected)
                else stringResource(R.string.controller_matching_disconnected),
                style = MaterialTheme.typography.bodySmall,
                color = if (status.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = describeMode(profile),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            when (profile.type) {
                ControllerDeviceType.Generic -> GenericActions(profile.descriptor, profile.mode)
                ControllerDeviceType.JoyConLeft, ControllerDeviceType.JoyConRight -> JoyConActions(profile)
            }
        }
    }

    @Composable
    private fun GenericActions(descriptor: String, mode: ControllerMode) {
        val isMatched = mode == ControllerMode.GenericMatched
        Button(onClick = { ControllerMatchingManager.toggleGenericMatch(descriptor) }) {
            Text(if (isMatched) stringResource(R.string.controller_matching_unmatch) else stringResource(R.string.controller_matching_match))
        }
    }

    @Composable
    private fun JoyConActions(profile: org.kenjinx.android.controllers.ControllerProfile) {
        val partnerName = profile.pairedDescriptor?.let {
            ControllerMatchingManager.getProfileForDescriptor(it)?.deviceName ?: it
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { ControllerMatchingManager.setJoyConSingle(profile.descriptor) },
                enabled = profile.mode != ControllerMode.JoyConSingle
            ) {
                Text(stringResource(R.string.controller_matching_use_single))
            }
            if (profile.pairedDescriptor != null) {
                Button(onClick = { ControllerMatchingManager.clearJoyConPair(profile.descriptor) }) {
                    Text(stringResource(R.string.controller_matching_stop_pair))
                }
            }
        }
        Column {
            if (profile.mode == ControllerMode.JoyConPairedPrimary && partnerName != null) {
                Text(
                    text = stringResource(R.string.controller_matching_pair_with, partnerName),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (profile.mode == ControllerMode.JoyConPairedSecondary && partnerName != null) {
                Text(
                    text = stringResource(R.string.controller_matching_secondary_pair, partnerName),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    @Composable
    private fun describeMode(profile: org.kenjinx.android.controllers.ControllerProfile): String {
        return when (profile.mode) {
            ControllerMode.Unmatched -> stringResource(R.string.controller_matching_status_unmatched)
            ControllerMode.GenericMatched -> stringResource(R.string.controller_matching_status_generic)
            ControllerMode.JoyConSingle -> stringResource(R.string.controller_matching_status_single)
            ControllerMode.JoyConPairedPrimary -> stringResource(R.string.controller_matching_status_paired_primary)
            ControllerMode.JoyConPairedSecondary -> stringResource(R.string.controller_matching_status_paired_secondary)
        }
    }
}
