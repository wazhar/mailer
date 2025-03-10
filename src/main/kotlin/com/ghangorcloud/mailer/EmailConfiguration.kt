package com.ghangorcloud.mailer

import java.util.*

class EmailConfig(propertiesPath: String) {
    private val properties = Properties()

    init {
        val inputStream = this::class.java.classLoader.getResourceAsStream(propertiesPath)
            ?: throw IllegalArgumentException("Properties file '$propertiesPath' not found in classpath resources.")
        properties.load(inputStream)
    }

    val smtpHost: String = properties.getProperty("smtp.host")
    val smtpPort: Int = properties.getProperty("smtp.port").toInt()

    val smtpAuthEnabled: Boolean = properties.getProperty("smtp.auth.enabled", "false").toBoolean()
    val smtpUsername: String? = properties.getProperty("smtp.username")
    val smtpPassword: String? = properties.getProperty("smtp.password")
    val smtpStartTlsEnabled: Boolean = properties.getProperty("smtp.starttls.enabled", "false").toBoolean()
    val smtpFromAddress: String = properties.getProperty("mail.from", "no-reply@example.com")

    val connectionReuse: Boolean = properties.getProperty("smtp.connectionReuse", "false").toBoolean()

    val workerThreads: Int = properties.getProperty("mail.workerThreads", "1").toInt()
    val totalEmails: Int = properties.getProperty("mail.totalEmails", "1").toInt()
    val mailsPerThread: Int = properties.getProperty("smtp.mailsPerThread", "1").toInt()

    val contentType: String = properties.getProperty("mail.contentType", "text/plain")
    val attachmentEnabled: Boolean = properties.getProperty("mail.attachment.enabled", "false").toBoolean()
    val attachmentPath: String? = properties.getProperty("mail.attachment.path")

    val recipientsTo: List<String> = properties.getProperty("mail.to").split(",")
    val recipientsCc: List<String>? = properties.getProperty("mail.cc")?.split(",")
    val recipientsBcc: List<String>? = properties.getProperty("mail.bcc")?.split(",")

    val emailSubject: String = properties.getProperty("mail.subject", "Default Email Subject")
    val emailInlineContent: String = properties.getProperty("mail.inlineContent", "Default Email Content")
}