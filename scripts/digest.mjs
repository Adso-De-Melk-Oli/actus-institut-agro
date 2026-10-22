// Compose le digest du matin (nouveautés depuis le précédent, par école, + événements du jour
// sur les campus) et l'éventuelle alerte de panne. Écrit data/digest.json, que la tâche
// d'envoi de 7 h 00 lit tel quel.

const TZ = "Europe/Paris";
const SITE = "https://adso-de-melk-oli.github.io/actus-institut-agro/";
const SCHOOLS = [
  ["institut", "L'Institut Agro", "#2f8f46"],
  ["montpellier", "Institut Agro Montpellier", "#e2632b"],
  ["rennes", "Institut Agro Rennes-Angers", "#1f6fd1"],
  ["fondation", "Fondation de l'Institut Agro", "#0e8a8c"],
];
const LABEL = Object.fromEntries(SCHOOLS.map(([k, l]) => [k, l]));
const CAMPUS = /campus|gaillarde|lavalette|florac|institut agro|agrocampus|amphith|rennes|angers|dijon|montpellier|viala|saint[- ]brieuc/i;

/* ---------- dates à l'heure de Paris ---------- */
export const parisDay = d => new Date(d).toLocaleDateString("fr-CA", { timeZone: TZ });          // AAAA-MM-JJ
const parisHour = d => +new Date(d).toLocaleString("en-GB", { timeZone: TZ, hour: "2-digit", hour12: false });
const addDays = (day, n) => { const d = new Date(day + "T12:00:00Z"); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };
const weekday = day => new Date(day + "T12:00:00Z").getUTCDay(); // 0 = dimanche
const nextWorkday = day => { let d = day; while ([0, 6].includes(weekday(d))) d = addDays(d, 1); return d; };
const prevWorkday = day => { let d = addDays(day, -1); while ([0, 6].includes(weekday(d))) d = addDays(d, -1); return d; };
/** L'instant UTC correspondant à « jour à hh:00, heure de Paris ». */
export function parisInstant(day, hh) {
  const guess = new Date(`${day}T${String(hh).padStart(2, "0")}:00:00Z`);
  const asParis = new Date(guess.toLocaleString("en-US", { timeZone: TZ }));
  const asUtc = new Date(guess.toLocaleString("en-US", { timeZone: "UTC" }));
  return new Date(guess - (asParis - asUtc));
}
/** Le digest préparé maintenant est celui du prochain envoi de 7 h 00 (jours ouvrés). */
export function digestDayFor(now) {
  const today = parisDay(now);
  return nextWorkday(parisHour(now) < 7 ? today : addDays(today, 1));
}

const fmtDay = (day, opts = { weekday: "long", day: "numeric", month: "long" }) => new Date(day + "T12:00:00Z").toLocaleDateString("fr-FR", { ...opts, timeZone: "UTC" });
const esc = s => String(s ?? "").replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const eventDay = a => String(a.date ?? "").slice(0, 10);
const eventEnd = a => String(a.endDate ?? a.date ?? "").slice(0, 10);
function when(a) {
  const s = eventDay(a), e = eventEnd(a);
  return e && e !== s ? `du ${fmtDay(s, { day: "numeric", month: "long" })} au ${fmtDay(e, { day: "numeric", month: "long" })}` : fmtDay(s);
}

/* ---------- composition ---------- */

export function buildDigest(data, now = new Date()) {
  const day = digestDayFor(now);
  const from = parisInstant(prevWorkday(day), 7), to = parisInstant(day, 7);
  const arts = data.articles ?? [];
  const fresh = arts.filter(a => a.firstSeen && new Date(a.firstSeen) > from && new Date(a.firstSeen) <= to);
  const today = arts.filter(a => a.kind === "agenda" && eventDay(a) <= day && day <= eventEnd(a));
  const onCampus = today.filter(a => !a.place || CAMPUS.test(a.place));
  const weekAhead = arts.filter(a => a.kind === "agenda" && eventDay(a) > day && eventDay(a) <= addDays(day, 7))
    .sort((a, b) => eventDay(a).localeCompare(eventDay(b)));

  const nActu = fresh.filter(a => a.kind === "actu").length, nEv = fresh.filter(a => a.kind === "agenda").length;
  const parts = [];
  if (onCampus.length) parts.push(`${onCampus.length} événement${onCampus.length > 1 ? "s" : ""} aujourd'hui`);
  if (nActu) parts.push(`${nActu} actu${nActu > 1 ? "s" : ""}`);
  if (nEv) parts.push(`${nEv} date${nEv > 1 ? "s" : ""} d'agenda`);
  const subject = `Actus Institut Agro · ${fmtDay(day)} · ${parts.length ? parts.join(", ") : "rien de nouveau"}`;

  /* --- HTML (styles en ligne : les messageries ignorent les feuilles de style) --- */
  const F = "-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif";
  const pill = (src) => { const [, label, color] = SCHOOLS.find(s => s[0] === src) ?? [, src, "#6e6e73"]; return `<span style="display:inline-block;padding:2px 9px;border-radius:999px;background:${color}1f;color:${color};font-size:12px;font-weight:600">${esc(label)}</span>`; };
  const item = a => {
    const meta = a.kind === "agenda" ? `${esc(when(a))}${a.place ? " · " + esc(a.place) : ""}` : "";
    const thumb = a.image ? `<td width="76" valign="top" style="padding:0 12px 0 0"><img src="${esc(a.image)}" width="76" height="56" alt="" style="display:block;width:76px;height:56px;object-fit:cover;border-radius:8px"></td>` : "";
    return `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 14px"><tr>${thumb}<td valign="top">
      ${meta ? `<div style="font-size:12px;color:#b26a00;font-weight:600;margin:0 0 2px">${meta}</div>` : ""}
      <a href="${esc(a.url)}" style="font-size:15px;font-weight:600;color:#1d1d1f;text-decoration:none;line-height:1.3">${esc(a.title)}</a>
      ${a.summary ? `<div style="font-size:13px;color:#6e6e73;line-height:1.4;margin-top:3px">${esc(a.summary)}</div>` : ""}
    </td></tr></table>`;
  };
  const h2 = t => `<h2 style="font-size:13px;letter-spacing:.06em;text-transform:uppercase;color:#6e6e73;margin:26px 0 12px">${t}</h2>`;
  let body = "";
  if (onCampus.length) {
    body += `<div style="background:#FFF4D6;border-radius:14px;padding:16px 18px;margin:0 0 8px"><div style="font-size:13px;font-weight:700;color:#8a5a00;margin:0 0 10px">Aujourd'hui sur les campus</div>`
      + onCampus.map(a => `<div style="margin:0 0 10px">${pill(a.source)}<br><a href="${esc(a.url)}" style="font-size:15px;font-weight:600;color:#1d1d1f;text-decoration:none">${esc(a.title)}</a><div style="font-size:13px;color:#6e6e73">${esc(a.place || "Lieu non précisé")}${eventEnd(a) !== eventDay(a) ? " · " + esc(when(a)) : ""}</div></div>`).join("") + `</div>`;
  }
  for (const [src, label] of SCHOOLS) {
    const mine = fresh.filter(a => a.source === src);
    if (!mine.length) continue;
    const actus = mine.filter(a => a.kind === "actu"), evs = mine.filter(a => a.kind === "agenda").sort((a, b) => eventDay(a).localeCompare(eventDay(b)));
    body += h2(esc(label));
    if (actus.length) body += actus.map(item).join("");
    if (evs.length) body += `<div style="font-size:12px;font-weight:700;color:#1d1d1f;margin:4px 0 8px">Agenda</div>` + evs.map(item).join("");
  }
  if (!fresh.length) body += `<p style="font-size:15px;color:#1d1d1f;margin:18px 0">Aucune nouvelle actu ni date d'agenda depuis le dernier digest.</p>`;
  const upcoming = weekAhead.filter(a => !fresh.includes(a));
  if (upcoming.length) body += h2("Dans les 7 prochains jours") + upcoming.map(a => `<div style="font-size:14px;margin:0 0 8px;color:#1d1d1f"><b>${esc(fmtDay(eventDay(a), { weekday: "short", day: "numeric", month: "short" }))}</b> · <a href="${esc(a.url)}" style="color:#1d1d1f">${esc(a.title)}</a> <span style="color:#6e6e73">(${esc(LABEL[a.source] ?? a.source)})</span></div>`).join("");
  const failing = Object.entries(data.sources ?? {}).filter(([, s]) => !s.ok).map(([k]) => LABEL[k] ?? k);
  if (failing.length) body += `<p style="font-size:12px;color:#b26a00;margin:22px 0 0">Relevé incomplet ce matin pour : ${esc(failing.join(", "))}.</p>`;

  const html = `<!doctype html><html><body style="margin:0;background:#f5f5f7"><div style="max-width:620px;margin:0 auto;padding:24px 16px;font-family:${F}">
<div style="font-size:12px;font-weight:600;color:#6e6e73;text-transform:uppercase;letter-spacing:.06em">${esc(fmtDay(day))}</div>
<h1 style="margin:2px 0 18px;font-size:28px;letter-spacing:-.02em;color:#F8AC00">Actus <span style="font-weight:300">|</span> L'Institut Agro</h1>
<div style="background:#ffffff;border-radius:18px;padding:20px 20px 8px">${body}</div>
<p style="font-size:12px;color:#6e6e73;margin:16px 4px">Nouveautés relevées depuis le ${esc(fmtDay(prevWorkday(day)))} à 7 h. Dijon n'est pas couvert (le site refuse les robots). <a href="${SITE}" style="color:#0071e3">Ouvrir le fil complet</a></p>
</div></body></html>`;

  /* --- version texte --- */
  const lines = [`Actus | L'Institut Agro — ${fmtDay(day)}`, ""];
  if (onCampus.length) { lines.push("AUJOURD'HUI SUR LES CAMPUS"); onCampus.forEach(a => lines.push(`- ${a.title} (${LABEL[a.source]}) — ${a.place || "lieu non précisé"}`, `  ${a.url}`)); lines.push(""); }
  for (const [src, label] of SCHOOLS) {
    const mine = fresh.filter(a => a.source === src); if (!mine.length) continue;
    lines.push(label.toUpperCase());
    mine.forEach(a => lines.push(`- ${a.kind === "agenda" ? "[Agenda, " + when(a) + "] " : ""}${a.title}`, `  ${a.url}`));
    lines.push("");
  }
  if (!fresh.length) lines.push("Aucune nouvelle actu ni date d'agenda depuis le dernier digest.", "");
  if (upcoming.length) { lines.push("DANS LES 7 PROCHAINS JOURS"); upcoming.forEach(a => lines.push(`- ${fmtDay(eventDay(a), { weekday: "short", day: "numeric", month: "short" })} : ${a.title} (${LABEL[a.source]})`)); lines.push(""); }
  if (failing.length) lines.push(`Relevé incomplet ce matin pour : ${failing.join(", ")}.`, "");
  lines.push(`Fil complet : ${SITE}`);

  /* --- alerte : une source en échec deux jours ouvrés de suite (puis rappel chaque semaine) --- */
  const down = Object.entries(data.sources ?? {}).filter(([, s]) => (s.failDays ?? 0) >= 2 && (s.failDays - 2) % 5 === 0 && s.lastFailDay === parisDay(now));
  const alert = down.length ? {
    subject: `Actus Institut Agro · alerte : ${down.map(([k]) => LABEL[k] ?? k).join(", ")} injoignable${down.length > 1 ? "s" : ""} depuis ${down[0][1].failDays} jours`,
    text: [
      "Le robot de collecte n'arrive plus à lire :", "",
      ...down.flatMap(([k, s]) => [`- ${LABEL[k] ?? k} : en échec depuis ${s.failDays} jours de relevé (depuis le ${fmtDay(s.failingSince)}).`, ...(s.errors ?? []).map(e => `  ${e}`)]), "",
      "Ses contenus déjà connus restent affichés sur le site. Si la cause est « 403 » ou « connexion refusée », le site bloque peut-être les robots ; si c'est « aucun contenu trouvé », sa mise en page a probablement changé : il faut ajuster les règles de lecture.",
      "", `Fil : ${SITE}`, "Relancer un relevé : https://github.com/Adso-De-Melk-Oli/actus-institut-agro/actions/workflows/collect.yml",
    ].join("\n"),
  } : null;

  return { forDate: day, builtAt: now.toISOString(), window: { from: from.toISOString(), to: to.toISOString() }, counts: { actus: nActu, agenda: nEv, today: onCampus.length, week: upcoming.length }, subject, html, text: lines.join("\n"), alert };
}
