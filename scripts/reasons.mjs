// Traduit les erreurs techniques du robot en phrases compréhensibles par tout le monde.
// Les détails techniques restent dans `errors` (pour l'e-mail d'alerte) ; `problem` est affiché tel quel.

export function plainReason(message = "") {
  const m = String(message);
  if (/robots\.txt/i.test(m)) return "le site n'autorise pas la lecture automatique de cette page";
  if (/aucun contenu trouvé/i.test(m)) return "la page ne contient plus de contenus lisibles (sa mise en page a peut-être changé)";
  if (/pas de réponse|timeout|timed out|UND_ERR_.*TIMEOUT/i.test(m)) return "le site n'a pas répondu à temps";
  if (/\b5\d\d\b/.test(m)) return "le site est en panne ou en maintenance";
  if (/\b(404|410)\b/.test(m)) return "la page lue jusqu'ici n'existe plus (son adresse a peut-être changé)";
  if (/\b(401|403)\b/.test(m)) return "le site refuse l'accès au robot de lecture";
  if (/\b429\b/.test(m)) return "le site demande de patienter (trop de visites)";
  if (/ENOTFOUND|EAI_AGAIN|getaddrinfo/i.test(m)) return "l'adresse du site est introuvable";
  if (/injoignable|ECONNREFUSED|ECONNRESET|EHOSTUNREACH|socket|fetch failed|certificate|CERT_/i.test(m)) return "le site est injoignable";
  if (/XML|JSON|parse|Unexpected token/i.test(m)) return "le contenu reçu est illisible";
  return "le site n'a pas pu être lu";
}

/** Une phrase pour une source : la cause si tout a échoué, « son agenda n'a pas pu être lu : … » si une seule partie. */
export function sourceProblem(errors = []) {
  if (!errors.length) return undefined;
  const parts = errors.map(e => { const [kind, ...rest] = String(e).split(" : "); return { kind, reason: plainReason(rest.join(" : ") || e) }; });
  const reasons = [...new Set(parts.map(p => p.reason))];
  if (parts.length >= 2 || parts.length === 0) return reasons.join(" ; ");
  const what = parts[0].kind === "agenda" ? "son agenda n'a pas pu être lu" : "ses actus n'ont pas pu être lues";
  return `${what} : ${parts[0].reason}`;
}
