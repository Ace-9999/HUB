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
        return { status: res.status, doc: new DOMParser().parseFromString(text, 'text/html'), login: /type=["']?password/i.test(text) && !/authorizedID/i.test(text) };
      };
    """

    fun sync(semesterId: String?): String = """
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
          const chosen = ${if (semesterId == null) "null" else JSONObject.quote(semesterId)} || (result.semesters.find(o => o[0]) || [])[0];
          if (!chosen) { window.__phSync = { phase: 'error', reason: 'no_semester', status: sem.status }; return; }
          result.semester = chosen;
          const params = Object.assign({ semesterSubId: chosen, x: new Date().toUTCString() }, base);
          const steps = [
            ['timetable', 'processViewTimeTable', params],
            ['attendance', 'processViewStudentAttendance', params],
            ['marks', 'examinations/doStudentMarkView', params],
            ['grades', 'examinations/examGradeView/StudentGradeHistory', Object.assign({ verifyMenu: 'true', nocache: String(Date.now()) }, base)],
          ];
          for (const [name, path, p] of steps) {
            window.__phSync = { phase: 'running', step: name };
            try {
              const r = await post(path, p);
              result.datasets[name] = r.login ? { status: r.status, error: 'session_expired' } : { status: r.status, tables: tables(r.doc) };
            } catch (e) { result.datasets[name] = { error: 'request_failed' }; }
          }
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
