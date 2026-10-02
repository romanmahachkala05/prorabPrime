package ru.prorabprime.data.settings

@JsFun("(key) => { const value = window.localStorage.getItem(key); return value === null ? null : value; }")
private external fun storageGet(key: String): JsString?

@JsFun("(key, value) => { window.localStorage.setItem(key, value); }")
private external fun storageSet(key: String, value: String)

/** The browser's `localStorage`: what a page keeps for itself between visits. */
internal object BrowserStorage {
    fun get(key: String): String? = storageGet(key)?.toString()

    fun set(key: String, value: String) = storageSet(key, value)
}
