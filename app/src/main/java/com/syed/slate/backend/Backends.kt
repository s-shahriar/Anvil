package com.syed.slate.backend

import com.syed.slate.BuildConfig

/** The two modules of the app. Each one is backed by its own Supabase project. */
enum class ModuleId(val key: String, val title: String, val tagline: String) {
    GENERAL("general", "General", "Bangla, English, GK, vocabulary and more"),
    ICT("ict", "ICT", "MCQ, written, viva, practice and equations"),
}

class BackendConfig(
    val module: ModuleId,
    val url: String,
    /** Publishable key: safe to ship, sent as `apikey` only. */
    val publishableKey: String,
    val googleWebClientId: String,
)

object Backends {
    val general = BackendConfig(
        ModuleId.GENERAL,
        "https://dancporuhvfwieyyzhdd.supabase.co",
        "sb_publishable_c7KZSTVs3MzBN5fZpQjtyA_U_8FliVi",
        BuildConfig.GOOGLE_WEB_CLIENT_ID_GENERAL,
    )
    val ict = BackendConfig(
        ModuleId.ICT,
        "https://skqarcuggnbjbhpxytmd.supabase.co",
        "sb_publishable_BRYlReke53VzpDqMHVwpvw_gUSwqYi2",
        BuildConfig.GOOGLE_WEB_CLIENT_ID_ICT,
    )

    fun of(module: ModuleId) = if (module == ModuleId.GENERAL) general else ict
}
