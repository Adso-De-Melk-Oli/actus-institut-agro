package io.github.adsodemelkoli.actus.data

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Réponse HTTP inattendue d'un serveur. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** Fichier téléchargé qui n'est pas la version attendue de l'application. */
class BadDownloadException : IOException("APK inattendu")

/**
 * Traduit une erreur technique en une phrase compréhensible par tout le monde : jamais de code,
 * d'adresse ni de nom d'exception à l'écran.
 */
object Problems {
    fun plain(e: Throwable?): String = when (e) {
        is UnknownHostException -> "Pas de connexion Internet."
        is SocketTimeoutException -> "Le serveur met trop de temps à répondre."
        is ConnectException, is NoRouteToHostException -> "Le serveur est injoignable pour le moment."
        is SSLException -> "La connexion sécurisée a échoué (Wi-Fi public ou réseau filtré ?)."
        is HttpStatusException -> when (e.code) {
            404, 410 -> "Les données sont introuvables pour le moment."
            401, 403, 429 -> "Le serveur limite l'accès pour le moment."
            in 500..599 -> "Le serveur est momentanément indisponible."
            else -> "Le serveur a répondu de façon inattendue."
        }
        is BadDownloadException -> "Le fichier téléchargé est incomplet."
        is SerializationException, is IllegalArgumentException -> "Les données reçues sont illisibles."
        is IOException -> "La connexion a été interrompue."
        else -> "Chargement impossible pour le moment."
    }
}
