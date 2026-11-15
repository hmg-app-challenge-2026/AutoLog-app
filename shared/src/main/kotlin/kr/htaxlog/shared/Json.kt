package kr.htaxlog.shared

import kotlinx.serialization.json.Json

/** 앱·서버가 같은 설정으로 직렬화하도록 공유한다. */
val HTaxLogJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
