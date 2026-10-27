// Envoie le digest du matin (et l'alerte éventuelle) par Gmail, photos jointes.
// Lancé par .github/workflows/send.yml. Secrets : GMAIL_USER, GMAIL_APP_PASSWORD.
//
//   node scripts/send.mjs            → envoi du jour : seulement si le digest est celui d'aujourd'hui,
//                                      qu'il est 7 h passées à Paris et qu'il n'a pas déjà été envoyé
//   MODE=test node scripts/send.mjs  → envoie l'aperçu (data/preview/digest.json), objet préfixé [Test]
//   Un digest « rien de nouveau » n'est envoyé qu'à l'administrateur.

import { readFile, writeFile } from "node:fs/promises";
import nodemailer from "nodemailer";
import { parisDay } from "./digest.mjs";

// destinataires du digest : secret DIGEST_TO (adresses séparées par des virgules), pour ne pas les publier dans
// ce dépôt public ; essais et alertes ne vont qu'à l'administrateur
const ADMIN = "olivier.piau@supagro.fr";
const DIGEST_TO = (process.env.DIGEST_TO || ADMIN).split(/[,;\s]+/).map(x => x.trim()).filter(x => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(x));
const { GMAIL_USER, GMAIL_APP_PASSWORD } = process.env;
const TEST = process.env.MODE === "test";
const now = new Date();
const today = parisDay(now);
const parisHour = +now.toLocaleString("en-GB", { timeZone: "Europe/Paris", hour: "2-digit", hour12: false });

// « ::error:: » : la cause apparaît dans le résumé du run GitHub, pas seulement dans son journal
/** Décalage horaire de Paris à cette date, au format « +02:00 ». */
function parisOffset(d) {
  const p = new Date(d.toLocaleString("en-US", { timeZone: "Europe/Paris" })), u = new Date(d.toLocaleString("en-US", { timeZone: "UTC" }));
  const m = Math.round((p - u) / 60000);
  return `${m >= 0 ? "+" : "-"}${String(Math.floor(Math.abs(m) / 60)).padStart(2, "0")}:${String(Math.abs(m) % 60).padStart(2, "0")}`;
}
const fail = msg => { console.log(`::error title=Envoi du digest::${msg}`); process.exit(1); };
if (!GMAIL_USER || !GMAIL_APP_PASSWORD) fail(`Secret ${!GMAIL_USER ? "GMAIL_USER" : "GMAIL_APP_PASSWORD"} absent ou vide : vérifier son nom dans Settings → Secrets and variables → Actions.`);

try {
  const file = TEST ? "data/preview/digest.json" : "data/digest.json";
  const digest = JSON.parse(await readFile(file, "utf8"));
  let sent = {};
  try { sent = JSON.parse(await readFile("data/sent.json", "utf8")); } catch {}

  if (!TEST) {
    if (digest.forDate !== today) { console.log(`Digest du ${digest.forDate}, pas d'aujourd'hui (${today}) : le relevé n'est pas encore passé, on attend le prochain créneau.`); process.exit(0); }
    if (parisHour < 7) {
      // lancé juste après le relevé (WAIT=1) : on attend 7 h, deux heures au plus ; sinon on laisse un créneau suivant envoyer
      const at7 = new Date(`${today}T07:00:00${parisOffset(now)}`);
      const ms = at7 - Date.now();
      if (process.env.WAIT !== "1" || ms > 2 * 3600e3) { console.log(`Il est ${parisHour} h à Paris : envoi à partir de 7 h.`); process.exit(0); }
      console.log(`Attente jusqu'à 7 h (${Math.round(ms / 60000)} min)…`);
      await new Promise(r => setTimeout(r, Math.max(0, ms)));
    }
    if (sent.digestFor === today) { console.log(`Digest du ${today} déjà envoyé.`); process.exit(0); }
  }

  const transport = nodemailer.createTransport({ service: "gmail", auth: { user: GMAIL_USER.trim(), pass: GMAIL_APP_PASSWORD.replace(/\s+/g, "") } });
  const from = `"Actus | L'Institut Agro" <${GMAIL_USER}>`;
  // les photos sont référencées dans le HTML par cid:att0, cid:att1… dans l'ordre de digest.images
  const attachments = [];
  for (const [i, img] of (digest.images ?? []).entries()) {
    try { attachments.push({ filename: `${img.id}.jpg`, content: await readFile(img.path), cid: `att${i}`, contentDisposition: "inline" }); }
    catch { console.warn(`photo manquante : ${img.path}`); }
  }
  const subject = (TEST ? "[Test] " : "") + digest.subject;
  // un digest sans aucune nouveauté ne part qu'à l'administrateur, pour vérifier que le robot tourne
  const empty = digest.empty ?? (digest.counts && !digest.counts.actus && !digest.counts.agenda && !digest.counts.today && !digest.counts.remote);
  const to = TEST || empty ? [ADMIN] : DIGEST_TO;
  const info = await transport.sendMail({ from, to, subject, html: digest.html, text: digest.text, attachments });
  console.log(`Envoyé à ${to.length} destinataire${to.length > 1 ? "s" : ""} : ${subject} (${attachments.length} photos) — ${info.messageId}`);

  const alerts = { ...(sent.alerts ?? {}) };
  if (digest.alert && !TEST) {
    await transport.sendMail({ from, to: ADMIN, subject: digest.alert.subject, text: digest.alert.text });
    for (const a of digest.alert.sources ?? []) alerts[a.source] = { since: a.since, at: today };
    console.log(`Alerte envoyée : ${digest.alert.subject}`);
  }
  // windowTo : le prochain digest reprendra exactement là où celui-ci s'arrête
  if (!TEST) await writeFile("data/sent.json", JSON.stringify({ digestFor: today, sentAt: new Date().toISOString(), subject, windowTo: digest.window?.to, alerts }, null, 1) + "\n");
} catch (e) {
  fail(`${e.code ?? ""} ${e.responseCode ?? ""} ${String(e.response ?? e.message).replace(/\s+/g, " ").slice(0, 300)}`);
}
