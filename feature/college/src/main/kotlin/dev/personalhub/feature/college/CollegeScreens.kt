package dev.personalhub.feature.college

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personalhub.college.api.*
import dev.personalhub.design.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as NameStyle
import java.util.Locale

enum class CollegePage(val title: String) { OVERVIEW("College"), TIMETABLE("Schedule"), ATTENDANCE("Attendance"), CALENDAR("Calendar"), MARKS("Marks"), CGPA("CGPA"), EXAMS("Exams"), HISTORY("Grade History") }

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val syncFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")
private val palette @Composable get() = listOf(Hub.colors.lime, Hub.colors.mint, Hub.colors.cyan, Hub.colors.peach, Hub.colors.apricot, Hub.colors.lavender)
@Composable private fun courseColor(s: CollegeSnapshot, code: String): Color {
    val codes = s.courses.map { it.code }.plus(s.meetings.map { it.courseCode }).distinct()
    return palette[(codes.indexOf(code).coerceAtLeast(0)) % palette.size]
}

/** College destination body. [onConnect] opens the owner-operated portal sync (optional semester). */
@Composable fun CollegeContent(vm: CollegeViewModel, page: CollegePage, onPage: (CollegePage) -> Unit, onConnect: (String?, Boolean) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (page != CollegePage.OVERVIEW) ScreenTitle(page.title, onBack = { onPage(CollegePage.OVERVIEW) }, back = HubIcons.Back,
            subtitle = ui.snapshot.semester?.name)
        if (!ui.loaded) return@Column
        if (!ui.snapshot.connected && page != CollegePage.CALENDAR) { ConnectCard(onConnect); return@Column }
        when (page) {
            CollegePage.OVERVIEW -> Overview(vm, ui, onPage, onConnect)
            CollegePage.TIMETABLE -> Timetable(ui.snapshot)
            CollegePage.ATTENDANCE -> Attendance(vm, ui)
            CollegePage.CALENDAR -> AcademicCalendar(ui.snapshot, vm)
            CollegePage.MARKS -> Marks(ui.snapshot)
            CollegePage.EXAMS -> Exams(ui.snapshot, vm)
            CollegePage.HISTORY -> GradeHistory(ui.snapshot)
            CollegePage.CGPA -> Cgpa(vm, ui)
        }
    }
}

@Composable private fun ConnectCard(onConnect: (String?, Boolean) -> Unit) {
    HubPanel(padding = 22.dp) {
        Pill("VTOP · CHENNAI", Hub.colors.lime)
        Text("Connect your college account", style = MaterialTheme.typography.displaySmall, color = Hub.colors.ink)
        Text("You'll sign in on the official portal inside this app. Your password and captcha stay on the portal page — PersonalHub never reads or stores them.",
            style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
        HubButton("Connect VTOP", { onConnect(null, true) }, Modifier.fillMaxWidth(), trailingIcon = HubIcons.Arrow)
        Text("Timetable, attendance, marks and grade history are saved on this device for offline use.", style = MaterialTheme.typography.bodySmall,
            color = Hub.colors.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- Overview
@Composable private fun Overview(vm: CollegeViewModel, ui: CollegeUi, onPage: (CollegePage) -> Unit, onConnect: (String?, Boolean) -> Unit) {
    val s = ui.snapshot
    val sum = s.summary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BigStat("CGPA", sum?.cgpa?.let { "%.2f".format(it) } ?: "—")
        BigStat("Credits", sum?.creditsEarned?.let { trim(it) } ?: "—", caption = "earned")
        val low = s.attendance.count { (it.percent ?: 100.0) < ui.target }
        BigStat("Low att.", if (s.attendance.isEmpty()) "—" else low.toString(), caption = "below ${ui.target}%")
    }
    UpcomingClasses(vm, s) { onPage(CollegePage.TIMETABLE) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tile("Attendance", HubIcons.Check, Hub.colors.mint, Modifier.weight(1f)) { onPage(CollegePage.ATTENDANCE) }
        Tile("Calendar", HubIcons.Calendar, Hub.colors.lavender, Modifier.weight(1f)) { onPage(CollegePage.CALENDAR) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tile("Marks", HubIcons.Chart, Hub.colors.apricot, Modifier.weight(1f)) { onPage(CollegePage.MARKS) }
        Tile("Exams", HubIcons.Edit, Hub.colors.peach, Modifier.weight(1f)) { onPage(CollegePage.EXAMS) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tile("CGPA", HubIcons.Sparkle, Hub.colors.lime, Modifier.weight(1f)) { onPage(CollegePage.CGPA) }
        Tile("Grades", HubIcons.Chart, Hub.colors.cyan, Modifier.weight(1f)) { onPage(CollegePage.HISTORY) }
    }
    SyncCard(vm, s, onConnect)
}

@Composable private fun Tile(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    HubSurface(modifier, color = color, onClick = onClick, onClickLabel = "Open $label") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Hub.colors.surface).border(1.5.dp, Hub.colors.outline, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Hub.colors.ink, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(HubIcons.Arrow, null, tint = Hub.colors.onAccent, modifier = Modifier.size(20.dp))
            }
            Text(label, style = MaterialTheme.typography.titleLarge, color = Hub.colors.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Swipeable strip of upcoming classes: swipe for the class after, tap to open the schedule. Cards share one size. */
@Composable fun UpcomingClasses(vm: CollegeViewModel, s: CollegeSnapshot, onOpen: () -> Unit) {
    val list = remember(s) { vm.upcoming(s) }
    if (list.isEmpty()) return
    val pager = androidx.compose.foundation.pager.rememberPagerState { list.size }
    androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 8.dp, beyondViewportPageCount = 1) { i ->
        NextClassCard(list[i], s, onClick = onOpen, label = if (i == 0) "Next class" else "Then", position = "${i + 1}/${list.size}")
    }
}

@Composable fun NextClassCard(next: NextClass, s: CollegeSnapshot, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, label: String = "Next class", position: String? = null) {
    val m = next.meeting
    val today = next.at.toLocalDate() == LocalDate.now()
    HubCard(if (today) "$label · ${next.at.toLocalTime().format(timeFmt)}" else "$label · ${next.at.dayOfWeek.getDisplayName(NameStyle.SHORT, Locale.getDefault())} ${next.at.toLocalTime().format(timeFmt)}",
        modifier, color = courseColor(s, m.courseCode), onClick = onClick, trailing = position?.let { p -> { Pill(p, Hub.colors.surface) } }) {
        Text(next.course?.title ?: m.courseCode, style = MaterialTheme.typography.headlineSmall, color = Hub.colors.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(m.courseCode, Hub.colors.surface)
            Pill(componentLabel(m.component), Hub.colors.surface)
            m.venue?.let { Pill(it, Hub.colors.surface) }
        }
        Text("${m.start.format(timeFmt)} – ${m.end.format(timeFmt)} · slot ${m.slot}", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.onAccent)
    }
}

@Composable private fun SyncCard(vm: CollegeViewModel, s: CollegeSnapshot, onConnect: (String?, Boolean) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    HubCard("Portal sync", trailing = { Pill(s.syncedAt?.let { "Synced" } ?: "Never", Hub.colors.mint) }) {
        s.syncedAt?.let { Text("Last sync ${it.atZone(vm.time.zone).format(syncFmt)}", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.inkSoft) }
        s.statuses.sortedBy { it.dataset.ordinal }.forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(st.state.name.lowercase().replaceFirstChar(Char::uppercase), when (st.state) { ImportState.OK -> Hub.colors.mint; ImportState.EMPTY -> Hub.colors.raised; ImportState.FAILED -> Hub.colors.peach; ImportState.SKIPPED -> Hub.colors.lavender })
                Text("${st.dataset.label} · ${st.detail}", style = MaterialTheme.typography.bodySmall, color = Hub.colors.ink, modifier = Modifier.weight(1f))
            }
        }
        Text("Imported values are unverified until you compare them with the portal once.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        if (s.semesters.size > 1) {
            Text("SEMESTER", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
            PillTabs(s.semesters.map { it.name }, s.semesters.indexOfFirst { it.id == s.semester?.id }, { onConnect(s.semesters[it].id, true) }, color = Hub.colors.cyan)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubButton("Sync now", { onConnect(s.semester?.id, false) }, Modifier.weight(1f), icon = HubIcons.Sync)
            HubButton(if (confirm) "Confirm" else "Disconnect", { if (confirm) vm.disconnect { dev.personalhub.feature.college.SessionHooks.clear() } else confirm = true }, tone = ButtonTone.Danger)
        }
        HubButton("Full refresh", { onConnect(s.semester?.id, true) }, tone = ButtonTone.Plain, compact = true, icon = HubIcons.Sync)
        Text("Sync now refreshes timetable, attendance, marks and exams. Grade history and the academic calendar refresh on semester change or Full refresh.",
            style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        if (confirm) Text("Disconnect deletes saved academic data from this device and signs the portal session out.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.danger)
    }
}

/** App-provided hook so the feature can clear the portal session without depending on the WebView module. */
object SessionHooks { var clear: () -> Unit = {} }

// ---------------------------------------------------------------- Timetable (REF-03)
private val weekDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

@Composable private fun Timetable(s: CollegeSnapshot) {
    if (s.meetings.isEmpty()) { EmptyState("No timetable", "The last sync found no timetable grid for this semester."); CourseList(s); return }
    val days = weekDays.filter { d -> d != DayOfWeek.SATURDAY || s.meetingsOn(d).isNotEmpty() }
    val first = s.meetings.minOf { it.start }.hour
    val last = (s.meetings.maxOf { it.end }.let { if (it.minute > 0) it.hour + 1 else it.hour }).coerceAtLeast(first + 1)
    // Fixed hour width so every block is wide enough for its course code; the grid scrolls sideways.
    val hourWidth = 72.dp
    val rowHeight = 68.dp
    val gridWidth = hourWidth * (last - first)
    val scroll = rememberScrollState()
    HubPanel(padding = 0.dp) {
        Row {
            Column(Modifier.width(34.dp)) {
                Spacer(Modifier.height(30.dp))
                days.forEach { d ->
                    HubDivider()
                    Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.Center) {
                        Text(d.getDisplayName(NameStyle.SHORT, Locale.getDefault()).take(2), style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink)
                    }
                }
            }
            Box(Modifier.width(Hub.Stroke).height(30.dp + (rowHeight + Hub.Stroke) * days.size).background(Hub.colors.outline))
            Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                Row(Modifier.width(gridWidth + 8.dp).height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                    (first until last).forEach { h ->
                        Text(java.time.LocalTime.of(h, 0).format(DateTimeFormatter.ofPattern("h a")), style = MaterialTheme.typography.labelSmall,
                            color = Hub.colors.inkSoft, modifier = Modifier.width(hourWidth).padding(start = 4.dp))
                    }
                }
                days.forEach { d ->
                    Box(Modifier.width(gridWidth + 8.dp).height(Hub.Stroke).background(Hub.colors.outline))
                    Box(Modifier.width(gridWidth + 8.dp).height(rowHeight)) {
                        // faint hour guides
                        (1 until last - first).forEach { i -> Box(Modifier.offset(x = hourWidth * i).width(1.dp).fillMaxHeight().background(Hub.colors.dot)) }
                        s.meetingsOn(d).forEach { m ->
                            val startMin = m.start.toSecondOfDay() / 60f - first * 60
                            val lenMin = Duration.between(m.start, m.end).toMinutes().toFloat()
                            val lab = componentOf(m.component) == "LAB"
                            val shape = RoundedCornerShape(7.dp)
                            Box(Modifier.offset(x = hourWidth * (startMin / 60f) + 2.dp, y = if (lab) 36.dp else 6.dp)
                                .width(hourWidth * (lenMin / 60f) - 4.dp).height(26.dp)
                                .clip(shape).background(courseColor(s, m.courseCode)).border(1.5.dp, Hub.colors.outline, shape)
                                .semantics { contentDescription = "${m.courseCode} ${componentLabel(m.component)} ${m.start.format(timeFmt)}" },
                                contentAlignment = Alignment.Center) {
                                Text(m.courseCode, style = MaterialTheme.typography.labelSmall, color = Hub.colors.onAccent, maxLines = 1, softWrap = false,
                                    overflow = TextOverflow.Clip, modifier = Modifier.padding(horizontal = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    Text("Upper row: theory · lower row: lab. Swipe the grid sideways for later hours.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    var dayIndex by rememberSaveable { mutableIntStateOf(days.indexOf(LocalDate.now().dayOfWeek).coerceAtLeast(0)) }
    SectionHeader("Day view")
    PillTabs(days.map { it.getDisplayName(NameStyle.SHORT, Locale.getDefault()) }, dayIndex, { dayIndex = it }, color = Hub.colors.lime)
    val list = s.meetingsOn(days[dayIndex.coerceIn(0, days.lastIndex)])
    if (list.isEmpty()) EmptyState("Free day", "No classes in the weekly timetable.")
    list.forEach { m -> MeetingRow(s, m) }
    CourseList(s)
}

@Composable private fun MeetingRow(s: CollegeSnapshot, m: ClassMeeting) {
    val course = s.course(m.courseCode, m.component)
    HubSurface(Modifier.fillMaxWidth(), shadow = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(66.dp)) {
                Text(m.start.format(timeFmt), style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink)
                Text(m.end.format(timeFmt), style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
            }
            Box(Modifier.width(6.dp).height(44.dp).clip(RoundedCornerShape(3.dp)).background(courseColor(s, m.courseCode)).border(1.dp, Hub.colors.outline, RoundedCornerShape(3.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course?.title ?: m.courseCode, style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(m.courseCode, Hub.colors.cyan); Pill(componentLabel(m.component), Hub.colors.mint)
                }
                m.venue?.let { Pill(it, Hub.colors.apricot) }
            }
        }
    }
}

@Composable private fun CourseList(s: CollegeSnapshot) {
    if (s.courses.isEmpty()) return
    SectionHeader("Courses · ${s.courses.size}")
    s.courses.forEach { c ->
        HubPanel(padding = 14.dp) {
            Text(c.title, style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollRow()) {
                Pill(c.code, Hub.colors.cyan); if (c.type.isNotBlank()) Pill(componentLabel(c.type), Hub.colors.mint)
                c.credits?.let { Pill("${trim(it)} Credits", Hub.colors.apricot) }
            }
            listOfNotNull(c.slot?.let { "Slot $it" }, c.venue, c.faculty).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
            }
        }
    }
}

@Composable private fun Modifier.horizontalScrollRow() = this.horizontalScroll(rememberScrollState())

// ---------------------------------------------------------------- Attendance
@Composable private fun Attendance(vm: CollegeViewModel, ui: CollegeUi) {
    val s = ui.snapshot
    Text("TARGET", style = MaterialTheme.typography.labelLarge, color = Hub.colors.ink)
    val targets = listOf(75, 80, 85, 90)
    ChoiceCells(targets.map { "$it%" }, targets.indexOf(ui.target).takeIf { it >= 0 }, { vm.setTarget(targets[it]) }, description = "Attendance target")
    staleNote(s, Dataset.ATTENDANCE)
    if (s.attendance.isEmpty()) { EmptyState("No attendance yet", "The portal returned no attendance rows for this semester."); return }
    s.attendance.sortedBy { it.percent ?: 101.0 }.forEach { a ->
        val pct = a.percent
        val plan = attendancePlan(a.attended, a.total, ui.target)
        val color = when { pct == null -> Hub.colors.raised; pct < ui.target -> Hub.colors.peach; pct < ui.target + 5 -> Hub.colors.apricot; else -> Hub.colors.mint }
        HubPanel {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(a.courseTitle ?: s.course(a.courseCode, a.type)?.title ?: a.courseCode, style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill(a.courseCode, Hub.colors.cyan); if (a.type.isNotBlank()) Pill(componentLabel(a.type), Hub.colors.mint) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(pct?.let { "%.0f%%".format(Math.floor(it)) } ?: "—", style = MaterialTheme.typography.displaySmall, color = Hub.colors.ink)
                    a.portalPercent?.let { Text("portal $it%", style = MaterialTheme.typography.labelSmall, color = Hub.colors.inkSoft) }
                }
            }
            HubBar(((pct ?: 0.0) / 100).toFloat(), color = color)
            Text("${a.attended} of ${a.total} attended · " + when {
                a.total == 0 -> "no classes recorded yet"
                plan.mustAttend > 0 -> "attend the next ${plan.mustAttend} to reach ${ui.target}%"
                plan.canSkip > 0 -> "can miss ${plan.canSkip} and stay at ${ui.target}%"
                else -> "on the edge — don't miss the next class"
            }, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink)
        }
    }
    Text("Percentages here are floor(attended ÷ total); the portal may round differently and its value is shown as “portal”.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
}

@Composable private fun staleNote(s: CollegeSnapshot, d: Dataset) {
    val st = s.statuses.firstOrNull { it.dataset == d } ?: return
    if (st.state == ImportState.FAILED) HubPanel(color = Hub.colors.apricot, padding = 12.dp) {
        Text("Last ${d.label.lowercase()} sync failed (${st.detail}). Showing the previously saved copy.", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.onAccent)
    }
}

// ---------------------------------------------------------------- Calendar (REF-04)
@Composable private fun AcademicCalendar(s: CollegeSnapshot, vm: CollegeViewModel) {
    val today = vm.time.today()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val ym = YearMonth.parse(month)
    val sel = LocalDate.parse(selected)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        HubIconButton(HubIcons.Back, "Previous month", { month = ym.minusMonths(1).toString() })
        HubSurface(color = Hub.colors.lime, radius = 50.dp, shadow = 3.dp) {
            Text("${ym.month.getDisplayName(NameStyle.FULL, Locale.getDefault())} ${ym.year}", style = MaterialTheme.typography.titleLarge, color = Hub.colors.onAccent, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        }
        HubIconButton(HubIcons.Arrow, "Next month", { month = ym.plusMonths(1).toString() })
    }
    HubPanel(padding = 0.dp) {
        Row(Modifier.fillMaxWidth().background(Hub.colors.lavender)) {
            DayOfWeek.entries.forEach { d -> Text(d.getDisplayName(NameStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelMedium, color = Hub.colors.onAccent, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 8.dp)) }
        }
        val start = ym.atDay(1).minusDays((ym.atDay(1).dayOfWeek.value - 1).toLong())
        (0 until 6).forEach { week ->
            val rowStart = start.plusDays(week * 7L)
            if (week == 5 && rowStart.month != ym.month) return@forEach
            HubDivider()
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                (0 until 7).forEach { i ->
                    val date = rowStart.plusDays(i.toLong())
                    val inMonth = date.month == ym.month
                    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                    val portal = s.calendarOn(date)
                    val kinds = portal.map { it.kind }.toSet()
                    val hasExam = s.examsOn(date).isNotEmpty()
                    val fill = when {
                        !inMonth -> Hub.colors.surface
                        hasExam || CalendarKind.EXAM in kinds -> Hub.colors.lime
                        CalendarKind.HOLIDAY in kinds -> Hub.colors.peach
                        CalendarKind.INSTRUCTIONAL in kinds -> Hub.colors.cyan
                        portal.isNotEmpty() -> Hub.colors.lavender
                        s.calendar.isEmpty() && !weekend && s.meetingsOn(date.dayOfWeek).isNotEmpty() -> Hub.colors.cyan
                        weekend -> Hub.colors.apricot
                        else -> Hub.colors.surface
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().background(fill).clickable(role = Role.Button, onClickLabel = date.toString()) { selected = date.toString() }, contentAlignment = Alignment.Center) {
                        val mark = when { date == sel -> Hub.colors.ink; date == today -> Hub.colors.danger; else -> null }
                        Box(Modifier.size(36.dp).then(if (mark != null) Modifier.border(2.dp, mark, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, color = if (inMonth) Hub.colors.onAccent.takeIf { fill != Hub.colors.surface } ?: Hub.colors.ink else Hub.colors.inkSoft)
                        }
                    }
                    if (i < 6) Box(Modifier.width(1.dp).fillMaxHeight().background(Hub.colors.outline.copy(alpha = 0.4f)))
                }
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Hub.colors.cyan to "Instructional", Hub.colors.peach to "Holiday", Hub.colors.lime to "Exam", Hub.colors.lavender to "Other", Hub.colors.apricot to "Weekend").forEach { (c, l) ->
            Dot(c, 12.dp); Text(l, style = MaterialTheme.typography.bodySmall, color = Hub.colors.ink)
        }
    }
    val labels = s.calendarOn(sel)
    val exams = s.examsOn(sel)
    val holiday = labels.any { it.kind == CalendarKind.HOLIDAY }
    val classes = if (holiday) emptyList() else s.meetingsOn(sel.dayOfWeek)
    HubCard(sel.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), trailing = {
        val first = labels.firstOrNull()
        if (first != null) Pill(kindLabel(first.kind), kindColor(first.kind))
        else Pill(if (classes.isEmpty()) "No classes" else "${classes.size} classes", if (classes.isEmpty()) Hub.colors.raised else Hub.colors.cyan)
    }) {
        labels.forEach { Text(it.label, style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink) }
        exams.forEach { e -> Text("${e.examType} · ${s.course(e.courseCode)?.title ?: e.courseTitle ?: e.courseCode}${e.examTime?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink) }
        classes.forEach { m -> Text("${m.start.format(timeFmt)}  ${s.course(m.courseCode, m.component)?.title ?: m.courseCode} · ${componentLabel(m.component)}", style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink) }
        Text(when {
            s.calendar.isEmpty() -> "Academic calendar not synced yet; classes come from the weekly timetable."
            holiday -> "Holiday per the academic calendar - weekly classes hidden."
            else -> "Labels: VTOP academic calendar. Classes: weekly timetable."
        }, style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
}

@Composable private fun kindColor(k: CalendarKind) = when (k) { CalendarKind.HOLIDAY -> Hub.colors.peach; CalendarKind.EXAM -> Hub.colors.lime; CalendarKind.INSTRUCTIONAL -> Hub.colors.cyan; CalendarKind.OTHER -> Hub.colors.lavender }
private fun kindLabel(k: CalendarKind) = when (k) { CalendarKind.HOLIDAY -> "Holiday"; CalendarKind.EXAM -> "Exam"; CalendarKind.INSTRUCTIONAL -> "Instructional"; CalendarKind.OTHER -> "Event" }

// ---------------------------------------------------------------- Exams
private val examDateFmt = DateTimeFormatter.ofPattern("EEE d MMM")
@Composable private fun Exams(s: CollegeSnapshot, vm: CollegeViewModel) {
    staleNote(s, Dataset.EXAMS)
    if (s.exams.isEmpty()) { EmptyState("No exams scheduled", "The portal hasn't published an exam schedule for this semester yet."); return }
    val today = vm.time.today()
    s.exams.groupBy { it.examType.ifBlank { "Exams" } }.forEach { (type, rows) ->
        SectionHeader(type)
        rows.sortedWith(compareBy(nullsLast()) { it.date }).forEach { e -> ExamCard(s, e, today) }
    }
}

@Composable private fun ExamCard(s: CollegeSnapshot, e: ExamSlot, today: LocalDate) {
    val date = e.date
    val past = date != null && date.isBefore(today)
    HubPanel(color = if (past) Hub.colors.raised else Hub.colors.surface) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.course(e.courseCode)?.title ?: e.courseTitle ?: e.courseCode, style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill(e.courseCode, Hub.colors.cyan); e.session?.let { Pill(it, Hub.colors.mint) } }
            }
            HubSurface(color = if (past) Hub.colors.surface else Hub.colors.lime, radius = 12.dp, shadow = 3.dp) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date?.dayOfMonth?.toString() ?: "?", style = MaterialTheme.typography.headlineMedium, color = Hub.colors.onAccent)
                    Text(date?.format(DateTimeFormatter.ofPattern("MMM")) ?: "TBA", style = MaterialTheme.typography.labelMedium, color = Hub.colors.onAccent)
                }
            }
        }
        listOfNotNull(date?.format(examDateFmt), e.examTime, e.reportingTime?.let { "report $it" }).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink)
        }
        listOfNotNull(e.venue, e.seatLocation, e.seatNumber?.let { "seat $it" }).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        }
    }
}

// ---------------------------------------------------------------- Marks
@Composable private fun Marks(s: CollegeSnapshot) {
    staleNote(s, Dataset.MARKS)
    if (s.marks.isEmpty()) { EmptyState("No marks yet", "No assessment rows were found for this semester."); return }
    s.marks.groupBy { it.courseCode to it.courseType }.forEach { (key, rows) ->
        val course = s.course(key.first, key.second)
        val known = rows.filter { it.weighted != null }
        HubPanel {
            Text(course?.title ?: key.first, style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(key.first, Hub.colors.cyan); key.second?.let { Pill(componentLabel(it), Hub.colors.mint) }
                Pill("${trim(known.sumOf { it.weighted!! })} / ${trim(known.sumOf { it.weightage ?: 0.0 })}", Hub.colors.lime)
            }
            rows.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.title, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f))
                    Text(m.scored?.let { sc -> "${trim(sc)}/${m.maxMark?.let(::trim) ?: "?"}" } ?: "pending", style = MaterialTheme.typography.titleSmall, color = if (m.scored == null) Hub.colors.inkSoft else Hub.colors.ink)
                    Text(m.weighted?.let { " → ${trim(it)}" } ?: "", style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft, modifier = Modifier.widthIn(min = 52.dp), textAlign = TextAlign.End)
                }
            }
            if (rows.size > known.size) Text("${rows.size - known.size} component(s) pending — not counted as zero.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
        }
    }
}

// ---------------------------------------------------------------- CGPA (REF-05)
@Composable private fun Cgpa(vm: CollegeViewModel, ui: CollegeUi) {
    val s = ui.snapshot
    val courses = scenarioCourses(s)
    val chosen = courses.mapNotNull { c -> ui.scenario[c.key]?.let { c.credits!! to it } }
    val base = s.summary?.creditsEarned
    val expected = scenarioCgpa(s.summary?.cgpa, base, chosen)
    val semGpa = gpa(chosen).gpa
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BigStat("Current", s.summary?.cgpa?.let { "%.2f".format(it) } ?: "—", caption = "official")
        BigStat("Expected", if (chosen.isEmpty()) "—" else expected?.let { "%.2f".format(it) } ?: "?", caption = "scenario")
    }
    Text(when {
        chosen.isEmpty() -> "Pick a grade for each course to see a scenario."
        expected == null -> "Official CGPA or earned credits weren't found, so only the semester GPA (${semGpa?.let { "%.2f".format(it) } ?: "—"}) can be estimated."
        else -> "Semester GPA %.2f over ${trim(chosen.sumOf { it.first })} credits, combined with ${trim(base!!)} earned credits. Estimate only — repeat and regulation rules aren't applied.".format(semGpa)
    }, style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (courses.isEmpty()) { EmptyState("No credit data", "Course credits come from the timetable sync. Sync again once registration shows credits."); return }
    courses.forEach { c ->
        HubPanel(padding = 14.dp) {
            Text(c.title, style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(c.code, Hub.colors.cyan); if (c.type.isNotBlank()) Pill(componentLabel(c.type), Hub.colors.mint)
                Spacer(Modifier.weight(1f)); Pill("${trim(c.credits!!)} Credits", Hub.colors.apricot)
            }
            val sel = ui.scenario[c.key]?.let { scenarioGrades.indexOf(it) }
            ChoiceCells(scenarioGrades, sel, { i -> vm.setGrade(c.key, scenarioGrades[i].takeUnless { sel == i }) }, description = "${c.title} grade")
        }
    }
    if (ui.scenario.isNotEmpty()) HubButton("Reset scenario", vm::resetScenario, tone = ButtonTone.Plain, icon = HubIcons.Undo)
}

// ---------------------------------------------------------------- Grade History
@Composable private fun GradeHistory(s: CollegeSnapshot) {
    staleNote(s, Dataset.RESULTS)
    val results = s.results.reversed()
    var index by rememberSaveable { mutableIntStateOf(0) }
    if (results.isEmpty()) {
        EmptyState("No semester results yet", "Sync again to import Grade View for semesters with published grades.")
    } else {
        val r = results[index.coerceIn(0, results.lastIndex)]
        PillTabs(results.map { shortSemester(it.semester.name) }, index.coerceIn(0, results.lastIndex), { index = it }, color = Hub.colors.cyan)
        Text(r.semester.name, style = MaterialTheme.typography.titleMedium, color = Hub.colors.ink)
        val g = r.gpa
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BigStat("GPA", g.gpa?.let { "%.2f".format(it) } ?: "—", caption = "computed")
            BigStat("Credits", trim(g.credits), caption = "graded")
            BigStat("CGPA", s.summary?.cgpa?.let { "%.2f".format(it) } ?: "—", caption = "official")
        }
        Text("Semester GPA is computed from the credits and letters below (S=10 … F=0); the portal's own value may differ for non-letter grades.",
            style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        r.courses.forEach { c -> ResultCard(c) }
    }
    if (s.grades.isNotEmpty()) {
        SectionHeader("All attempts · ${s.grades.size}")
        HubPanel(padding = 12.dp) {
            s.grades.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.courseCode, style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft, modifier = Modifier.width(86.dp))
                    Text(g.title, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    g.credits?.let { Text(trim(it), style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft) }
                    Pill(g.grade, if (g.grade == "S" || g.grade == "A") Hub.colors.lime else if (g.grade == "F") Hub.colors.peach else Hub.colors.mint)
                }
            }
        }
    }
}

private fun shortSemester(name: String): String {
    val season = listOf("Fall", "Winter", "Summer", "Short").firstOrNull { name.contains(it, ignoreCase = true) } ?: return name.take(18)
    val year = Regex("""\d{4}-\d{2}""").find(name)?.value ?: return season
    return "$season $year"
}

@Composable private fun ResultCard(c: GradedCourse) {
    HubPanel {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleLarge, color = Hub.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollRow()) {
                    Pill(c.code, Hub.colors.cyan); c.type?.let { Pill(componentLabel(it), Hub.colors.mint) }
                    c.credits?.let { Pill("${trim(it)} Credits", Hub.colors.apricot) }
                }
            }
            HubSurface(color = if (c.grade == "F") Hub.colors.peach else Hub.colors.lime, radius = 12.dp, shadow = 3.dp) {
                Column(Modifier.widthIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.grade ?: "—", style = MaterialTheme.typography.displaySmall, color = Hub.colors.onAccent)
                    c.grandTotal?.let { Text(trim(it), style = MaterialTheme.typography.labelMedium, color = Hub.colors.onAccent) }
                }
            }
        }
        if (c.components.isNotEmpty()) {
            HubDivider()
            c.components.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.title, style = MaterialTheme.typography.bodyMedium, color = Hub.colors.ink, modifier = Modifier.weight(1f))
                    Text(m.scored?.let { sc -> "${trim(sc)}/${m.maxMark?.let(::trim) ?: "?"}" } ?: "—", style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink)
                    Text(m.weighted?.let { " → ${trim(it)}" } ?: "", style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft, modifier = Modifier.widthIn(min = 56.dp), textAlign = TextAlign.End)
                }
            }
            c.grandTotal?.let {
                Row { Text("Total", style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink, modifier = Modifier.weight(1f)); Text(trim(it), style = MaterialTheme.typography.titleSmall, color = Hub.colors.ink) }
            }
        }
        if (c.ranges.isNotEmpty()) {
            Text("GRADE RANGES", style = MaterialTheme.typography.labelMedium, color = Hub.colors.inkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollRow()) {
                c.ranges.forEach { g -> Pill("${g.grade}  ${g.range}", if (g.grade == c.grade) Hub.colors.lime else Hub.colors.surface) }
            }
        } else if (c.components.isEmpty()) Text("No mark breakdown published for this course.", style = MaterialTheme.typography.bodySmall, color = Hub.colors.inkSoft)
    }
}

fun componentLabel(type: String): String = when (componentOf(type)) { "THEORY" -> "Theory"; "LAB" -> "Lab"; "PROJECT" -> "Project"; else -> type }
internal fun trim(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)

/** Today-page summary: next class and lowest attendance, opening College pages. */
@Composable fun CollegeTodayCards(vm: CollegeViewModel, onOpen: (CollegePage) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val s = ui.snapshot
    if (!ui.loaded) return
    if (!s.connected) {
        HubCard("College", trailing = { Pill("Not connected", Hub.colors.lavender) }, onClick = { onOpen(CollegePage.OVERVIEW) }) {
            Text("Connect VTOP to see your next class and attendance here.", style = MaterialTheme.typography.bodyLarge, color = Hub.colors.inkSoft)
        }
        return
    }
    vm.nextClass(s)?.let { NextClassCard(it, s, onClick = { onOpen(CollegePage.TIMETABLE) }) }
    val today = vm.time.today()
    s.exams.filter { val d = it.date; d != null && !d.isBefore(today) && !d.isAfter(today.plusDays(14)) }.minByOrNull { it.date!! }?.let { e ->
        HubCard("Next exam · ${e.date!!.format(examDateFmt)}", color = Hub.colors.lime, onClick = { onOpen(CollegePage.EXAMS) }) {
            Text("${e.examType} · ${s.course(e.courseCode)?.title ?: e.courseTitle ?: e.courseCode}", style = MaterialTheme.typography.titleMedium, color = Hub.colors.onAccent)
            listOfNotNull(e.examTime, e.venue, e.seatNumber?.let { "seat $it" }).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Hub.colors.onAccent) }
        }
    }
    s.calendarOn(today).firstOrNull { it.kind == CalendarKind.HOLIDAY }?.let { h ->
        HubCard("Holiday today", color = Hub.colors.peach, onClick = { onOpen(CollegePage.CALENDAR) }) { Text(h.label, style = MaterialTheme.typography.bodyLarge, color = Hub.colors.onAccent) }
    }
    val low = s.attendance.filter { (it.percent ?: 100.0) < ui.target }.minByOrNull { it.percent ?: 100.0 }
    if (low != null) HubCard("Attendance warning", color = Hub.colors.peach, onClick = { onOpen(CollegePage.ATTENDANCE) }) {
        val plan = attendancePlan(low.attended, low.total, ui.target)
        Text("${low.courseTitle ?: low.courseCode} is at %.0f%%. Attend the next ${plan.mustAttend} to reach ${ui.target}%%.".format(Math.floor(low.percent!!)), style = MaterialTheme.typography.bodyLarge, color = Hub.colors.onAccent)
    }
}
