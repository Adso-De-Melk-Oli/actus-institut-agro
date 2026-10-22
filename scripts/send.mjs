// Envoie le digest du matin (et l'alerte éventuelle) par Gmail, photos jointes.
// Lancé par .github/workflows/send.yml. Secrets : GMAIL_USER, GMAIL_APP_PASSWORD.
//
//   node scripts/send.mjs            → envoi du jour : seulement si le digest est celui d'aujourd'hui,
//                                      qu'il est 7 h passées à Paris et qu'il n'a pas déjà été envoyé
//   MODE=test node scripts/send.mjs  → envoie l'aperçu (data/preview/digest.json), objet préfixé [Test]

import { readFile, writeFile } from "node:fs/promises";
import nodemailer from "nodemailer";
import { parisDay } from "./digest.mjs";

const TO = process.env.MAIL_TO || "olivier.piau@supagro.fr";
const { GMAIL_USER, GMAIL_APP_PASSWORD } = process.env;
const TEST = process.env.MODE === "test";
const now = new Date();
const today = parisDay(now);
const parisHour = +now.toLocaleString("en-GB", { timeZone: "Europe/Paris", hour: "2-digit", hour12: false });

// « ::error:: » : la cause apparaît dans le résumé du run GitHub, pas seulement dans son journal
const fail = msg => { console.log(`::error title=Envoi du digest::${msg}`); process.exit(1); };
if (!GMAIL_USER || !GMAIL_APP_PASSWORD) fail(`Secret ${!GMAIL_USER ? "GMAIL_USER" : "GMAIL_APP_PASSWORD"} absent ou vide : vérifier son nom dans Settings → Secrets and variables → Actions.`);

try {
  const file = TEST ? "data/preview/digest.json" : "data/digest.json";
  const digest = JSON.parse(await readFile(file, "utf8"));
  let sent = {};
  try { sent = JSON.parse(await readFile("data/sent.json", "utf8")); } catch {}

  if (!TEST) {
    if (digest.forDate !== today) { console.log(`Digest du ${digest.forDate}, pas d'aujourd'hui (${today}) : le relevé n'est pas encore passé, on attend le prochain créneau.`); process.exit(0); }
    if (parisHour < 7) { console.log(`Il est ${parisHour} h à Paris : envoi à partir de 7 h.`); process.exit(0); }
    if (sent.digestFor === today) { console.log(`Digest du ${today} déjà envoyé.`); process.exit(0); }
  }

  const transport = nodemailer.createTransport({ service: "gmail", auth: { user: GMAIL_USER, pass: GMAIL_APP_PASSWORD } });
  const from = `"Actus | L'Institut Agro" <${GMAIL_USER}>`;
  // les photos sont référencées dans le HTML par cid:att0, cid:att1… dans l'ordre de digest.images
  const attachments = [];
  for (const [i, img] of (digest.images ?? []).entries()) {
    try { attachments.push({ filename: `${img.id}.jpg`, content: await readFile(img.path), cid: `att${i}`, contentDisposition: "inline" }); }
    catch { console.warn(`photo manquante : ${img.path}`); }
  }
  const subject = (TEST ? "[Test] " : "") + digest.subject;
  const info = await transport.sendMail({ from, to: TO, subject, html: digest.html, text: digest.text, attachments });
  console.log(`Envoyé à ${TO} : ${subject} (${attachments.length} photos) — ${info.messageId}`);

  if (digest.alert && !TEST) {
    await transport.sendMail({ from, to: TO, subject: digest.alert.subject, text: digest.alert.text });
    console.log(`Alerte envoyée : ${digest.alert.subject}`);
  }
  if (!TEST) await writeFile("data/sent.json", JSON.stringify({ digestFor: today, sentAt: now.toISOString(), subject }, null, 1) + "\n");
} catch (e) {
  fail(`${e.code ?? ""} ${e.responseCode ?? ""} ${String(e.response ?? e.message).replace(/\s+/g, " ").slice(0, 300)}`);
}
