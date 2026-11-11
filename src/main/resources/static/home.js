// MedCare · portada pública (JavaScript puro)
(function () {
  "use strict";
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

  /* ---------- Menú en pantallas pequeñas ---------- */
  var burger = $("#burger"), nav = $("#nav");
  if (burger && nav) {
    var setNav = function (open) {
      nav.classList.toggle("open", open);
      burger.setAttribute("aria-expanded", String(open));
      burger.setAttribute("aria-label", open ? "Cerrar menú" : "Abrir menú");
    };
    burger.addEventListener("click", function () { setNav(!nav.classList.contains("open")); });
    $$("a", nav).forEach(function (a) { a.addEventListener("click", function () { setNav(false); }); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") setNav(false); });
  }

  /* ---------- Filtro de doctores por especialidad ---------- */
  var chips = $$(".fchip");
  chips.forEach(function (c) {
    c.addEventListener("click", function () {
      chips.forEach(function (x) { x.setAttribute("aria-pressed", String(x === c)); });
      aplicarFiltro();
    });
  });

  /* ---------- Buscador de doctores (nombre, especialidad o síntoma) ---------- */
  var buscar = $("#docBuscar");
  var SINTOMAS = {
    "Medicina general": ["fiebre", "gripe", "resfrio", "resfriado", "tos", "dolor de cabeza", "cabeza", "cansancio", "malestar", "garganta", "chequeo", "estomago", "diarrea", "fever", "cough", "headache", "check-up", "checkup", "flu"],
    "Cardiología": ["corazon", "presion", "hipertension", "palpitaciones", "pecho", "colesterol", "heart", "blood pressure", "chest"],
    "Odontología": ["muela", "diente", "dientes", "caries", "encia", "limpieza dental", "boca", "tooth", "teeth", "dental", "gum"],
    "Pediatría": ["nino", "nina", "bebe", "vacuna", "vacunas", "crecimiento", "hijo", "hija", "child", "baby", "vaccine", "kid"],
    "Dermatología": ["piel", "acne", "mancha", "manchas", "ronchas", "alergia", "caspa", "cabello", "unas", "skin", "rash", "hair"],
    "Ginecología": ["embarazo", "regla", "menstruacion", "papanicolau", "mujer", "pregnancy", "period", "women"]
  };
  function sinTildes(s) { return (s || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, ""); }
  function aplicarFiltro() {
    var act = $(".fchip[aria-pressed='true']");
    var esp = act ? act.getAttribute("data-filter") : "";
    var q = buscar ? sinTildes(buscar.value.trim()) : "";
    var espPorSintoma = {};
    if (q) Object.keys(SINTOMAS).forEach(function (e) {
      if (SINTOMAS[e].some(function (w) { return q.indexOf(w) >= 0 || (q.length >= 3 && w.indexOf(q) === 0); })) espPorSintoma[e] = true;
    });
    var visibles = 0;
    $$(".doc").forEach(function (d) {
      var e = d.getAttribute("data-esp");
      var okEsp = !esp || e === esp;
      var okQ = !q || sinTildes(d.textContent).indexOf(q) >= 0 || !!espPorSintoma[e];
      d.hidden = !(okEsp && okQ);
      if (!d.hidden) visibles++;
    });
    var vacio = $("#docVacio"); if (vacio) vacio.hidden = visibles > 0;
  }
  if (buscar) buscar.addEventListener("input", aplicarFiltro);

  /* ---------- Formulario de reserva ---------- */
  var form = $("#rvForm");
  if (!form) return;
  var selEsp = $("#especialidad"), selDoc = $("#doctor"), inFecha = $("#fecha"), inHora = $("#hora");
  var slots = $("#slots");
  var allDocs = $$("option[data-esp]", selDoc).map(function (o) { return { value: o.value, esp: o.getAttribute("data-esp"), precio: o.getAttribute("data-precio") }; });
  var MES = ["ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "set.", "oct.", "nov.", "dic."];
  var DIA = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
  var token = 0;

  function parseDay(v) { var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v || ""); return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null; }
  function setMsg(text) {
    slots.innerHTML = "";
    var p = document.createElement("p"); p.className = "slots-msg"; p.textContent = text; slots.appendChild(p);
  }

  function updateSummary() {
    $("#sEsp").textContent = selEsp.value || "—";
    $("#sDoc").textContent = selDoc.value || "—";
    var d = parseDay(inFecha.value);
    $("#sFecha").textContent = d ? DIA[d.getDay()] + " " + d.getDate() + " " + MES[d.getMonth()] + " " + d.getFullYear() : "—";
    $("#sHora").textContent = inHora.value ? inHora.value + " h" : "—";
    var dm = allDocs.filter(function (x) { return x.value === selDoc.value; })[0];
    var pr = $("#sPrecio"); if (pr) pr.textContent = dm && dm.precio ? "S/ " + dm.precio : "—";
  }

  /* Lista de espera: cuando no hay horarios, ofrece avisar si se libera uno */
  function ofrecerEspera(texto) {
    setMsg(texto);
    var f = $("#esperaForm");
    if (!f) return;
    var b = document.createElement("button");
    b.type = "button"; b.className = "btn secondary small espera-btn"; b.textContent = "Avisarme si se libera un horario";
    b.addEventListener("click", function () {
      var v = { nombre: ($("#nombre") || {}).value || "", dni: ($("#dni") || {}).value || "", telefono: ($("#telefono") || {}).value || "", email: ($("#email") || {}).value || "" };
      var falta = !v.nombre.trim() ? "#nombre" : !/^\d{8}$/.test(v.dni) ? "#dni" : v.telefono.replace(/\D/g, "").length < 7 ? "#telefono" : "";
      if (falta) { setMsg("Completa tu nombre, DNI y teléfono (paso 2) y vuelve a pulsar el botón."); $(falta).focus(); return; }
      f.elements.doctor.value = selDoc.value; f.elements.fecha.value = inFecha.value;
      Object.keys(v).forEach(function (k) { f.elements[k].value = v[k]; });
      f.submit();
    });
    slots.appendChild(b);
  }

  // Muestra solo los doctores de la especialidad elegida
  function fillDoctors() {
    var keep = selDoc.value;
    selDoc.innerHTML = "";
    var first = document.createElement("option"); first.value = ""; first.textContent = "Elige un doctor"; selDoc.appendChild(first);
    allDocs.forEach(function (d) {
      if (selEsp.value && d.esp !== selEsp.value) return;
      var o = document.createElement("option"); o.value = d.value; o.textContent = d.value; o.setAttribute("data-esp", d.esp);
      selDoc.appendChild(o);
    });
    selDoc.value = keep;
    if (selDoc.value !== keep) selDoc.value = "";
  }

  function loadSlots(keepSelected) {
    var doc = selDoc.value, fecha = inFecha.value, d = parseDay(fecha);
    if (!keepSelected) inHora.value = "";
    updateSummary();
    if (!doc || !d) { setMsg("Elige un doctor y una fecha para ver los horarios libres."); return; }
    if (d.getDay() === 0) { setMsg("No atendemos los domingos. Elige otra fecha."); inHora.value = ""; updateSummary(); return; }
    var my = ++token;
    setMsg("Buscando horarios…");
    fetch(slots.getAttribute("data-url") + "?doctor=" + encodeURIComponent(doc) + "&fecha=" + encodeURIComponent(fecha), { headers: { Accept: "application/json" } })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function (list) {
        if (my !== token) return;
        if (!list.length) { ofrecerEspera("No hay horarios libres ese día. Prueba con otra fecha o pide que te avisemos si se libera uno."); inHora.value = ""; updateSummary(); return; }
        slots.innerHTML = "";
        list.forEach(function (h) {
          var b = document.createElement("button");
          b.type = "button"; b.className = "slot"; b.textContent = h;
          b.setAttribute("aria-pressed", String(inHora.value === h));
          b.addEventListener("click", function () {
            inHora.value = h;
            $$(".slot", slots).forEach(function (x) { x.setAttribute("aria-pressed", String(x === b)); });
            updateSummary();
          });
          slots.appendChild(b);
        });
        if (inHora.value && list.indexOf(inHora.value) < 0) inHora.value = "";
        updateSummary();
      })
      .catch(function () { if (my === token) setMsg("No pudimos cargar los horarios. Intenta de nuevo."); });
  }

  selEsp.addEventListener("change", function () { fillDoctors(); loadSlots(false); });
  selDoc.addEventListener("change", function () {
    var m = allDocs.filter(function (d) { return d.value === selDoc.value; })[0];
    if (m && selEsp.value !== m.esp) { selEsp.value = m.esp; fillDoctors(); selDoc.value = m.value; }
    loadSlots(false);
  });
  inFecha.addEventListener("change", function () { loadSlots(false); });

  // Botones "Reservar" de especialidades y doctores
  function goReserve(esp, doc) {
    if (esp) { selEsp.value = esp; fillDoctors(); }
    if (doc) selDoc.value = doc;
    loadSlots(false);
    $("#reservar").scrollIntoView();
    (doc ? inFecha : selDoc).focus({ preventScroll: true });
  }
  $$(".esp .more").forEach(function (b) { b.addEventListener("click", function () { goReserve(b.getAttribute("data-esp"), ""); }); });
  $$(".doc [data-doctor]").forEach(function (b) { b.addEventListener("click", function () { goReserve(b.getAttribute("data-esp"), b.getAttribute("data-doctor")); }); });

  // Solo números en DNI y teléfono
  var dni = $("#dni"), tel = $("#telefono");
  if (dni) dni.addEventListener("input", function () { dni.value = dni.value.replace(/\D/g, "").slice(0, 8); });
  if (tel) tel.addEventListener("input", function () { tel.value = tel.value.replace(/[^\d\s+()-]/g, ""); });

  /* ---------- Estado inicial (también al volver con errores del servidor) ---------- */
  var sel = slots.getAttribute("data-sel") || inHora.value;
  if (sel) inHora.value = sel;
  fillDoctors();
  loadSlots(true);
  if ($(".err")) { var first = $(".invalid"); if (first) first.scrollIntoView({ block: "center" }); }

/* PWA: registra el service worker (solo en https o localhost) */
if ("serviceWorker" in navigator && (location.protocol === "https:" || location.hostname === "localhost")) {
  window.addEventListener("load", () => { navigator.serviceWorker.register("/sw.js").catch(() => {}); });
}
})();
