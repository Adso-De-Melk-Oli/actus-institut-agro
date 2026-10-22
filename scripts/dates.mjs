// Lecture des dates écrites en français (« jeudi 5 novembre 2026 », « du 19 au 21 octobre »…).
const clean = s => String(s ?? "").replace(/\s+/g, " ").trim();
export const ymd = (y, m, d) => `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;

const MONTHS = { janv: 1, janvier: 1, fevr: 2, fev: 2, fevrier: 2, mars: 3, avr: 4, avril: 4, mai: 5, juin: 6, juil: 7, juillet: 7, aout: 8, sept: 9, septembre: 9, oct: 10, octobre: 10, nov: 11, novembre: 11, dec: 12, decembre: 12 };
const norm = s => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
const MON_RE = "(janv(?:ier)?|f[eé]vr?(?:ier)?|mars|avr(?:il)?|mai|juin|juil(?:let)?|ao[uû]t|sept(?:embre)?|oct(?:obre)?|nov(?:embre)?|d[eé]c(?:embre)?)\\.?";
const mon = m => MONTHS[norm(m).replace(/\.$/, "")];
function guessYear(m, d) {
  const NOW = new Date(); // date sans année : la plus proche d'aujourd'hui
  const y = NOW.getFullYear();
  const cands = [y - 1, y, y + 1].map(yy => ({ yy, t: Math.abs(new Date(yy, m - 1, d) - NOW) }));
  return cands.sort((a, b) => a.t - b.t)[0].yy;
}

/** Cherche une date (ou une période) d'événement dans un texte. Renvoie {date, endDate} ou null. */
export function parseFrenchDates(text) {
  const t = " " + clean(text) + " ";
  let m;
  // du 30 septembre au 2 octobre 2026
  if ((m = t.match(new RegExp(`du\\s+(\\d{1,2})(?:er)?\\s+${MON_RE}\\s*(\\d{4})?\\s+au\\s+(\\d{1,2})(?:er)?\\s+${MON_RE}\\s*(\\d{4})?`, "i")))) {
    const y2 = m[6] ? +m[6] : guessYear(mon(m[5]), +m[4]); const y1 = m[3] ? +m[3] : (mon(m[2]) > mon(m[5]) ? y2 - 1 : y2);
    return { date: ymd(y1, mon(m[2]), +m[1]), endDate: ymd(y2, mon(m[5]), +m[4]) };
  }
  // du 19 au 21 octobre 2026
  if ((m = t.match(new RegExp(`du\\s+(\\d{1,2})(?:er)?\\s+au\\s+(\\d{1,2})(?:er)?\\s+${MON_RE}\\s*(\\d{4})?`, "i")))) {
    const y = m[4] ? +m[4] : guessYear(mon(m[3]), +m[1]);
    return { date: ymd(y, mon(m[3]), +m[1]), endDate: ymd(y, mon(m[3]), +m[2]) };
  }
  // les 10 et 11 octobre 2026
  if ((m = t.match(new RegExp(`(?:les|le)\\s+(\\d{1,2})(?:er)?\\s+et\\s+(?:le\\s+)?(\\d{1,2})\\s+${MON_RE}\\s*(\\d{4})?`, "i")))) {
    const y = m[4] ? +m[4] : guessYear(mon(m[3]), +m[1]);
    return { date: ymd(y, mon(m[3]), +m[1]), endDate: ymd(y, mon(m[3]), +m[2]) };
  }
  // du 04/11/2026 au 07/11/2026
  if ((m = t.match(/du\s+(\d{1,2})\/(\d{1,2})\/(\d{4})\s+au\s+(\d{1,2})\/(\d{1,2})\/(\d{4})/i)))
    return { date: ymd(+m[3], +m[2], +m[1]), endDate: ymd(+m[6], +m[5], +m[4]) };
  // 5 novembre 2026 / jeudi 5 novembre / 05 nov.
  if ((m = t.match(new RegExp(`(?:^|[^\\d])(\\d{1,2})(?:er)?\\s+${MON_RE}\\s*(\\d{4})?`, "i")))) {
    const y = m[3] ? +m[3] : guessYear(mon(m[2]), +m[1]);
    return { date: ymd(y, mon(m[2]), +m[1]) };
  }
  if ((m = t.match(/(\d{1,2})\/(\d{1,2})\/(\d{4})/))) return { date: ymd(+m[3], +m[2], +m[1]) };
  return null;
}

