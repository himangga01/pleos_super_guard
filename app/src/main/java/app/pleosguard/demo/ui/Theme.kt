package app.pleosguard.demo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.R

/** 화면 시안 v4.2(build_ui_v4.py)와 UI 토큰 5.2.0의 색입니다. */
object C {
    val Bg = Color(0xFF090E16)
    val Surf = Color(0xFF121C2B)
    val Surf2 = Color(0xFF19283D)
    val Line = Color(0xFF26364D)
    val Fg = Color(0xFFF7FAFF)
    val Mut = Color(0xFFB8C5D8)
    val Dim = Color(0xFF8C99AD)
    val Mint = Color(0xFF5EEAD4)
    val Amber = Color(0xFFFFC857)
    val Red = Color(0xFFFF5B6B)
    val Purple = Color(0xFF9FA4FF)
    val EBg = Color(0xFF1B0B12)
    val EBand = Color(0xFF702132)
    val ECard = Color(0xFF2A1219)
    val ENeu = Color(0xFF3A3346)
    val EPink = Color(0xFFFFCAD1)
    val EDraft = Color(0xFFF7D9DF)
    val Help = Color(0xFF622230)
    val OnAmber = Color(0xFF30291C)
    val OnMint = Color(0xFF1E3340)
    val OnPurple = Color(0xFF262A4D)
    val TestBg = Color(0xFF2B2410)
    val Dark = Color(0xFF090E16)
}

/** 앱 화면 글꼴 Pretendard(SIL OFL 1.1, assets/licenses/pretendard-OFL.txt). */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

fun ts(size: TextUnit, color: Color = C.Fg, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Pretendard, fontSize = size, color = color, fontWeight = weight, lineHeight = lineHeight)

val TitleXL = ts(64.sp, weight = FontWeight.Bold, lineHeight = 80.sp)
val Title = ts(30.sp, weight = FontWeight.SemiBold)
val Head = ts(26.sp, weight = FontWeight.SemiBold)
val Body = ts(24.sp)
val Small = ts(22.sp, C.Mut)
