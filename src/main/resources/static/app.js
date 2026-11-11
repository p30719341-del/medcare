// MedCare · Laboratorio 12: modal accesible + AJAX (fetch) + imágenes con vista previa
(function () {
  "use strict";
  var $ = function (s) { return document.querySelector(s); };
  var modal = $("#solModal"), form = $("#solForm"), lista = $("#solLista"), estado = $("#solEstado");
  if (!modal || !form || !lista) return;

  var API = "/api/solicitudes";
  var MAX_BYTES = 2 * 1024 * 1024;                  // 2 MB
  var TIPOS = ["image/jpeg", "image/png"];          // validación por tipo MIME y por extensión
  var EXT = /\.(jpe?g|png)$/i;
  var TIMEOUT_MS = 15000;
  var abrirBtn = $("#solAbrir"), enviar = $("#solEnviar");
  var foto = $("#solFoto"), drop = $("#solDrop"), prev = $("#solPrev"), prevImg = $("#solPrevImg");
  var archivo = null, urlPrev = null, ultimoFoco = null, toastT = null;

  /* ---------- utilidades ---------- */
  function el(tag, cls, txt) { var e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; }
  function csrf() {
    var t = document.querySelector('meta[name="_csrf"]'), h = document.querySelector('meta[name="_csrf_header"]');
    return t && h && t.content ? { header: h.content, token: t.content } : null;
  }
  function toast(msg, ko) {
    var t = $("#solToast"); t.textContent = msg; t.className = "toast" + (ko ? " ko" : ""); t.hidden = false;
    clearTimeout(toastT); toastT = setTimeout(function () { t.hidden = true; }, 4200);
  }
  function tam(b) { return b >= 1048576 ? (b / 1048576).toFixed(1) + " MB" : Math.max(1, Math.round(b / 1024)) + " KB"; }
  // fetch con tiempo máximo: si el servidor no responde, se informa en vez de quedar cargando para siempre
  function conTimeout(url, opts) {
    var ctl = new AbortController(), t = setTimeout(function () { ctl.abort(); }, TIMEOUT_MS);
    opts = opts || {}; opts.signal = ctl.signal;
    return fetch(url, opts).finally(function () { clearTimeout(t); });
  }
  // Traduce cualquier fallo a un mensaje claro para la persona
  function mensajeError(e) {
    if (e && e.name === "AbortError") return "El servidor tardó demasiado en responder. Inténtalo de nuevo.";
    if (e && e.http) {
      if (e.http === 413) return "El archivo es demasiado grande para el servidor.";
      if (e.http === 403) return "Tu sesión de seguridad venció. Recarga la página e inténtalo otra vez.";
      if (e.http >= 500) return "El servidor tuvo un problema (error " + e.http + "). Inténtalo en unos minutos.";
      return "No se pudo completar la operación (error " + e.http + ").";
    }
    return "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.";
  }

  /* ---------- AJAX GET: listar ---------- */
  function mostrarCargando() {
    estado.hidden = true; lista.setAttribute("aria-busy", "true"); lista.textContent = "";
    for (var i = 0; i < 3; i++) lista.appendChild(el("div", "sol-skel"));
  }
  function mostrarEstado(titulo, detalle, esError, reintentar) {
    estado.className = "sol-estado" + (esError ? " error" : ""); estado.textContent = "";
    estado.appendChild(el("b", null, titulo)); estado.appendChild(el("span", null, detalle));
    if (reintentar) { var b = el("button", "btn secondary small", "Reintentar"); b.type = "button"; b.addEventListener("click", cargar); estado.appendChild(b); }
    estado.hidden = false;
  }
  function tarjeta(s, nueva) {
    var c = el("article", "sol-card" + (nueva ? " nueva" : ""));
    if (s.imagenUrl) {
      var b = el("button", "sol-thumb"); b.type = "button"; b.setAttribute("aria-label", "Ampliar la imagen de la solicitud de " + s.solicitante);
      var im = el("img"); im.src = s.imagenUrl; im.alt = "Evidencia enviada por " + s.solicitante; im.loading = "lazy";
      im.style.cssText = "width:100%;height:100%;object-fit:cover;display:block";
      b.appendChild(im); b.addEventListener("click", function () { ampliar(s.imagenUrl, im.alt); }); c.appendChild(b);
    } else {
      var n = el("div", "sol-nothumb"); n.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 3h6l1 2h3v16H5V5h3zM9 12h6M12 9v6"/></svg>'; c.appendChild(n);
    }
    var body = el("div", "sol-body"), top = el("div", "sol-top");
    top.appendChild(el("span", "sol-tag", s.servicio)); top.appendChild(el("span", "sol-est", s.estado));
    var meta = el("div", "sol-meta"), quien = el("span"); quien.appendChild(el("b", null, s.solicitante)); meta.appendChild(quien); meta.appendChild(el("span", null, s.fecha));
    body.appendChild(top); body.appendChild(el("p", null, s.descripcion)); body.appendChild(meta);
    c.appendChild(body); return c;
  }
  function pintar(datos, idNueva) {
    lista.textContent = ""; lista.setAttribute("aria-busy", "false");
    var cnt = $("#solCount"); cnt.hidden = !datos.length; cnt.textContent = datos.length;
    if (!datos.length) { mostrarEstado("Aún no hay solicitudes", "Sé la primera persona en enviar una con el botón «Nueva solicitud».", false); return; }
    estado.hidden = true;
    datos.forEach(function (s) { lista.appendChild(tarjeta(s, s.id === idNueva)); });
  }
  function cargar(idNueva) {
    mostrarCargando();
    return conTimeout(API, { headers: { Accept: "application/json" } })
      .then(function (r) { if (!r.ok) { var e = new Error("http"); e.http = r.status; throw e; } return r.json(); })
      .then(function (d) { pintar(d, typeof idNueva === "number" ? idNueva : null); })
      .catch(function (e) {
        lista.textContent = ""; lista.setAttribute("aria-busy", "false");
        mostrarEstado("No pudimos cargar las solicitudes", mensajeError(e), true, true);
      });
  }

  /* ---------- Modal ---------- */
  function limpiarErrores() {
    ["Nombre", "Correo", "Servicio", "Descripcion", "Foto"].forEach(function (k) { var m = $("#sol" + k + "-e"); if (m) m.textContent = ""; });
    Array.prototype.forEach.call(form.querySelectorAll("[aria-invalid]"), function (x) { x.removeAttribute("aria-invalid"); });
    $("#solError").hidden = true;
  }
  function marcar(id, msg) { var i = $("#" + id), m = $("#" + id + "-e"); if (i) i.setAttribute("aria-invalid", "true"); if (m) m.textContent = msg; }
  function abrir() { ultimoFoco = document.activeElement; resetear(); modal.showModal(); $("#solNombre").focus(); }
  function cerrar() { if (modal.open) modal.close(); }
  function resetear() { form.reset(); quitarFoto(); limpiarErrores(); contador(); setCarga(false); }
  abrirBtn.addEventListener("click", abrir);
  $("#solCerrar").addEventListener("click", cerrar);
  $("#solCancelar").addEventListener("click", cerrar);
  $("#solRefrescar").addEventListener("click", function () { cargar(); });
  modal.addEventListener("click", function (e) { if (e.target === modal) cerrar(); });          // clic en el fondo oscuro
  modal.addEventListener("close", function () { if (ultimoFoco && ultimoFoco.focus) ultimoFoco.focus(); });
  modal.addEventListener("cancel", function (e) { if (enviar.classList.contains("cargando")) e.preventDefault(); });

  function contador() { $("#solDescripcion-c").textContent = $("#solDescripcion").value.length + " / 500"; }
  $("#solDescripcion").addEventListener("input", contador);

  /* ---------- Imagen: selección, validación y vista previa ---------- */
  function quitarFoto() {
    archivo = null; foto.value = ""; prev.hidden = true;
    if (urlPrev) { URL.revokeObjectURL(urlPrev); urlPrev = null; } prevImg.removeAttribute("src");
    $("#solFoto-e").textContent = ""; foto.removeAttribute("aria-invalid");
  }
  function validarArchivo(f) {
    if (!f) return "";
    if (TIPOS.indexOf(f.type) < 0 || !EXT.test(f.name)) return "Formato no permitido. Usa una imagen JPG o PNG.";
    if (f.size > MAX_BYTES) return "La imagen pesa " + tam(f.size) + " y el máximo es 2 MB.";
    if (f.size === 0) return "El archivo está vacío.";
    return "";
  }
  function elegir(f) {
    quitarFoto(); if (!f) return;
    var m = validarArchivo(f);
    if (m) { $("#solFoto-e").textContent = m; foto.value = ""; return; }
    archivo = f; urlPrev = URL.createObjectURL(f);            // vista previa local: todavía NO se ha enviado nada
    prevImg.src = urlPrev; $("#solPrevNom").textContent = f.name; $("#solPrevTam").textContent = tam(f.size); prev.hidden = false;
  }
  foto.addEventListener("change", function () { elegir(foto.files[0]); });
  drop.addEventListener("click", function () { foto.click(); });
  drop.addEventListener("keydown", function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); foto.click(); } });
  ["dragenter", "dragover"].forEach(function (n) { drop.addEventListener(n, function (e) { e.preventDefault(); drop.classList.add("over"); }); });
  ["dragleave", "drop"].forEach(function (n) { drop.addEventListener(n, function (e) { e.preventDefault(); drop.classList.remove("over"); }); });
  drop.addEventListener("drop", function (e) { var f = e.dataTransfer && e.dataTransfer.files[0]; if (f) elegir(f); });
  $("#solQuitar").addEventListener("click", quitarFoto);

  /* ---------- Validación del formulario (cliente) ---------- */
  function validar() {
    limpiarErrores();
    var ok = true, primero = null;
    function falla(id, msg) { marcar(id, msg); ok = false; if (!primero) primero = $("#" + id); }
    var n = $("#solNombre").value.trim(), c = $("#solCorreo").value.trim(), s = $("#solServicio").value, d = $("#solDescripcion").value.trim();
    if (n.length < 3) falla("solNombre", "Escribe tu nombre completo (mínimo 3 letras).");
    if (!c) falla("solCorreo", "El correo es obligatorio."); else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(c)) falla("solCorreo", "Escribe un correo válido, por ejemplo nombre@correo.com.");
    if (!s) falla("solServicio", "Elige un tipo de servicio.");
    if (d.length < 10) falla("solDescripcion", "Describe tu solicitud con al menos 10 caracteres (llevas " + d.length + ").");
    var fm = validarArchivo(archivo); if (fm) { marcar("solFoto", fm); ok = false; if (!primero) primero = drop; }
    if (primero) primero.focus();
    return ok;
  }
  ["solNombre", "solCorreo", "solServicio", "solDescripcion"].forEach(function (id) {
    $("#" + id).addEventListener("input", function () { this.removeAttribute("aria-invalid"); var m = $("#" + id + "-e"); if (m) m.textContent = ""; });
  });

  /* ---------- AJAX POST con FormData ---------- */
  function setCarga(on) {
    enviar.classList.toggle("cargando", on); enviar.disabled = on;
    enviar.querySelector(".lbl-t").textContent = on ? "Enviando…" : "Enviar solicitud";
    $("#solCancelar").disabled = on;
  }
  form.addEventListener("submit", function (ev) {
    ev.preventDefault();
    if (!validar()) return;
    var fd = new FormData();
    fd.append("nombre", $("#solNombre").value.trim()); fd.append("correo", $("#solCorreo").value.trim());
    fd.append("servicio", $("#solServicio").value); fd.append("descripcion", $("#solDescripcion").value.trim());
    if (archivo) fd.append("evidencia", archivo, archivo.name);
    var headers = { Accept: "application/json" }, t = csrf(); if (t) headers[t.header] = t.token;   // NO se fija Content-Type: el navegador agrega el boundary
    $("#solError").hidden = true; setCarga(true);
    conTimeout(API, { method: "POST", body: fd, headers: headers })
      .then(function (r) {
        if (r.ok) return r.json();
        return r.json().catch(function () { return null; }).then(function (b) { var e = new Error("http"); e.http = r.status; e.cuerpo = b; throw e; });
      })
      .then(function (nueva) {
        setCarga(false); cerrar(); toast("¡Solicitud enviada! Ya aparece en la lista.");
        cargar(nueva.id).then(function () { var c = lista.querySelector(".nueva"); if (c) c.scrollIntoView({ behavior: "smooth", block: "center" }); });
      })
      .catch(function (e) {
        setCarga(false);
        if (e.http === 400 && e.cuerpo && e.cuerpo.errores) {           // errores de validación del servidor, por campo
          var map = { nombre: "solNombre", correo: "solCorreo", servicio: "solServicio", descripcion: "solDescripcion", evidencia: "solFoto" };
          Object.keys(e.cuerpo.errores).forEach(function (k) { if (map[k]) marcar(map[k], e.cuerpo.errores[k]); });
          return;
        }
        var b = $("#solError"); b.textContent = mensajeError(e); b.hidden = false;
      });
  });

  /* ---------- Visor de imagen ampliada ---------- */
  function ampliar(src, alt) {
    var v = $("#solVisor");
    if (!v) { v = el("dialog", "visor"); v.id = "solVisor"; v.setAttribute("aria-label", "Imagen ampliada"); v.appendChild(el("img")); document.body.appendChild(v); v.addEventListener("click", function () { v.close(); }); }
    var im = v.querySelector("img"); im.src = src; im.alt = alt; v.showModal();
  }

  cargar();
})();
