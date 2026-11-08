package org.uniapk.upgrader.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import kotlin.math.*

@Composable fun FavoriteButton(packageName:String) {
 val store=koinInject<FavoriteStore>();val flags by store.state.collectAsStateWithLifecycle()
 val selected=packageName in flags;val context=LocalContext.current
 val label=if(selected)"Aus Favoriten entfernen" else "Zu Favoriten hinzufügen"
 IconToggleButton(checked=selected,onCheckedChange={store.toggle(packageName);android.widget.Toast.makeText(context,if(!selected)"Zu Favoriten hinzugefügt" else "Aus Favoriten entfernt",android.widget.Toast.LENGTH_SHORT).show()},modifier=Modifier.semantics{contentDescription=label}) {
  val color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
  Canvas(Modifier.size(24.dp)) {
   val path=Path();repeat(10){i->val a=(i*PI/5-PI/2);val r=size.minDimension*(if(i%2==0).47f else .22f);val x=center.x+cos(a).toFloat()*r;val y=center.y+sin(a).toFloat()*r;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close()
   if(selected)drawPath(path,color) else drawPath(path,color,style=Stroke(1.7.dp.toPx()))
  }
 }
}
