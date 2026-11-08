package org.uniapk.upgrader.glass
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
@Composable fun spacing(value:Int)=(value*(if(LocalAppearance.current.compact).67f else 1f)).dp
