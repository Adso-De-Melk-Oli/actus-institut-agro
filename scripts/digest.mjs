// Compose le digest du matin (nouveautés depuis le précédent, par école, + événements du jour
// sur les campus) et l'éventuelle alerte de panne. Écrit data/digest.json, que la tâche
// d'envoi de 7 h 00 lit tel quel.

const TZ = "Europe/Paris";
const SITE = "https://adso-de-melk-oli.github.io/actus-institut-agro/";
// [clé, nom, couleur, teinte claire opaque] — les messageries retirent les couleurs semi-transparentes
const SCHOOLS = [
  ["institut", "L'Institut Agro", "#2f8f46", "#e6f3e9"],
  ["montpellier", "Institut Agro Montpellier", "#c9531f", "#fbebe3"],
  ["rennes", "Institut Agro Rennes-Angers", "#1f6fd1", "#e5effb"],
  ["dijon", "Institut Agro Dijon", "#8e2b4e", "#f6e6ec"],
  ["fondation", "Fondation de l'Institut Agro", "#0e7f81", "#e1f2f2"],
];
export const MAX_PHOTOS = 15; // vignettes de 4 Ko environ
export const SCHOOL_COLORS = Object.fromEntries(SCHOOLS.map(([k, l, c]) => [k, { label: l, color: c }]));
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
/** Jour du prochain digest : aujourd'hui (jour ouvré) tant qu'il n'est pas parti, même si le relevé est en retard ;
 *  sinon le jour ouvré suivant. sent = contenu de data/sent.json ({ digestFor, windowTo, alerts }). */
export function digestDayFor(now, sent = {}) {
  const today = parisDay(now);
  const workday = ![0, 6].includes(weekday(today));
  return workday && sent.digestFor !== today ? today : nextWorkday(addDays(today, 1));
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

/** Sélection du jour : nouveautés, événements du jour (sur place / à distance), semaine à venir. */
function select(data, now, sent = {}) {
  const day = digestDayFor(now, sent);
  // fenêtre : depuis la fin de celle du dernier digest envoyé, jusqu'à maintenant (ou 7 h le jour même)
  const from = sent.windowTo ? new Date(sent.windowTo) : parisInstant(prevWorkday(day), 7);
  const to = day === parisDay(now) ? new Date(Math.max(+now, +parisInstant(day, 7))) : parisInstant(day, 7);
  const arts = data.articles ?? [];
  const fresh = arts.filter(a => !a.seed && a.firstSeen && new Date(a.firstSeen) > from && new Date(a.firstSeen) <= to);
  const today = arts.filter(a => a.kind === "agenda" && eventDay(a) <= day && day <= eventEnd(a));
  const remote = today.filter(a => a.remote);
  const onCampus = today.filter(a => !a.remote && (!a.place || CAMPUS.test(a.place)));
  const upcoming = arts.filter(a => a.kind === "agenda" && eventDay(a) > day && eventDay(a) <= addDays(day, 7) && !fresh.includes(a))
    .sort((a, b) => eventDay(a).localeCompare(eventDay(b)));
  return { day, from, to, fresh, onCampus, remote, upcoming };
}
/** Contenus qui auront une photo dans le digest, par ordre d'importance (6 au plus). */
export function photoCandidates(data, now = new Date(), sent = {}) {
  const { onCampus, fresh } = select(data, now, sent);
  const order = [...onCampus, ...fresh.filter(a => a.kind === "actu"), ...fresh.filter(a => a.kind === "agenda")];
  return [...new Map(order.filter(a => a.image).map(a => [a.id, a])).values()].slice(0, MAX_PHOTOS);
}

/** thumbs : { [id]: chemin de la vignette } pour les contenus dont la vignette a pu être préparée. */
export function buildDigest(data, now = new Date(), thumbs = {}, sent = {}) {
  const { day, from, to, fresh, onCampus, remote, upcoming } = select(data, now, sent);
  const sinceLabel = `${fmtDay(parisDay(from))} à ${from.toLocaleTimeString("fr-FR", { timeZone: TZ, hour: "2-digit", minute: "2-digit" }).replace(":", " h ")}`;
  const nActu = fresh.filter(a => a.kind === "actu").length, nEv = fresh.filter(a => a.kind === "agenda").length;
  const parts = [];
  if (onCampus.length) parts.push(`${onCampus.length} événement${onCampus.length > 1 ? "s" : ""} sur les campus aujourd'hui`);
  if (nActu) parts.push(`${nActu} actu${nActu > 1 ? "s" : ""}`);
  if (nEv) parts.push(`${nEv} date${nEv > 1 ? "s" : ""} d'agenda`);
  const subject = `Actus Institut Agro · ${fmtDay(day)} · ${parts.length ? parts.join(", ") : "rien de nouveau"}`;

  // images jointes : cid:att0, cid:att1… dans l'ordre d'apparition
  const images = [];
  // une photo, ou à défaut la vignette de remplacement de l'école (une seule pièce jointe par école)
  const photo = (a, w, h) => {
    const key = thumbs[a.id] ? a.id : thumbs[`ph:${a.source}`] ? `ph:${a.source}` : null;
    if (!key) return "";
    let i = images.findIndex(x => x.id === key);
    if (i < 0) { images.push({ id: key, path: thumbs[key] }); i = images.length - 1; }
    return `<img src="cid:att${i}" width="${w}" height="${h}" alt="" style="display:block;width:${w}px;height:${h}px;border:0;border-radius:10px">`;
  };
  // logo de l'Institut Agro en tête (thumbs.logo = chemin du PNG, fourni quand l'e-mail part de GitHub)
  let logoCell = "";
  if (thumbs.logo) {
    images.push({ id: "logo", path: thumbs.logo });
    logoCell = `<td width="56" valign="middle" style="padding:0 0 0 6px"><img src="cid:att${images.length - 1}" width="48" height="48" alt="L'Institut Agro" style="display:block;width:48px;height:48px;border:0"></td>`;
  }
  const school = src => SCHOOLS.find(s => s[0] === src) ?? [src, LABEL[src] ?? src, "#6e6e73", "#f0f0f2"];
  const F = "-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif";
  const pill = src => { const [, label, color, tint] = school(src); return `<span style="display:inline-block;padding:3px 10px;border-radius:999px;background-color:${tint};color:${color};font-size:12px;font-weight:700;letter-spacing:.01em">${esc(label)}</span>`; };
  const link = (a, size) => `<a href="${esc(a.url)}" style="font-size:${size}px;font-weight:700;color:#1d1d1f;text-decoration:none;line-height:1.3">${esc(a.title)}</a>`;
  const row = (left, right) => `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr>${left ? `<td width="132" valign="top" style="padding:0 16px 0 0">${left}</td>` : ""}<td valign="top">${right}</td></tr></table>`;
  const sep = `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr><td height="1" bgcolor="#ececf0" style="background-color:#ececf0;font-size:0;line-height:0">&nbsp;</td></tr></table>`;
  const item = a => {
    const meta = a.kind === "agenda" ? `<div style="font-size:13px;color:#a35d00;font-weight:700;margin:0 0 5px">${esc(when(a))}${a.remote ? " · À distance" : a.place ? " · " + esc(a.place) : ""}</div>` : "";
    return `<div style="padding:18px 0">${row(photo(a, 120, 80), `${meta}${link(a, 17)}${a.summary ? `<div style="font-size:14px;color:#55555c;line-height:1.5;margin-top:6px">${esc(a.summary)}</div>` : ""}`)}</div>`;
  };
  const sectionTitle = (src) => { const [, label, color] = school(src); return `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:34px 0 4px"><tr><td width="5" bgcolor="${color}" style="background-color:${color};border-radius:3px">&nbsp;</td><td style="padding:2px 0 2px 12px;font-size:21px;font-weight:800;color:#1d1d1f;letter-spacing:-.01em">${esc(label)}</td></tr></table>`; };
  const subTitle = t => `<div style="font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#86868b;margin:14px 0 0">${t}</div>`;

  let html = "";
  if (onCampus.length || remote.length) {
    html += `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 8px"><tr><td bgcolor="#fff1cc" style="background-color:#fff1cc;border-radius:18px;padding:24px 24px 8px">`
      + `<div style="font-size:26px;font-weight:800;color:#1d1d1f;letter-spacing:-.02em;margin:0 0 4px">Aujourd'hui sur les campus</div>`
      + `<div style="font-size:14px;color:#7a5200;margin:0 0 8px">${esc(fmtDay(day))}</div>`
      + onCampus.map(a => `<div style="padding:16px 0">${row(photo(a, 120, 80), `<div style="margin:0 0 8px">${pill(a.source)}</div>${link(a, 18)}<div style="font-size:14px;color:#55555c;margin-top:6px">${esc(a.place || "Lieu non précisé")}${eventEnd(a) !== eventDay(a) ? " · " + esc(when(a)) : ""}</div>`)}</div>`).join("")
      + (onCampus.length ? "" : `<div style="font-size:15px;color:#55555c;padding:8px 0 16px">Aucun événement sur les campus aujourd'hui.</div>`)
      + (remote.length ? `<div style="font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;color:#7a5200;margin:8px 0 0">Aussi aujourd'hui, à distance</div>` + remote.map(a => `<div style="padding:12px 0 16px"><div style="margin:0 0 6px">${pill(a.source)}</div>${link(a, 16)}</div>`).join("") : "")
      + `</td></tr></table>`;
  }
  for (const [src] of SCHOOLS) {
    const mine = fresh.filter(a => a.source === src);
    if (!mine.length) continue;
    const actus = mine.filter(a => a.kind === "actu");
    const evs = mine.filter(a => a.kind === "agenda").sort((a, b) => eventDay(a).localeCompare(eventDay(b)));
    html += sectionTitle(src);
    if (actus.length) html += subTitle(`Nouvelle${actus.length > 1 ? "s" : ""} actu${actus.length > 1 ? "s" : ""}`) + actus.map(item).join(sep);
    if (evs.length) html += subTitle(`Nouvelle${evs.length > 1 ? "s" : ""} date${evs.length > 1 ? "s" : ""} d'agenda`) + evs.map(item).join(sep);
  }
  if (!fresh.length) html += `<div style="font-size:17px;color:#1d1d1f;margin:30px 0 6px;font-weight:700">Rien de nouveau depuis le dernier digest.</div><div style="font-size:14px;color:#55555c">Aucune actu ni date d'agenda n'a été publiée sur les sites suivis.</div>`;
  if (upcoming.length) {
    html += `<div style="font-size:21px;font-weight:800;color:#1d1d1f;margin:38px 0 10px;letter-spacing:-.01em">Dans les 7 prochains jours</div>`
      + upcoming.map(a => `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 14px"><tr><td width="64" valign="top"><table role="presentation" cellpadding="0" cellspacing="0" border="0" width="52"><tr><td align="center" bgcolor="${school(a.source)[2]}" style="background-color:${school(a.source)[2]};color:#ffffff;font-size:11px;font-weight:800;text-transform:uppercase;padding:3px 0;border-radius:8px 8px 0 0">${esc(fmtDay(eventDay(a), { month: "short" }).replace(".", ""))}</td></tr><tr><td align="center" bgcolor="#f2f2f5" style="background-color:#f2f2f5;font-size:22px;font-weight:800;color:#1d1d1f;padding:4px 0;border-radius:0 0 8px 8px">${+eventDay(a).slice(8)}</td></tr></table></td><td valign="top" style="padding-top:2px">${link(a, 15)}<div style="font-size:13px;color:#86868b;margin-top:3px">${esc(fmtDay(eventDay(a), { weekday: "long" }))} · ${esc(LABEL[a.source] ?? a.source)}${a.remote ? " · à distance" : a.place ? " · " + esc(a.place) : ""}</div></td></tr></table>`).join("");
  }
  const failing = Object.entries(data.sources ?? {}).filter(([, s]) => !s.ok).map(([k]) => LABEL[k] ?? k);
  if (failing.length) html += `<div style="font-size:13px;color:#a35d00;margin:26px 0 0">Relevé incomplet ce matin pour : ${esc(failing.join(", "))}.</div>`;

  html = `<div style="background-color:#f5f5f7;padding:28px 12px;font-family:${F}"><table role="presentation" align="center" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:640px;margin:0 auto"><tr><td>
<div style="font-size:13px;font-weight:700;color:#86868b;text-transform:uppercase;letter-spacing:.08em;padding:0 6px">${esc(fmtDay(day))}</div>
<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:4px 0 22px"><tr>${logoCell}<td style="padding:0 6px;font-size:34px;font-weight:800;letter-spacing:-.03em;color:#F8AC00">Actus <span style="font-weight:300;color:#d9d9de">|</span> L'Institut Agro</td></tr></table>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr><td bgcolor="#ffffff" style="background-color:#ffffff;border-radius:22px;padding:28px 28px 30px">${html}</td></tr></table>
<div style="font-size:13px;color:#86868b;line-height:1.5;margin:20px 6px 0">Nouveautés relevées depuis le ${esc(sinceLabel)}.<br><a href="${SITE}" style="color:#0071e3;font-weight:600">Ouvrir le fil complet</a></div>
</td></tr></table></div>`;

  /* --- version texte --- */
  const lines = [`Actus | L'Institut Agro — ${fmtDay(day)}`, ""];
  if (onCampus.length) { lines.push("AUJOURD'HUI SUR LES CAMPUS"); onCampus.forEach(a => lines.push(`- ${a.title} (${LABEL[a.source]}) — ${a.place || "lieu non précisé"}`, `  ${a.url}`)); lines.push(""); }
  if (remote.length) { lines.push("AUSSI AUJOURD'HUI, À DISTANCE"); remote.forEach(a => lines.push(`- ${a.title} (${LABEL[a.source]})`, `  ${a.url}`)); lines.push(""); }
  for (const [src, label] of SCHOOLS) {
    const mine = fresh.filter(a => a.source === src); if (!mine.length) continue;
    lines.push(label.toUpperCase());
    mine.forEach(a => lines.push(`- ${a.kind === "agenda" ? "[Agenda, " + when(a) + "] " : ""}${a.title}`, `  ${a.url}`));
    lines.push("");
  }
  if (!fresh.length) lines.push("Rien de nouveau depuis le dernier digest.", "");
  if (upcoming.length) { lines.push("DANS LES 7 PROCHAINS JOURS"); upcoming.forEach(a => lines.push(`- ${fmtDay(eventDay(a), { weekday: "short", day: "numeric", month: "short" })} : ${a.title} (${LABEL[a.source]})`)); lines.push(""); }
  if (failing.length) lines.push(`Relevé incomplet ce matin pour : ${failing.join(", ")}.`, "");
  lines.push(`Fil complet : ${SITE}`);
  /* --- alerte : une source en échec deux jours ouvrés de suite (puis rappel chaque semaine) --- */
  // une alerte par panne (dès 2 jours de relevé en échec), puis un rappel tous les 7 jours tant qu'elle dure ;
  // sent.alerts[source] = { since, at } est noté par l'envoi : une alerte non envoyée sera reproposée
  const down = Object.entries(data.sources ?? {}).filter(([k, s]) => {
    if ((s.failDays ?? 0) < 2 || !s.failingSince) return false;
    const last = sent.alerts?.[k];
    return !last || last.since !== s.failingSince || (now - new Date(last.at + "T12:00:00Z")) / 864e5 >= 6.5;
  });
  const alert = down.length ? {
    sources: down.map(([k, s]) => ({ source: k, since: s.failingSince })),
    subject: `Actus Institut Agro · alerte : ${down.map(([k]) => LABEL[k] ?? k).join(", ")} injoignable${down.length > 1 ? "s" : ""} depuis ${down[0][1].failDays} jours`,
    text: [
      "Le robot de collecte n'arrive plus à lire :", "",
      ...down.flatMap(([k, s]) => [`- ${LABEL[k] ?? k} : en échec depuis ${s.failDays} jours de relevé (depuis le ${fmtDay(s.failingSince)}).`, ...(s.errors ?? []).map(e => `  ${e}`)]), "",
      "Ses contenus déjà connus restent affichés sur le site. Si la cause est « 403 » ou « connexion refusée », le site bloque peut-être les robots ; si c'est « aucun contenu trouvé », sa mise en page a probablement changé : il faut ajuster les règles de lecture.",
      "", `Fil : ${SITE}`, "Relancer un relevé : https://github.com/Adso-De-Melk-Oli/actus-institut-agro/actions/workflows/collect.yml",
    ].join("\n"),
  } : null;

  return { forDate: day, builtAt: now.toISOString(), window: { from: from.toISOString(), to: to.toISOString() }, counts: { actus: nActu, agenda: nEv, today: onCampus.length, remote: remote.length, week: upcoming.length }, subject, html, text: lines.join("\n"), images: images.map(x => ({ ...x, raw: `https://raw.githubusercontent.com/Adso-De-Melk-Oli/actus-institut-agro/main/${x.path}` })), alert };
}
