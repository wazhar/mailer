package com.ghangorcloud.mailer

import jakarta.activation.FileDataSource
import jakarta.mail.*
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ConcurrentEmailSender(private val config: EmailConfig) {

    private val executor = Executors.newFixedThreadPool(config.workerThreads)
    private val emailsSentByThread = ConcurrentHashMap<String, AtomicInteger>()
    private val globalEmailCounter = AtomicInteger(0)

    private val session: Session = Session.getInstance(Properties().apply {
        put("mail.smtp.host", config.smtpHost)
        put("mail.smtp.port", "${config.smtpPort}")
        put("mail.smtp.auth", "${config.smtpAuthEnabled}")
        put("mail.smtp.starttls.enable", "${config.smtpStartTlsEnabled}")
    }, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(config.smtpUsername, config.smtpPassword)
        }
    })

    fun sendEmails() {
        repeat(config.workerThreads) {
            executor.submit { threadWorkerTask() }
        }
        executor.shutdown()
    }

    private fun threadWorkerTask() {
        val threadName = Thread.currentThread().name

        repeat(config.mailsPerThread) {
            val currentGlobalCount = globalEmailCounter.incrementAndGet()
            if (currentEmailNumberExceedsGlobalTotal(currentGlobalCount)) return@repeat
            try {
                sendSingleEmail(currentGlobalCount)
                incrementEmailCountForThread(threadName)
            } catch (e: Exception) {
                println("Error sending email #$currentGlobalCount from thread $threadName: ${e.message}")
            }
        }
    }

    private fun currentGlobalCount() = globalEmailCounter.get()

    private fun currentEmailNumberExceedsGlobalTotal(currentGlobalCount: Int): Boolean =
        currentGlobalCount > config.totalEmails

    private fun sendSingleEmail(emailNumber: Int) {
        MimeMessage(session).apply {
            setFrom(jakarta.mail.internet.InternetAddress(config.smtpFromAddress))
            config.recipientsTo.forEach {
                addRecipient(Message.RecipientType.TO, jakarta.mail.internet.InternetAddress(it.trim()))
            }
            config.recipientsCc?.forEach {
                addRecipient(Message.RecipientType.CC, jakarta.mail.internet.InternetAddress(it.trim()))
            }
            config.recipientsBcc?.forEach {
                addRecipient(Message.RecipientType.BCC, jakarta.mail.internet.InternetAddress(it.trim()))
            }

            setSubject("${config.emailSubject} (#$emailNumber)")

            if (config.attachmentEnabled && config.attachmentPath != null) {
                val multipart = MimeMultipart()

                val bodyPart = MimeBodyPart().apply {
                    setContent(
                        config.emailInlineContent,
                        if (config.contentType == "html") "text/html" else "text/plain"
                    )
                }

                val attachmentPart = MimeBodyPart().apply {
                    dataHandler = jakarta.activation.DataHandler(FileDataSource(config.attachmentPath))
                    fileName = FileDataSource(config.attachmentPath).name
                }

                multipart.addBodyPart(bodyPart)
                multipart.addBodyPart(attachmentPart)
                setContent(multipart)
            } else {
                setContent(config.emailInlineContent, if (config.contentType == "html") "text/html" else "text/plain")
            }

            if (config.connectionReuse) {
                Transport.send(this)
            } else {
                session.getTransport("smtp").use { transport ->
                    transport.connect()
                    transport.sendMessage(this, allRecipients)
                }
            }
        }
        println("Email #$emailNumber sent by thread ${Thread.currentThread().name}")
    }

    private fun incrementEmailCountForThread(threadName: String) {
        threadEmailCountHolder(threadName).incrementAndGet()
    }

    private fun threadEmailCountHolder(threadName: String): AtomicInteger =
        emailsSentByThread.computeIfAbsent(threadName) { AtomicInteger(0) }
}