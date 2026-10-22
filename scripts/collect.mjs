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
      const res = await fetch(u.origin + "/robots.txt", { headers: { "User-Agent": UA } });
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
  const res = await fetch(url, { headers: { "User-Agent": UA, "Accept-Language": "fr" }, redirect: "follow" });
  if (!res.ok) throw new Error(`${res.status} sur ${url}`);
  return res.text();
}

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
  const $ = cheerio.load(html);
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
    const prev = seen.get(href);
    if (!prev || (heading.length > prev.title.length)) seen.set(href, { url: href, title: heading, text: boxText, teaser });
  });
  return [...seen.values()].filter(x => x.title && x.title.length > 3);
}
function dateIn(text) { // date de publication jj/mm/aaaa ou « 12 octobre 2026 »
  const m = clean(text).match(/(\d{2})\/(\d{2})\/(\d{4})/);
  if (m) return ymd(+m[3], +m[2], +m[1]);
  return parseFrenchDates(text)?.date ?? null;
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
        .map(i => ({ kind: "actu", title: i.title, url: i.url, date: i.pubDate, summary: summarize(i.text) }));
    },
    async agenda() {
      const items = await rss("https://www.institut-agro-montpellier.fr/agenda/rss.xml");
      const out = [];
      for (const i of items) {
        let when = parseFrenchDates(i.text);
        let place;
        if (!when) { // la description ne donne pas la date : on lit la page de l'événement
          try { const txt = clean(cheerio.load(await get(i.url))("main, article, body").first().text()); when = parseFrenchDates(txt); place = placeIn(txt); } catch {}
        }
        if (!when) continue;
        out.push({ kind: "agenda", title: i.title, url: i.url, ...when, place: place ?? placeIn(i.text), summary: summarize(i.text.replace(/^.*?\d{4}\s*/, "")) });
      }
      return out;
    },
  },

  rennes: {
    prefix: "ren",
    async actus() {
      const base = "https://www.institut-agro-rennes-angers.fr/actualites";
      return listing(await get(base), base, /^\/actualites\/[^/]+\/?$/).slice(0, 12)
        .map(x => ({ kind: "actu", title: x.title, url: x.url, date: dateIn(x.text), summary: summarize(x.teaser && x.teaser !== x.title ? x.teaser : "") }));
    },
    async agenda() {
      const base = "https://www.institut-agro-rennes-angers.fr/agenda";
      return listing(await get(base), base, /^\/agenda\/[^/]+\/?$/).slice(0, 12)
        .map(x => ({ kind: "agenda", title: x.title, url: x.url, ...(parseFrenchDates(x.text.replace(x.title, "")) ?? {}), place: placeIn(x.text), summary: summarize(x.teaser && x.teaser !== x.title ? x.teaser : "") }))
        .filter(x => x.date);
    },
  },

  institut: {
    prefix: "ia",
    async actus() {
      const base = "https://www.institut-agro.fr/fr/toutes-les-actualites";
      return listing(await get(base), base, /^\/fr\/toutes-les-actualites\/[^/]+\/?$/).slice(0, 12)
        .map(x => ({ kind: "actu", title: x.title, url: x.url, date: dateIn(x.text), summary: summarize(x.teaser && x.teaser !== x.title ? x.teaser : "") }));
    },
    async agenda() {
      const base = "https://www.institut-agro.fr/fr";
      const links = listing(await get(base), base, /^\/fr\/agenda\/[^/]+\/?$/).slice(0, 10);
      const out = [];
      for (const x of links) { // la page d'accueil ne donne pas l'année : on lit chaque événement
        try {
          const $ = cheerio.load(await get(x.url));
          $("header, footer, nav, script, style").remove();
          const txt = clean($("main, article, body").first().text());
          const when = parseFrenchDates(txt.replace(x.title, ""));
          if (!when) continue;
          const lead = clean($("main p, article p").filter((_, p) => clean($(p).text()).length > 60).first().text());
          out.push({ kind: "agenda", title: x.title, url: x.url, ...when, place: placeIn(txt), summary: summarize(lead) });
        } catch (e) { if (DEBUG) console.warn("institut agenda", x.url, e.message); }
      }
      return out;
    },
  },

  fondation: {
    prefix: "fon",
    async actus() {
      return (await rss("https://fondation-institut-agro.fr/feed/"))
        .map(i => ({ kind: "actu", title: i.title, url: i.url, date: i.pubDate, summary: summarize(i.text.replace(/L’article .*? est apparu en premier sur .*$/i, "")) }));
    },
    async agenda() {
      const base = "https://fondation-institut-agro.fr/actualites-fondation-institut-agro/";
      return listing(await get(base), base, /^\/event\/[^/]+\/?$/).slice(0, 12)
        .map(x => ({ kind: "agenda", title: x.title.replace(/^["«\s]+|["»\s]+$/g, ""), url: x.url, ...(parseFrenchDates(x.text.replace(x.title, "")) ?? {}), place: placeIn(x.text), summary: summarize(x.teaser && x.teaser !== x.title ? x.teaser : "") }))
        .filter(x => x.date);
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
        byKey.set(k, Object.fromEntries(Object.entries({ ...old, ...it, id, source: name, summary: it.summary || old?.summary || "" }).filter(([, v]) => v !== undefined)));
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
