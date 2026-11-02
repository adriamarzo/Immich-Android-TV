package nl.giejay.android.tv.immich.api.model

// Body of POST /search/smart. Only fields the v3 endpoint accepts: it rejects unknown properties.
// Field names are kept by the api.model.** rule in proguard-rules.pro, as with SearchRequest.
data class SmartSearchRequest(
    val query: String,
    val page: Int,
    val size: Int,
    val withExif: Boolean = true,
    val visibility: String = "timeline"
)
