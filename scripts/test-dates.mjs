// Vérifie la lecture des dates françaises (lancé par la CI avant la collecte).
import assert from "node:assert/strict";
const { parseFrenchDates: p } = await import("./dates.mjs");
const y = new Date().getFullYear();
const cases = [
  ["Le jeudi 5 novembre 2026", { date: "2026-11-05" }],
  ["Du 19 au 21 octobre 2026, campus", { date: "2026-10-19", endDate: "2026-10-21" }],
  ["du 30 septembre au 2 octobre 2026", { date: "2026-09-30", endDate: "2026-10-02" }],
  ["Du 04/11/2026 au 07/11/2026", { date: "2026-11-04", endDate: "2026-11-07" }],
  ["Publié le 12/10/2026", { date: "2026-10-12" }],
  ["1er décembre 2026", { date: "2026-12-01" }],
  ["21 janv. 2027 Montpellier", { date: "2027-01-21" }],
  ["Les 10 et 11 octobre 2026 à Paris", { date: "2026-10-10", endDate: "2026-10-11" }],
  ["Rien ici", null],
];
for (const [txt, want] of cases) assert.deepEqual(p(txt), want, txt);
assert.match(p("06 nov.").date, /^\d{4}-11-06$/);
console.log(`dates : ${cases.length + 1} cas OK`);
