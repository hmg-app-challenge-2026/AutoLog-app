package kr.autolog.vehicle.ui.mail

import androidx.annotation.StringRes
import kr.autolog.vehicle.R
import kr.autolog.vehicle.core.MailError

/** 메일 오류 → 사람이 읽을 문장(운행기록부 보내기·설정 메일 계정이 같이 쓴다). */
@StringRes
fun mailErrorText(error: MailError): Int = when (error) {
    MailError.NOT_CONFIGURED -> R.string.mail_not_configured
    MailError.AUTH -> R.string.mail_error_auth
    MailError.NETWORK -> R.string.mail_error_network
    MailError.INVALID_RECIPIENT -> R.string.mail_error_recipient
    MailError.UNKNOWN -> R.string.mail_error_unknown
}
