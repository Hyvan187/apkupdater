package org.uniapk.upgrader.glass
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
object UniLayout { const val PAGE=20;const val CARD=18;const val COMPACT_CARD=12;const val GAP=14;const val MIN_TOUCH=48 }
@Composable fun spacing(value:Int)=(value*(if(LocalAppearance.current.compact).67f else 1f)).dp
