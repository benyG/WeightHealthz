package com.forge.core.ai.gemini

import javax.inject.Qualifier

/**
 * Distingue la configuration JSON de Gemini de celle du plan, fournie par `core-data` : les deux
 * vivent dans le même composant Hilt et n'ont pas les mêmes réglages.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class GeminiJson
