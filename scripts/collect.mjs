// Collecte les actus et l'agenda de l'Institut Agro, de ses écoles et de sa Fondation,
// puis écrit data/articles.json. Lancé chaque matin par .github/workflows/collect.yml.
//
// Règles : on lit les flux RSS quand ils existent, sinon les pages Actualités/Agenda ;
// on respecte robots.txt (Dijon refuse les robots : on ne le lit pas) ; si une source
// échoue, on garde ses contenus de la veille.

import { readFile, writeFile, mkdir } from "node:fs/promises";
import * as cheerio from "cheerio";
import { XMLParser } from "fast-xml-parser";
import { parseFrenchDates, ymd } from "./dates.mjs";

const OUT = "data/articles.json";
const UA = "ActusInstitutAgroBot/1.0 (+https://github.com/Adso-De-Melk-Oli/actus-institut-agro)";
const DEBUG = process.env.DEBUG === "1";
const NOW = new Date();

/* ---------- utilitaires ---------- */

const sleep = ms => new Promise(r => setTimeout(r, ms));
const clean = s => String(s ?? "").replace(/\s+/g, " ").trim();
function stripHtml(html) {
  if (!html) return "";
  const $ = cheerio.load(`<div>${html}</div>`);
  return clean($("div").first().text());
}
function summarize(text, max = 200) {
  const t = clean(text);
  if (t.length <= max) return t;
  const cut = t.slice(0, max);
  return cut.slice(0, Math.max(cut.lastIndexOf(" "), max - 20)).replace(/[,;:\s]+$/, "") + "…";
}
function slugFromUrl(url) {
  const path = new URL(url).pathname.replace(/\/+$/, "");
  return path.split("/").pop().toLowerCase().replace(/[^a-z0-9-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 70) || "x";
}

const robotsCache = new Map();
async function allowedByRobots(url) {
  const u = new URL(url);
  if (!robotsCache.has(u.origin)) {
    let rules = [];
    try {
      const res = await fetch(u.origin + "/robots.txt", { headers: { "User-Agent": UA }, signal: AbortSignal.timeout(10_000) });
      if (res.ok) {
        let applies = false;
        for (const raw of (await res.text()).split(/\r?\n/)) {
          const line = raw.replace(/#.*/, "").trim();
          const [k, ...v] = line.split(":"); const val = v.join(":").trim();
          if (/^user-agent$/i.test(k)) applies = val === "*" || /actusinstitutagro/i.test(val);
          else if (applies && /^disallow$/i.test(k) && val) rules.push(val);
        }
      }
    } catch { /* robots.txt injoignable : on applique la prudence par défaut ci-dessous */ }
    robotsCache.set(u.origin, rules);
  }
  return !robotsCache.get(u.origin).some(p => u.pathname.startsWith(p));
}

async function get(url) {
  if (!(await allowedByRobots(url))) throw new Error(`robots.txt interdit ${url}`);
  await sleep(400); // politesse
  // 20 s maximum par page, et un second essai : un site en panne ne doit pas bloquer les autres
  let res;
  for (let attempt = 1; ; attempt++) {
    try {
      res = await fetch(url, { headers: { "User-Agent": UA, "Accept-Language": "fr" }, redirect: "follow", signal: AbortSignal.timeout(20_000) });
      if (res.status >= 500 && attempt < 2) throw new Error(`${res.status}`);
      break;
    } catch (e) {
      if (attempt >= 2) throw new Error(`${url} injoignable (${e.name === "TimeoutError" ? "pas de réponse en 20 s" : e.message})`);
      await sleep(5000);
    }
  }
  if (!res.ok) throw new Error(`${res.status} sur ${url}`);
  const body = await res.text();
  if (DEBUG && snapshots++ < 40) { // mode diagnostic : copie des pages lues, pour ajuster les règles de lecture
    await mkdir("debug", { recursive: true });
    await writeFile(`debug/${String(snapshots).padStart(2, "0")}-${new URL(url).hostname.split(".")[1] ?? "site"}-${slugFromUrl(url)}.html`, `<!-- ${url} -->\n` + body);
  }
  return body;
}
let snapshots = 0;

/* ---------- nettoyage des textes ---------- */

/** Retire du chapô le titre répété, la signature Drupal (« olivier jeu, 8 Oct 2026 - 07:31 ») et la date de tête. */
function cleanDesc(text, title = "") {
  let t = clean(text)
    .replace(/(?:^|\s)[\p{L}._-]+\s+(?:lun|mar|mer|jeu|ven|sam|dim)\.?,\s+\d{1,2}\s+\p{L}+\.?\s+\d{4}\s+-\s+\d{1,2}:\d{2}/giu, " ")
    .replace(/L[’']article .*? est apparu en premier sur .*$/i, "");
  t = clean(t);
  if (title && t.toLowerCase().startsWith(clean(title).toLowerCase())) t = t.slice(clean(title).length);
  return clean(t).replace(/^[\s:,;.–—-]+/, "");
}
/** « Le jeudi 5 novembre 2026, l'Institut… » → « L'Institut… » */
function dropLeadingDate(text) {
  const m = text.match(/^(?:le|du|les|depuis le)?\s*(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)?\s*\d{1,2}(?:er)?(?:\s+\S+){0,6}?\s+\d{4}(?:\s*,?\s*de\s+\d{1,2}h\d{0,2}\s+à\s+\d{1,2}h\d{0,2})?\s*[,.:–-]?\s*/i);
  const t = m ? text.slice(m[0].length) : text;
  return t ? t[0].toUpperCase() + t.slice(1) : t;
}
const goodSummary = (t, title) => t && t.length >= 40 && t.toLowerCase() !== String(title).toLowerCase();

/** Charge du HTML en séparant les blocs par une espace (sinon « Montpellier</div><div>Campus » devient « MontpellierCampus »). */
const loadSpaced = html => cheerio.load(String(html).replace(/<br\s*\/?>/gi, " ").replace(/<\/(p|div|li|h[1-6]|span|td|dd|dt|address|a|strong|em)>/gi, "$& "));
/** Nettoie un lieu : sans « + Google Map », sans doublons, raisonnablement court. */
function cleanPlace(p) {
  if (!p) return undefined;
  let t = clean(cheerio.load(`<p>${p}</p>`)("p").text()).replace(/\+?\s*Google\s*Maps?/gi, "").replace(/^(Lieu|Où|Adresse)\s*:?\s*/i, "").replace(/[\s,·-]+$/, "");
  if (t.length > 90) t = t.slice(0, 90).replace(/[,·-][^,·-]*$/, "");
  return t.length >= 3 ? t : undefined;
}
/** Lit la page d'un contenu : titre, texte, premier paragraphe, lieu. */
async function detail(url) {
  const $ = loadSpaced(await get(url));
  // lu AVANT le nettoyage, qui retire les balises script
  let ld = {};
  $('script[type="application/ld+json"]').each((_, sc) => {
    try {
      const walk = o => { if (!o || typeof o !== "object") return; if (Array.isArray(o)) return o.forEach(walk);
        if (/Event$/.test(String(o["@type"])) && o.startDate && !ld.startDate) ld = o; Object.values(o).forEach(walk); };
      walk(JSON.parse($(sc).html()));
    } catch {}
  });
  const ogImage = $('meta[property="og:image"]').attr("content");
  // titre : celui du contenu, pas celui du bandeau (« Agenda », « À la une ») ni le slogan du site
  const stripSite = t => clean(t).replace(/\s+[|–—-]\s+[^|–—]*Institut Agro[^|–—]*$/i, "").replace(/\s+\|\s+.*$/, "");
  const refTitles = [$('meta[property="og:title"]').attr("content"), $("title").first().text()].filter(Boolean).map(stripSite).filter(t => t.length > 3);
  const normT = t => clean(t).toLowerCase().replace(/[«»"“”'’]/g, "").replace(/\s+/g, " ");
  const headings = $("h1, h2").toArray()
    .filter(h => !/sr-only|visually-hidden|element-invisible/.test($(h).attr("class") || "") && !/^block-|breadcrumb/.test($(h).attr("id") || "") && !$(h).closest("header, nav, footer, [class*=banner], [class*=bandeau], [class*=hero]").length)
    .map(h => clean($(h).text())).filter(Boolean);
  const pageTitle = headings.find(h => refTitles.some(r => normT(r) === normT(h))) ?? refTitles[0] ?? headings[0];
  $("header, footer, nav, script, style, noscript, form, [class*=cookie], [class*=breadcrumb], [class*=share]").remove();
  const root = $("main").first().length ? $("main").first() : $("body");
  const h1 = clean(root.find("h1").first().text());
  const lead = clean(root.find("p").filter((_, p) => clean($(p).text()).length > 60).first().text());
  const placeEl = clean(root.find("[class*=lieu], [class*=location], [class*=venue], [class*=adresse], [class*=address], [class*=place]").first().text());
  const text = clean(root.text());
  const imgOf = v => typeof v === "string" ? v : Array.isArray(v) ? imgOf(v[0]) : v?.url;
  const firstImg = root.find("img").toArray().map(i => $(i).attr("src") || $(i).attr("data-src")).find(src => src && isPhoto(src));
  const image = [ogImage, imgOf(ld.image), firstImg].filter(Boolean).map(u => new URL(u, url).toString()).find(isPhoto);
  const loc = ld.location ? [ld.location.name, [].concat(ld.location.address?.streetAddress ?? [])[0]].filter(Boolean).join(" · ") : undefined;
  const ldWhen = ld.startDate ? { date: String(ld.startDate).slice(0, 10), ...(ld.endDate && String(ld.endDate).slice(0, 10) !== String(ld.startDate).slice(0, 10) ? { endDate: String(ld.endDate).slice(0, 10) } : {}) } : null;
  // Drupal (Rennes-Angers) : <time datetime="…">Samedi 24 octobre 2026 - 14:00</time><p>Campus de Rennes</p>
  const afterTime = clean(root.find("time[datetime]").first().next("p").text());
  const timePlace = afterTime && afterTime.length < 80 && !/\d{4}/.test(afterTime) ? afterTime : undefined;
  const domPlace = timePlace ?? placeEl && placeEl.length < 120 ? placeEl.replace(/^(Lieu|Où|Adresse)\s*:?\s*/i, "") : undefined;
  return { h1: pageTitle || h1, lead, text, image, ldWhen, place: cleanPlace(loc || domPlace || placeIn(text)) };
}
/** Une vraie photo, pas un logo, une icône ou un pixel de suivi. */
function isPhoto(src) { return /\.(jpe?g|png|webp|avif|gif)(\?|$)/i.test(src) && !/logo|icon|picto|sprite|avatar|placeholder|blank|pixel/i.test(src); }

/* ---------- lecture RSS ---------- */

const xml = new XMLParser({ ignoreAttributes: true, processEntities: true, htmlEntities: true });
async function rss(url) {
  const doc = xml.parse(await get(url));
  const items = doc?.rss?.channel?.item ?? [];
  return (Array.isArray(items) ? items : [items]).map(it => ({
    title: clean(stripHtml(String(it.title ?? ""))),
    url: clean(it.link),
    pubDate: it.pubDate ? new Date(it.pubDate).toISOString() : null,
    text: stripHtml(String(it["content:encoded"] ?? it.description ?? "")),
  })).filter(i => i.title && i.url);
}

/* ---------- lecture des pages de liste ---------- */

const ABS = (base, href) => new URL(href, base).toString().replace(/[#?].*$/, "");
/** Liens de détail d'une page de liste, avec le bloc qui les entoure (titre, date, chapô). */
function listing(html, base, pathRe) {
  const $ = loadSpaced(html);
  $("header, footer, nav, script, style, noscript").remove();
  const seen = new Map();
  $("a[href]").each((_, a) => {
    const href = ABS(base, $(a).attr("href"));
    const path = new URL(href).pathname;
    if (new URL(href).origin !== new URL(base).origin || !pathRe.test(path)) return;
    // le plus petit bloc parent qui ne contient qu'un seul contenu
    let box = $(a);
    for (let i = 0; i < 6; i++) {
      const parent = box.parent(); if (!parent.length) break;
      const others = parent.find("a[href]").toArray().map(x => ABS(base, $(x).attr("href"))).filter(h => pathRe.test(new URL(h).pathname) && h !== href);
      if (others.length) break;
      box = parent;
    }
    const heading = clean(box.find("h2,h3,h4,.title,[class*=title]").first().text()) || clean($(a).text()) || clean($(a).attr("title"));
    const boxText = clean(box.text());
    const teaser = clean(box.find("p,[class*=summary],[class*=chapo],[class*=teaser],[class*=description],[class*=body]").first().text());
    const thumb = box.find("img").toArray().map(i => $(i).attr("src") || $(i).attr("data-src")).find(src => src && isPhoto(src));
    const prev = seen.get(href);
    if (!prev || (heading.length > prev.title.length)) seen.set(href, { url: href, title: heading, text: boxText, teaser, image: thumb ? new URL(thumb, base).toString() : undefined });
  });
  return [...seen.values()].filter(x => x.title && x.title.length > 3);
}
function dateIn(text) { // date de publication jj/mm/aaaa ou « 12 octobre 2026 »
  const m = clean(text).match(/(\d{2})\/(\d{2})\/(\d{4})/);
  if (m) return ymd(+m[3], +m[2], +m[1]);
  return parseFrenchDates(text)?.date ?? null;
}
/** Complète des événements repérés dans une liste ou un flux en lisant leur page. */
async function events(cands, { titleFromPage = false } = {}) {
  const out = [];
  for (const c of cands) {
    let d = {};
    try { d = await detail(c.url); } catch (e) { if (DEBUG) console.warn("détail", c.url, e.message); }
    const title = (titleFromPage && d.h1) || c.title || d.h1;
    const when = d.ldWhen ?? parseFrenchDates(c.text?.replace(c.title, "") ?? "") ?? parseFrenchDates((d.text ?? "").replace(d.h1 ?? "", ""));
    if (!title || !when) continue;
    const fromList = dropLeadingDate(cleanDesc(c.teaser || c.text || "", title));
    const fromPage = dropLeadingDate(cleanDesc(d.lead ?? "", title));
    const summary = goodSummary(fromList, title) ? fromList : goodSummary(fromPage, title) ? fromPage : (fromList !== title ? fromList : "");
    out.push({ kind: "agenda", title, url: c.url, ...when, place: cleanPlace(c.place) ?? d.place, image: d.image ?? c.image, summary: summarize(summary) });
  }
  return out;
}
function placeIn(text) {
  const m = clean(text).match(/(?:Lieu|Où)\s*:?\s*([^|•\n]{3,80}?)(?=\s{2,}|\s(?:Date|Horaire|Tarif|Inscription|Gratuit|Lire|En savoir)|$)/i);
  return m ? clean(m[1]) : undefined;
}

/* ---------- sources ---------- */

const SOURCES = {
  montpellier: {
    prefix: "mtp",
    async actus() {
      return (await rss("https://www.institut-agro-montpellier.fr/rss.xml"))
        .filter(i => !/\/agenda\//.test(i.url))
        .map(i => ({ kind: "actu", title: i.title, url: i.url, date: i.pubDate, summary: summarize(cleanDesc(i.text, i.title)) }));
    },
    async agenda() {
      const items = await rss("https://www.institut-agro-montpellier.fr/agenda/rss.xml");
      return events(items.map(i => ({ url: i.url, title: i.title, text: cleanDesc(i.text, i.title) })));
    },
  },

  rennes: {
    prefix: "ren",
    async actus() {
      const base = "https://www.institut-agro-rennes-angers.fr/actualites";
      return listing(await get(base), base, /^\/actualites\/[^/]+\/?$/).slice(0, 12)
        .map(x => ({ kind: "actu", title: x.title, url: x.url, date: dateIn(x.text), image: x.image, summary: summarize(x.teaser && x.teaser !== x.title ? cleanDesc(x.teaser, x.title) : "") }));
    },
    async agenda() {
      const base = "https://www.institut-agro-rennes-angers.fr/agenda";
      return events(listing(await get(base), base, /^\/agenda\/[^/]+\/?$/).slice(0, 12), { titleFromPage: true });
    },
  },

  institut: {
    prefix: "ia",
    async actus() {
      const base = "https://www.institut-agro.fr/fr/toutes-les-actualites";
      return listing(await get(base), base, /^\/fr\/toutes-les-actualites\/[^/]+\/?$/).slice(0, 12)
        .map(x => ({ kind: "actu", title: x.title, url: x.url, date: dateIn(x.text), image: x.image, summary: summarize(x.teaser && x.teaser !== x.title ? cleanDesc(x.teaser, x.title) : "") }));
    },
    async agenda() { // la page d'accueil ne donne pas l'année : events() lit chaque événement
      const base = "https://www.institut-agro.fr/fr";
      return events(listing(await get(base), base, /^\/fr\/agenda\/[^/]+\/?$/).slice(0, 10).map(x => ({ url: x.url })), { titleFromPage: true });
    },
  },

  fondation: {
    prefix: "fon",
    async actus() {
      return (await rss("https://fondation-institut-agro.fr/feed/"))
        .map(i => ({ kind: "actu", title: i.title, url: i.url, date: i.pubDate, summary: summarize(cleanDesc(i.text, i.title)) }));
    },
    async agenda() {
      const base = "https://fondation-institut-agro.fr/actualites-fondation-institut-agro/";
      return events(listing(await get(base), base, /^\/event\/[^/]+\/?$/).slice(0, 12), { titleFromPage: true });
    },
  },
};

/* ---------- fusion et écriture ---------- */

let previous = { articles: [] };
try { previous = JSON.parse(await readFile(OUT, "utf8")); } catch {}

const key = a => `${a.kind}|${a.url}`;
const byKey = new Map(previous.articles.map(a => [key(a), a]));
const status = {};

for (const [name, src] of Object.entries(SOURCES)) {
  status[name] = { ok: true, actus: 0, agenda: 0, errors: [] };
  for (const kind of ["actus", "agenda"]) {
    try {
      const items = (await src[kind]()).filter(i => i.title && i.url);
      if (!items.length) throw new Error("aucun contenu trouvé (la page a peut-être changé)");
      for (const it of items) {
        const k = key(it); const old = byKey.get(k);
        const id = old?.id ?? `${src.prefix}-${it.kind === "agenda" ? "ag-" : ""}${slugFromUrl(it.url)}`;
        const fresh = Object.fromEntries(Object.entries(it).filter(([, v]) => v !== undefined && v !== ""));
        // un résumé ou un lieu déjà connus sont conservés : ils ont pu être relus à la main
        byKey.set(k, { ...old, ...fresh, id, source: name, summary: old?.summary || fresh.summary || "" });
      }
      status[name][kind === "actus" ? "actus" : "agenda"] = items.length;
      if (DEBUG) console.log(`\n[${name}/${kind}] ${items.length}`, JSON.stringify(items.slice(0, 3), null, 1));
    } catch (e) {
      status[name].ok = false;
      status[name].errors.push(`${kind} : ${e.message}`);
      console.warn(`⚠ ${name}/${kind} : ${e.message}`);
    }
  }
}

// visuels : pour les contenus sans image (flux RSS), on lit l'image de partage de leur page, une seule fois
let lookups = 0;
for (const a of byKey.values()) {
  if (a.image || a.noImage || lookups >= 60) continue;
  lookups++;
  try { const d = await detail(a.url); if (d.image) a.image = d.image; else a.noImage = true; }
  catch (e) { if (DEBUG) console.warn("image", a.url, e.message); }
}

// purge : actus de plus de 12 mois, événements terminés depuis plus de 6 mois
const limitActu = new Date(NOW); limitActu.setFullYear(limitActu.getFullYear() - 1);
const limitEvent = new Date(NOW); limitEvent.setMonth(limitEvent.getMonth() - 6);
const articles = [...byKey.values()].filter(a => {
  if (!a.date) return true;
  const d = new Date((a.endDate || a.date).length === 10 ? (a.endDate || a.date) + "T12:00:00Z" : (a.endDate || a.date));
  return a.kind === "agenda" ? d >= limitEvent : d >= limitActu;
}).sort((a, b) => String(b.date ?? "").localeCompare(String(a.date ?? "")));

const counts = {}; for (const a of articles) counts[a.source] = (counts[a.source] || 0) + 1;
for (const s of Object.keys(status)) status[s].count = counts[s] || 0;

await mkdir("data", { recursive: true });
await writeFile(OUT, JSON.stringify({ updatedAt: NOW.toISOString(), sources: status, articles }, null, 1) + "\n");
console.log(`\n${articles.length} contenus écrits dans ${OUT}`);
for (const [s, v] of Object.entries(status)) console.log(`  ${v.ok ? "✓" : "✗"} ${s} : ${v.actus} actus, ${v.agenda} événements relevés, ${v.count} en base${v.errors.length ? " — " + v.errors.join(" ; ") : ""}`);
