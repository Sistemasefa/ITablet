package mx.efa.itablet.integration

/**
 * Public Microsoft Entra identifiers for ITablet.
 * No client secret is used by the Android public client.
 */
object EntraConfig {
    const val CLIENT_ID = "3163b458-4a4e-4d1d-b852-73bcfb831696"
    const val TENANT_ID = "2b42bfa9-1c65-4c99-9718-ae60c5762292"
    const val PACKAGE_NAME = "mx.efa.itablet"
    const val SIGNATURE_HASH_BASE64 = "Sgb4MAzR7Wo125OvBz4Cb3DFPUY="
    const val REDIRECT_URI = "msauth://mx.efa.itablet/Sgb4MAzR7Wo125OvBz4Cb3DFPUY%3D"
    const val MAIL_SEND_SCOPE = "Mail.Send"
}
