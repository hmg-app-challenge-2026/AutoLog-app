package kr.autolog.vehicle.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.autolog.vehicle.screenshot.ShotSize
import kr.autolog.vehicle.screenshot.captureAll
import kr.autolog.vehicle.ui.design.AutoLogTheme
import kr.autolog.vehicle.ui.design.Size
import kr.autolog.vehicle.ui.design.Space
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * 디자인 언어 11장 컴포넌트 갤러리. 컴포넌트를 추가·변경하면 여기도 같이 고친다 — 갤러리가 지금 컴포넌트 목록과 같아야 한다.
 * 역할별로 나눠 TALL 한 장씩: 기본(버튼·칩·배지·숫자·배너) · 타일(사람·동작·아바타·동작 카드) · 입력 · 팝업.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GalleryScreenshotTest {
    /** 컴포넌트 갤러리는 세로 공간이 넉넉한 좁은 화면(분할 한 칸) 한 크기로. */
    private val gallerySize = listOf(ShotSize.NARROW)

    @Test
    fun basics() = captureAll("components", gallerySize) {
        Column(Modifier.padding(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            PrimaryButton("계속 기록", onClick = {}, modifier = Modifier.fillMaxWidth())
            SecondaryButton("기록 종료", onClick = {}, modifier = Modifier.fillMaxWidth())
            TertiaryButton("나중에 선택", onClick = {})
            StatusLine("기록 중", AutoLogTheme.colors.success, detail = "35분 경과")
            StatusLine("대기 중", AutoLogTheme.colors.textTertiary)
            TripListRow("본사 → 판교 테크노밸리 거래처", "오늘 09:12 · 23.4 km · 목적 미입력", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
                DriverBadge("u-1", "김영업", onClick = {})
                DriverBadge(null, null, onClick = {})
                CloseButton(onClick = {})
                RoundIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "이전 달", onClick = {})
                RoundIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "다음 달", onClick = {}, enabled = false)
                // 완료 표시 — 갤러리에선 작게(실제 200 dp). 스크린샷은 정지형이라 다 그려진 모양
                SuccessCheck(onDrawn = {}, size = Size.iconButton)
            }
            // 줄 안에서 바로 끝낼 일(extra) — 운행 기록 조회의 운전자 지정
            TripListRow("본사 → 강남 고객사", "11월 7일 14:05 · 운전자 미확정 · 8.6 km", onClick = {}) {
                Text("운전자 지정", style = AutoLogTheme.type.caption, color = AutoLogTheme.colors.textSecondary)
            }
            HeroMetric("23.4", "km", "주행 거리", "계기판 거리를 읽지 못했어요", size = MetricSize.MEDIUM)
            RouteFlow("본사", "판교 테크노밸리 거래처")
            RouteFlow("본사", "판교 테크노밸리 거래처", live = false)
        }
    }

    @Test
    fun tiles() = captureAll("components_tiles", gallerySize) {
        val c = AutoLogTheme.colors
        Column(Modifier.padding(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.s6)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s5)) {
                ProfileTile("u-1", "김영업", "영업1팀", scheduled = true, recent = false, onClick = {})
                ProfileTile("u-2", "이현장", "기술지원팀", scheduled = false, recent = true, onClick = {})
                ProfileTile("u-3", "박관리", "총무팀", scheduled = false, recent = false, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s5)) {
                ActionTile(Icons.Rounded.MoreHoriz, "다른 사람", onClick = {})
                ActionTile(Icons.Rounded.Add, "새 운전자 추가", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s5)) {
                Avatar("u-4", "정다은", Size.avatarSmall)
                AvatarFace("최", Brush.linearGradient(listOf(c.accent, c.accentFill)), Size.avatarSmall)
                IconAvatar(Icons.Rounded.Person, Size.avatarSmall)
            }
            SectionHeader("이번 달 운행 정보")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                DockTile(Icons.Rounded.LocalGasStation, "주유 기록 추가", onClick = {}, modifier = Modifier.weight(1f))
                DockTile(Icons.Rounded.History, "운행 기록 조회", onClick = {}, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                DockTile(Icons.Rounded.Settings, "설정", onClick = {}, modifier = Modifier.weight(1f))
                DockTile(Icons.Rounded.Stop, "기록 종료", onClick = {}, modifier = Modifier.weight(1f), emphasized = true)
            }
        }
    }

    @Test
    fun forms() = captureAll("components_forms", gallerySize) {
        Column(Modifier.padding(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            NavRow(Icons.Rounded.Mail, "메일 계정", "메일 계정 연결됨", onClick = {})
            NavRow(Icons.Rounded.Mail, "메일 계정", "메일 계정 연결됨", onClick = {}, selected = true)
            CheckRow("회계팀", checked = true, onClick = {}, detail = "회사 지정 · accounting@example.com")
            CheckRow("나", checked = false, onClick = {}, detail = "me@example.com")
            PeriodSwitcher(YearMonth.of(2026, 11), canGoNext = false, onPrev = {}, onNext = {})
            InputField(label = "서버 주소", value = "https://autolog.acme.co.kr", onValueChange = {})
            InputField(label = "이름", value = "", onValueChange = {}, placeholder = "예: 홍길동")
            CodeField(label = "연결 코드 6자리", value = "7KQ3", length = 6, onValueChange = {})
            ButtonNote("네트워크에 연결하지 못했어요. 연결되면 다시 보내 주세요", color = AutoLogTheme.colors.danger)
            ChoiceRow("일반 업무", selected = true, onClick = {})
            ChoiceRow("출퇴근", selected = false, onClick = {})
            ChoiceRow("개인 용무", selected = false, onClick = null) // 보기 전용
            ConfidenceRow("상호", "SK 판교주유소", 0.98, onClick = {})
            ConfidenceRow("수량", "4.0 L", 0.41, onClick = {})
            ConfidenceRow("금액", null, null, onClick = {})
            QrImage("https://autolog.example/r/abcd1234?t=demo", Space.s9 * 2)
        }
    }

    /** 숫자판(금액·리터) — 3×4 키가 커서 forms 캡처와 따로 찍는다. 차량 연결 코드는 키보드로 넣는다(CodeField — forms). */
    @Test
    fun keypads() = captureAll("components_keypads", gallerySize) {
        Column(Modifier.padding(Space.s6), verticalArrangement = Arrangement.spacedBy(Space.s6)) {
            NumericKeypad(onDigit = {}, onDelete = {}, allowDecimal = true)
        }
    }

    @Test
    fun modalList() = captureAll("components_modal_close", gallerySize) {
        Box {
            Text(
                "뒤 화면",
                Modifier.padding(Space.s6),
                style = AutoLogTheme.type.display,
                color = AutoLogTheme.colors.textPrimary,
            )
            ModalCard(onClose = {}) {
                ModalText("목록처럼 제목이 필요 없는 팝업은 X만 둬요.")
            }
        }
    }

    @Test
    fun modal() = captureAll("components_modal", gallerySize) {
        Box {
            Text(
                "뒤 화면",
                Modifier.padding(Space.s6),
                style = AutoLogTheme.type.display,
                color = AutoLogTheme.colors.textPrimary,
            )
            ModalCard(title = "가운데 카드", onClose = {}, footer = {
                PrimaryButton("확인", onClick = {}, modifier = Modifier.fillMaxWidth(), height = Size.buttonMd)
            }) {
                ModalText("제목은 X와 같은 줄 가운데, 취소는 오른쪽 위 X, 결정 버튼은 아래에 고정돼요.")
            }
        }
    }
}
