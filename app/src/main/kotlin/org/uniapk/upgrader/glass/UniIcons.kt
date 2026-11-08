package org.uniapk.upgrader.glass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
/** Small native vectors, shared by visual settings cards. */
object UniIcons {
 private fun icon(name:String,path:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).addPath(PathParser().parsePathString(path).toNodes(),fill=SolidColor(Color.Black)).build()
 val Palette=icon("Palette","M12,2 A10,10 0,1 0,12,22 L14,22 A3,3 0,0 0,16,17 A2,2 0,0 1,18,14 L20,14 A3,3 0,0 0,22,11 A10,10 0,0 0,12,2 Z M7,8 A1.5,1.5 0,1 1,7,11 A1.5,1.5 0,1 1,7,8 Z M11,5 A1.5,1.5 0,1 1,11,8 A1.5,1.5 0,1 1,11,5 Z M16,6 A1.5,1.5 0,1 1,16,9 A1.5,1.5 0,1 1,16,6 Z")
 val Download=icon("Download","M11,3 L13,3 L13,13 L17,9 L18.4,10.4 L12,17 L5.6,10.4 L7,9 L11,13 Z M4,18 L6,18 L6,20 L18,20 L18,18 L20,18 L20,22 L4,22 Z")
 val Wifi=icon("Wifi","M1,8 Q12,-1 23,8 L21,10 Q12,3 3,10 Z M5,12 Q12,6 19,12 L17,14 Q12,10 7,14 Z M9,16 Q12,13 15,16 L12,20 Z")
 val Storage=icon("Storage","M3,3 L21,3 L21,8 L3,8 Z M3,10 L21,10 L21,15 L3,15 Z M3,17 L21,17 L21,22 L3,22 Z")
 val Security=icon("Security","M12,2 L21,6 L20,14 Q18,20 12,23 Q6,20 4,14 L3,6 Z M12,5 L6,8 L7,14 Q9,18 12,20 Q15,18 17,14 L18,8 Z")
 val PhoneAndroid=icon("Phone","M6,1 L18,1 Q20,1 20,3 L20,21 Q20,23 18,23 L6,23 Q4,23 4,21 L4,3 Q4,1 6,1 Z M6,4 L6,19 L18,19 L18,4 Z")
 val Language=icon("Language","M12,2 A10,10 0,1 0,12,22 A10,10 0,1 0,12,2 Z M12,4 Q7,12 12,20 Q17,12 12,4 Z M4,11 L20,11 L20,13 L4,13 Z")
}

