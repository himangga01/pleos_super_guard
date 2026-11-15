package app.pleossupercare.phone.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.R

/** 차 앱(demo-app/app의 ui/Theme.kt)과 같은 색입니다. 화면 시안 v4.2 · UI 토큰 5.2.0. */
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
    val ECard = Color(0xFF2A1219)
    val OnAmber = Color(0xFF30291C)
    val OnMint = Color(0xFF1E3340)
    val OnPurple = Color(0xFF262A4D)
    val Dark = Color(0xFF090E16)

    /** 경고등 색 점(차 앱 경고등 찾기와 같은 값). */
    val WarnRed = Color(0xFFFF4D4D)
    val WarnOrange = Color(0xFFFFA43A)
    val WarnGreen = Color(0xFF3DDC84)
    val WarnBlue = Color(0xFF4C9BFF)
}

/** 앱 글꼴 Pretendard(SIL OFL 1.1, assets/licenses/pretendard-OFL.txt). */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

fun ts(size: TextUnit, color: Color = C.Fg, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Pretendard, fontSize = size, color = color, fontWeight = weight, lineHeight = lineHeight)

// 폰 글자 크기: 차 화면(먼 거리)보다 작지만 한 손 · 차 안에서 읽기 쉽게 본문 17sp 이상.
val TitleL = ts(28.sp, weight = FontWeight.Bold, lineHeight = 36.sp)
val Title = ts(23.sp, weight = FontWeight.SemiBold, lineHeight = 30.sp)
val Head = ts(19.sp, weight = FontWeight.SemiBold, lineHeight = 26.sp)
val Body = ts(17.sp, lineHeight = 25.sp)
val Small = ts(15.sp, C.Mut, lineHeight = 21.sp)
