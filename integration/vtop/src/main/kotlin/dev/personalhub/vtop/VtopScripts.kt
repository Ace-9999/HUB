package dev.personalhub.vtop

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/*
 * In-page acquisition, run only on the verified portal origin after the owner signs in.
 * Requests use the page's own session and CSRF token; responses are parsed with DOMParser into
 * bounded table grids (text only). Credentials, cookies and raw HTML never cross into native code.
 * Endpoint paths follow the pinned reference-source audit (docs research §1) and are validated live.
 */
internal object VtopScripts {
    /** Returns "signed_in" | "login" | "other" — no page text. */
    const val STATE = """(() => {
      const id = document.getElementById('authorizedIDX') || document.querySelector('input[name=authorizedID]');
      if (id && id.value) return 'signed_in';
      if (document.querySelector('input[type=password]')) return 'login';
      return 'other';
    })()"""

    const val POLL = "(() => window.__phSync ? JSON.stringify(window.__phSync) : '')()"

    private const val COMMON = """
      const LIMIT_TABLES = 60, LIMIT_ROWS = 600, LIMIT_TEXT = 300;
      const clean = s => (s || '').replace(/\s+/g, ' ').trim().slice(0, LIMIT_TEXT);
      const cellText = cell => { const c = cell.cloneNode(true); c.querySelectorAll('table,script,style').forEach(n => n.remove()); return clean(c.textContent); };
      const grid = table => {
        const out = []; const spans = [];
        const rows = Array.from(table.rows).slice(0, LIMIT_ROWS);
        rows.forEach((tr, r) => {
          out[r] = out[r] || [];
          let col = 0;
          Array.from(tr.cells).forEach(cell => {
            while (out[r][col] !== undefined) col++;
            const text = cellText(cell);
            const cs = Math.min(cell.colSpan || 1, 20), rs = Math.min(cell.rowSpan || 1, 20);
            for (let dr = 0; dr < rs && r + dr < rows.length; dr++) {
              out[r + dr] = out[r + dr] || [];
              for (let dc = 0; dc < cs; dc++) out[r + dr][col + dc] = text;
            }
            col += cs;
          });
        });
        return out.map(row => Array.from(row, v => v === undefined ? '' : v));
      };
      const tables = doc => Array.from(doc.querySelectorAll('table')).slice(0, LIMIT_TABLES).map(t => {
        const tr = t.parentElement ? t.parentElement.closest('tr') : null;
        const prev = tr ? tr.previousElementSibling : null;
        return { id: (t.id || '').slice(0, 60), rows: grid(t), context: prev ? Array.from(prev.cells).map(cellText).slice(0, 20) : [] };
      });
      const csrf = () => { const e = document.querySelector('input[name=_csrf]') || document.querySelector('meta[name=_csrf]'); return e ? (e.value || e.content) : ''; };
      const uid = () => { const e = document.getElementById('authorizedIDX') || document.querySelector('input[name=authorizedID]'); return e ? e.value : ''; };
      const post = async (path, params) => {
        const body = new URLSearchParams(params);
        const res = await fetch('/vtop/' + path, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest' }, body });
        const text = await res.text();
        return { status: res.status, raw: text, doc: new DOMParser().parseFromString(text, 'text/html'), login: /type=["']?password/i.test(text) && !/authorizedID/i.test(text) };
      };
      const postMultipart = async (path, fields) => {
        const fd = new FormData(); Object.keys(fields).forEach(k => fd.append(k, fields[k]));
        const res = await fetch('/vtop/' + path, { method: 'POST', credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' }, body: fd });
        const text = await res.text();
        return { status: res.status, raw: text, doc: new DOMParser().parseFromString(text, 'text/html'), login: /type=["']?password/i.test(text) && !/authorizedID/i.test(text) };
      };
      // Structure only: ids/names/paths matching safe patterns, counts and lengths. No values or text.
      const safe = s => /^[A-Za-z0-9_\/.-]{1,80}$/.test(s || '');
      const shape = (r, sem) => ({
        status: r.status, len: r.raw.length, tables: r.doc.querySelectorAll('table').length,
        selects: Array.from(r.doc.querySelectorAll('select')).filter(s => safe(s.id || s.name)).map(s => ({ id: s.id || s.name, n: s.options.length, hasSem: !!sem && Array.from(s.options).some(o => o.value === sem) })).slice(0, 10),
        inputs: Array.from(r.doc.querySelectorAll('input')).map(i => i.name || i.id).filter(safe).slice(0, 20),
        forms: Array.from(r.doc.querySelectorAll('form')).map(f => f.getAttribute('action') || '').filter(safe).slice(0, 10),
        ids: Array.from(r.doc.querySelectorAll('[id]')).map(e => e.id).filter(safe).slice(0, 40),
        // Code identifiers used near each marks endpoint in inline script (parameter names), not page text.
        near: Array.from(new Set(Array.from(r.raw.matchAll(/["']([A-Za-z]+\/[A-Za-z\/]+|[a-z][A-Za-z]*(?:View|Calendar|Schedule|Exam|Mark)[A-Za-z]*)["']/g)).flatMap(m =>
          (r.raw.slice(Math.max(0, m.index - 400), m.index + 400).match(/[A-Za-z_][A-Za-z0-9_]{2,30}(?=\s*[:=(])/g) || [])))).slice(0, 120),
        options: Array.from(r.doc.querySelectorAll('select')).flatMap(s => Array.from(s.options).slice(0, 3).map(o => o.value)).filter(v => safe(v) && v.length <= 20).slice(0, 12),
        endpoints: Array.from(new Set(Array.from(r.raw.matchAll(/["']([A-Za-z]+\/[A-Za-z\/]+|[a-z][A-Za-z]*(?:View|Calendar|Schedule|Exam|Mark)[A-Za-z]*)["']/g)).map(m => m[1]))).filter(safe).slice(0, 30),
        paths: Array.from(new Set((r.raw.match(/["'][A-Za-z\/]*[Mm]ark[A-Za-z\/]*["']/g) || []).map(x => x.slice(1, -1)))).filter(safe).slice(0, 15),
      });
    """

    /** [full] = also fetch the heavy, rarely-changing datasets (academic calendar, grade history, semester results). */
    fun sync(semesterId: String?, full: Boolean = true): String = """
    (() => {
      window.__phSync = { phase: 'running' };
      $COMMON
      (async () => {
        const result = { phase: 'done', datasets: {} };
        try {
          const token = csrf(), id = uid();
          if (!token || !id) { window.__phSync = { phase: 'error', reason: 'not_signed_in' }; return; }
          const base = { _csrf: token, authorizedID: id };
          const sem = await post('academics/common/StudentTimeTableChn', Object.assign({ verifyMenu: 'true', nocache: String(Date.now()) }, base));
          if (sem.login) { window.__phSync = { phase: 'error', reason: 'session_expired' }; return; }
          const select = sem.doc.getElementById('semesterSubId') || sem.doc.querySelector('select[name=semesterSubId]');
          result.semesters = select ? Array.from(select.options).slice(0, 80).map(o => [o.value, clean(o.textContent)]) : [];
          result.semesterStatus = sem.status;
          // Default: the semester in session by date (VIT: Fall Jul-Nov, Winter Dec-Apr, Summer May-Jun), else the first offered.
          const now = new Date(), m = now.getMonth() + 1, y = now.getFullYear();
          const season = m >= 7 && m <= 11 ? 'fall' : (m === 12 || m <= 4 ? 'winter' : 'summer');
          const start = m >= 7 ? y : y - 1, ay = start + '-' + String((start + 1) % 100).padStart(2, '0');
          const inSession = result.semesters.find(o => o[0] && o[1].toLowerCase().includes(season) && o[1].includes(ay));
          const chosen = ${if (semesterId == null) "null" else JSONObject.quote(semesterId)} || (inSession || result.semesters.find(o => o[0]) || [])[0];
          if (!chosen) { window.__phSync = { phase: 'error', reason: 'no_semester', status: sem.status }; return; }
          result.semester = chosen;
          const params = Object.assign({ semesterSubId: chosen, x: new Date().toUTCString() }, base);
          const menuOpen = path => post(path, Object.assign({ verifyMenu: 'true', nocache: String(Date.now()) }, base));
          const semFrom = r => {
            const s = r.doc.getElementById('semesterSubId') || r.doc.querySelector('select[name=semesterSubId]');
            const vals = s ? Array.from(s.options).map(o => o.value).filter(v => v) : [];
            return vals.includes(chosen) ? chosen : (vals[0] || chosen);
          };
          const store = (name, r) => { result.datasets[name] = r.login ? { status: r.status, error: 'session_expired' } : { status: r.status, tables: tables(r.doc) }; };
          const step = async (name, fn) => { window.__phSync = { phase: 'running', step: name }; try { await fn(); } catch (e) { result.datasets[name] = { error: 'request_failed' }; } };
          result.shapes = {};
          await step('timetable', async () => store('timetable', await post('processViewTimeTable', params)));
          await step('attendance', async () => store('attendance', await post('processViewStudentAttendance', params)));
          // Marks page has its own semester list and submits multipart FormData (verified on the owner's portal).
          await step('marks', async () => {
            const menu = await menuOpen('examinations/StudentMarkView');
            const s = semFrom(menu);
            const r = await postMultipart('examinations/doStudentMarkView', { authorizedID: id, semesterSubId: s, _csrf: token });
            store('marks', r); result.shapes.marks = { sameSemester: s === chosen, tables: r.doc.querySelectorAll('table').length };
          });
          await step('exams', async () => {
            const r = await postMultipart('examinations/doSearchExamScheduleForStudent', { authorizedID: id, semesterSubId: chosen, _csrf: token });
            store('exams', r); result.shapes.exams = { status: r.status, tables: r.doc.querySelectorAll('table').length };
          });
          // Academic calendar: class group from the page (e.g. COMB), months from getDateForSemesterPreview, one grid per month.
          const full = ${if (full) "true" else "false"};
          result.skipped = full ? [] : ['calendar', 'grades', 'results'];
          if (full) await step('calendar', async () => {
            const menu = await menuOpen('academics/common/CalendarPreview');
            const cg = Array.from((menu.doc.getElementById('classGroupId') || { options: [] }).options).map(o => o.value).filter(v => v);
            const group = cg[0] || 'ALL';
            const d = await post('getDateForSemesterPreview', { semSubId: chosen, classGroupId: group, authorizedID: id, _csrf: token, x: new Date().toUTCString() });
            const months = Array.from(new Set(d.raw.match(/\d{2}-[A-Z]{3}-\d{4}/g) || [])).slice(0, 8);
            const all = []; const statuses = [];
            for (const m of months) {
              const c = await post('processViewCalendar', { _csrf: token, calDate: m, semSubId: chosen, classGroupId: group, authorizedID: id, x: new Date().toUTCString() });
              statuses.push(c.status);
              if (c.status === 200) tables(c.doc).forEach(tb => { tb.context = [m]; all.push(tb); });
            }
            result.datasets.calendar = { status: months.length && statuses.every(x => x === 200) ? 200 : (statuses.find(x => x !== 200) || 204), tables: all };
            result.shapes.calendar = { months: months.length, statuses, tables: all.length };
          });
          if (full) await step('grades', async () => store('grades', await menuOpen('examinations/examGradeView/StudentGradeHistory')));
          // Semester results: Grade View per semester with published grades, then each course's detail (marks + ranges).
          if (full) await step('results', async () => {
            const menu = await menuOpen('examinations/examGradeView/StudentGradeView');
            const sel = menu.doc.getElementById('semesterSubId');
            const opts = sel ? Array.from(sel.options).map(o => o.value) : [];
            const sems = result.semesters.filter(o => o[0] && opts.includes(o[0])).slice(0, 10);
            const out = [];
            for (const [sm, name] of sems) {
              window.__phSync = { phase: 'running', step: 'results ' + (out.length + 1) };
              const r = await postMultipart('examinations/examGradeView/doStudentGradeView', { authorizedID: id, semesterSubId: sm, _csrf: token });
              const tbs = tables(r.doc);
              if (r.status !== 200 || !tbs.length) continue;
              const buttons = Array.from(r.doc.querySelectorAll('[onclick*=getGradeViewDetails]')).slice(0, 25).map(b => {
                const cid = ((b.getAttribute('onclick') || '').match(/getGradeViewDetails\(\s*['"]([^'"]+)['"]/) || [])[1];
                const tr = b.closest('tr');
                const code = tr ? ((tr.textContent || '').match(/[A-Z]{3,5}\d{3,4}[A-Z]?/) || [])[0] : null;
                return { cid, code };
              }).filter(x => x.cid && x.code);
              const details = [];
              for (const b of buttons) {
                const fields = { authorizedID: id, semesterSubId: sm, courseId: b.cid, _csrf: token, x: new Date().toUTCString() };
                let d = await post('examinations/examGradeView/getGradeViewDetails', fields);
                if (d.status !== 200 || !d.doc.querySelectorAll('table').length) d = await postMultipart('examinations/examGradeView/getGradeViewDetails', fields);
                details.push({ code: b.code, status: d.status, tables: d.status === 200 ? tables(d.doc) : [] });
              }
              // The portal prints the semester GPA on this page (often outside any table): keep only that number.
              const gm = ((r.doc.body && r.doc.body.textContent) || '').replace(/\s+/g, ' ').match(/(?:^|[^C])GPA\s*[:\-]?\s*(\d{1,2}\.\d{1,3})/i);
              out.push({ semester: sm, name, tables: tbs, details, gpa: gm ? gm[1] : null });
            }
            result.datasets.results = { status: 200, semesters: out };
            const d0 = (out[0] && out[0].details[0]) || null;
            result.shapes.results = { semesters: out.length, courses: out.map(x => x.details.length),
              detailStatus: d0 && d0.status, detailTables: d0 && d0.tables.map(tb => tb.rows.length + 'x' + Math.max(0, ...tb.rows.map(rw => rw.length))),
              detailHeads: d0 && d0.tables.map(tb => (tb.rows[0] || []).map(c => String(c).replace(/[0-9]/g, '9').slice(0, 20))) };
          });
          window.__phSync = result;
        } catch (e) { window.__phSync = { phase: 'error', reason: 'script_failed' }; }
      })();
      return 'started';
    })()"""

    /** Decodes an evaluateJavascript string result (a JSON-encoded string) to its payload. */
    fun unquote(encoded: String?): String? = runCatching { JSONTokener(encoded ?: return null).nextValue() as? String }.getOrNull()

    fun tables(array: JSONArray?): List<PortalTable> = (0 until (array?.length() ?: 0)).map { i ->
        val t = array!!.getJSONObject(i)
        val rows = t.optJSONArray("rows") ?: JSONArray()
        val ctx = t.optJSONArray("context") ?: JSONArray()
        PortalTable(t.optString("id"), (0 until rows.length()).map { r -> val row = rows.getJSONArray(r); (0 until row.length()).map { PortalCell(row.optString(it)) } },
            (0 until ctx.length()).map { ctx.optString(it) })
    }
}
