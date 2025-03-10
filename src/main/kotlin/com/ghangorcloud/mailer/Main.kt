package com.ghangorcloud.mailer


fun main() {
    val config = EmailConfig("mail.properties")
    val sender = ConcurrentEmailSender(config)
    sender.sendEmails()
}
