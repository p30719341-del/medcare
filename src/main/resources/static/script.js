// MedCare · comportamiento de la interfaz (JavaScript puro, sin librerías)
(function () {
  "use strict";
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));

  /* ---------- Fecha en el encabezado ---------- */
  const today = $("#today");
  if (today) today.textContent = new Date().toLocaleDateString("es-PE", { weekday: "long", day: "numeric", month: "long", year: "numeric" });

  /* ---------- Aviso (toast) enviado por el servidor ---------- */
  const toast = $("#toast");
  if (toast) {
    const hide = () => toast.classList.remove("show");
    setTimeout(hide, 4500);
    const close = $("#toastClose");
    if (close) close.addEventListener("click", hide);
  }

  /* ---------- Menú lateral en pantallas pequeñas ---------- */
  const menuBtn = $("#menuBtn");
  const sidebar = $("#sidebar");
  if (menuBtn && sidebar) {
    const scrim = document.createElement("div");
    scrim.className = "scrim";
    document.body.appendChild(scrim);
    const setMenu = (open) => {
      document.body.classList.toggle("menu-open", open);
      menuBtn.setAttribute("aria-expanded", String(open));
      menuBtn.setAttribute("aria-label", open ? "Cerrar menú" : "Abrir menú");
    };
    menuBtn.addEventListener("click", () => setMenu(!document.body.classList.contains("menu-open")));
    scrim.addEventListener("click", () => setMenu(false));
    document.addEventListener("keydown", (e) => { if (e.key === "Escape") setMenu(false); });
    $$("a", sidebar).forEach((a) => a.addEventListener("click", () => setMenu(false)));
  }

  /* ---------- Utilidades de fecha ---------- */
  const parseDay = (iso) => {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || "");
    return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null;
  };
  const startOfToday = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; };
  const daysFromToday = (d) => Math.round((d - startOfToday()) / 86400000);
  const plural = (n, one, many) => n + " " + (n === 1 ? one : many);

  /* ---------- Panel admin: tabla de pacientes ---------- */
  const table = $("#tabla");
  if (table) {
    const tbody = $("tbody", table);
    const rows = () => $$("tr[data-name]", tbody);

    // Iniciales y "hace X días"
    rows().forEach((tr) => {
      const av = $(".av", tr);
      if (av) {
        const parts = (tr.dataset.name || "").trim().split(/\s+/).filter(Boolean);
        av.textContent = ((parts[0] || "")[0] || "") + ((parts[1] || "")[0] || "");
        av.textContent = av.textContent.toUpperCase();
      }
      const rel = $(".rel", tr);
      const d = rel && parseDay(rel.dataset.date);
      if (rel && d) {
        const n = -daysFromToday(d);
        rel.textContent = n === 0 ? "Hoy" : n === 1 ? "Ayer" : n > 1 ? "Hace " + plural(n, "día", "días") : "En " + plural(-n, "día", "días");
      }
    });

    // Búsqueda inmediata mientras escribes (el botón Buscar sigue funcionando en el servidor)
    const q = $("#q");
    const count = $("#count");
    const total = count ? count.dataset.total : null;
    const emptyRow = () => $(".empty-row", tbody);
    const normalize = (s) => (s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
    const filter = () => {
      const term = normalize(q.value.trim());
      let shown = 0;
      rows().forEach((tr) => {
        const ok = !term || normalize(tr.textContent).includes(term);
        tr.hidden = !ok;
        if (ok) shown++;
      });
      let er = emptyRow();
      if (!shown && !er) {
        er = document.createElement("tr");
        er.className = "empty-row js";
        er.innerHTML = '<td colspan="6">No se encontraron pacientes.</td>';
        tbody.appendChild(er);
      } else if (shown && er && er.classList.contains("js")) {
        er.remove();
      }
      if (count && total !== null) count.textContent = "Mostrando " + shown + " de " + total + " pacientes · Datos de demostración";
    };
    if (q) {
      q.addEventListener("input", filter);
      if (q.value) filter();
    }

    // Ordenar al hacer clic en el encabezado
    const heads = $$("th", table);
    heads.forEach((th, i) => {
      if (!th.dataset.sort) return;
      th.tabIndex = 0;
      th.setAttribute("role", "columnheader");
      const sort = () => {
        const dir = th.getAttribute("aria-sort") === "ascending" ? "descending" : "ascending";
        heads.forEach((h) => h.removeAttribute("aria-sort"));
        th.setAttribute("aria-sort", dir);
        const key = (tr) => {
          const td = tr.children[i];
          return td.dataset.v !== undefined ? td.dataset.v : td.textContent.trim();
        };
        const list = rows().sort((a, b) => {
          const r = th.dataset.sort === "date" ? key(a).localeCompare(key(b)) : key(a).localeCompare(key(b), "es", { numeric: true, sensitivity: "base" });
          return dir === "ascending" ? r : -r;
        });
        const er = emptyRow();
        list.forEach((tr) => tbody.insertBefore(tr, er || null));
      };
      th.addEventListener("click", sort);
      th.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); sort(); } });
    });
  }

  /* ---------- Confirmar eliminación con un diálogo ---------- */
  const dlg = $("#confirmDialog");
  if (dlg && typeof dlg.showModal === "function") {
    let pending = null;
    $$(".del-form").forEach((f) => f.addEventListener("submit", (e) => {
      e.preventDefault();
      pending = f;
      $("#confirmName").textContent = f.dataset.name || "este paciente";
      dlg.showModal();
      $("#confirmNo").focus();
    }));
    $("#confirmNo").addEventListener("click", () => dlg.close());
    $("#confirmYes").addEventListener("click", () => { dlg.close(); if (pending) pending.submit(); });
    dlg.addEventListener("click", (e) => { if (e.target === dlg) dlg.close(); });
  } else {
    // Navegador sin <dialog>: confirmación básica
    $$(".del-form").forEach((f) => f.addEventListener("submit", (e) => {
      if (!window.confirm("¿Eliminar a este paciente y su acceso?")) e.preventDefault();
    }));
  }

  /* ---------- Agenda del día: marca las citas pasadas y la siguiente ---------- */
  const appts = $$(".appt[data-time]");
  if (appts.length) {
    const now = new Date();
    const nowMin = now.getHours() * 60 + now.getMinutes();
    let nextDone = false;
    appts.forEach((el) => {
      const [h, m] = el.dataset.time.split(":").map(Number);
      if (h * 60 + m < nowMin) { el.classList.add("done"); return; }
      if (!nextDone) {
        nextDone = true;
        el.classList.add("is-next");
        const strong = $("strong", el);
        if (strong) strong.insertAdjacentHTML("beforeend", '<span class="next-tag">SIGUIENTE</span>');
      }
    });
  }

  /* ---------- Portal del paciente: cuenta regresiva ---------- */
  const cd = $("#countdown");
  if (cd) {
    const d = parseDay(cd.dataset.date);
    if (d) {
      const [h, m] = (cd.dataset.time || "00:00").split(":").map(Number);
      const when = new Date(d.getFullYear(), d.getMonth(), d.getDate(), h, m);
      const diffDays = daysFromToday(d);
      let text;
      if (diffDays === 0) {
        const mins = Math.round((when - new Date()) / 60000);
        text = mins > 60 ? "Hoy, en " + plural(Math.floor(mins / 60), "hora", "horas") : mins > 0 ? "Hoy, en " + plural(mins, "minuto", "minutos") : "Es hoy";
      } else if (diffDays === 1) text = "Mañana";
      else text = "En " + plural(diffDays, "día", "días");
      cd.textContent = text;
    } else cd.hidden = true;
  }

  /* ---------- Formulario de paciente: solo números en DNI y teléfono ---------- */
  const dni = $("#dni");
  if (dni) dni.addEventListener("input", () => { dni.value = dni.value.replace(/\D/g, "").slice(0, 8); });
  const phone = $("#phone");
  if (phone) phone.addEventListener("input", () => { phone.value = phone.value.replace(/[^\d\s+()-]/g, ""); });

  /* ---------- Login: mostrar contraseña y perfiles de demostración ---------- */
  const pw = $("#password");
  const pwToggle = $("#pwToggle");
  if (pw && pwToggle) {
    pwToggle.addEventListener("click", () => {
      const show = pw.type === "password";
      pw.type = show ? "text" : "password";
      pwToggle.textContent = show ? "Ocultar" : "Mostrar";
      pwToggle.setAttribute("aria-pressed", String(show));
    });
  }
  $$(".demo [data-user]").forEach((b) => b.addEventListener("click", () => {
    const u = $("#username");
    if (u && pw) { u.value = b.dataset.user; pw.value = b.dataset.pass; pw.focus(); }
  }));

  /* ---------- Confirmación sencilla para formularios con data-confirm (cancelar cita, etc.) ---------- */
  $$("form[data-confirm]").forEach((f) => f.addEventListener("submit", (e) => {
    if (!window.confirm(f.dataset.confirm)) e.preventDefault();
  }));

  /* ---------- Reprogramar cita: horarios libres del doctor según la fecha elegida ---------- */
  const rp = $("#rpForm");
  if (rp) {
    const fecha = $("#rpFecha"), box = $("#rpSlots"), hora = $("#rpHora"), btn = $("#rpBtn");
    const msg = (t) => { box.innerHTML = ""; const p = document.createElement("p"); p.className = "slots-msg"; p.textContent = t; box.appendChild(p); };
    const load = () => {
      hora.value = ""; btn.disabled = true;
      if (!fecha.value) { msg("Elige una fecha para ver los horarios libres."); return; }
      msg("Buscando horarios…");
      fetch(rp.dataset.url + "?doctor=" + encodeURIComponent(rp.dataset.doctor) + "&fecha=" + encodeURIComponent(fecha.value), { headers: { Accept: "application/json" } })
        .then((r) => { if (!r.ok) throw new Error(); return r.json(); })
        .then((list) => {
          if (!list.length) { msg("Ese día no hay horarios libres. Prueba con otra fecha."); return; }
          box.innerHTML = "";
          list.forEach((h) => {
            const b = document.createElement("button");
            b.type = "button"; b.textContent = h; b.setAttribute("aria-pressed", "false");
            b.addEventListener("click", () => {
              $$("button", box).forEach((x) => x.setAttribute("aria-pressed", "false"));
              b.setAttribute("aria-pressed", "true"); hora.value = h; btn.disabled = false;
            });
            box.appendChild(b);
          });
        })
        .catch(() => msg("No pudimos cargar los horarios. Intenta de nuevo."));
    };
    fecha.addEventListener("change", load);
    rp.addEventListener("submit", (e) => { if (!hora.value) e.preventDefault(); });
  }

  /* ---------- Atención: plantillas de receta y CIE-10 ---------- */
  const plSel = $("#plantillaSel");
  if (plSel) plSel.addEventListener("change", () => {
    const o = plSel.options[plSel.selectedIndex];
    if (!o || !o.value) return;
    const dx = $("#dx"), rx = $("#rx"), cie = $("#cie");
    if (dx) dx.value = o.getAttribute("data-dx") || "";
    if (rx) rx.value = o.getAttribute("data-rx") || "";
    if (cie) cie.value = o.getAttribute("data-cie") || "";
  });
  const chat = $(".chatlist");
  if (chat) chat.scrollTop = chat.scrollHeight;

/* PWA: registra el service worker (solo en https o localhost) */
if ("serviceWorker" in navigator && (location.protocol === "https:" || location.hostname === "localhost")) {
  window.addEventListener("load", () => { navigator.serviceWorker.register("/sw.js").catch(() => {}); });
}
})();
