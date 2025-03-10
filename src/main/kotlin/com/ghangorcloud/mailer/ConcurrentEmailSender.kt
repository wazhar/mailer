package com.ghangorcloud.mailer

import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
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
    private val totalEmailsToSend = config.msgPerThread * config.workerThreads

    private val session: Session = Session.getInstance(Properties().apply {
        put("mail.smtp.host", config.smtpHost)
        put("mail.smtp.port", "${config.smtpPort}")
        put("mail.smtp.auth", "${config.smtpAuthEnabled}")
        put("mail.smtp.starttls.enable", "${config.smtpStartTlsEnabled}")
        put("mail.smtp.connectiontimeout", "${config.smtpConnectionTimeoutMillis}")
        put("mail.smtp.timeout", "${config.smtpReadTimeoutMillis}")

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

        if (config.connectionReuse) {
            session.getTransport("smtp").use { transport ->
                transport.connect()
                repeat(config.msgPerThread) {
                    val currentGlobalCount = globalEmailCounter.incrementAndGet()
                    if (currentEmailNumberExceedsGlobalTotal(currentGlobalCount)) return@repeat

                    try {
                        sendSingleEmail(currentGlobalCount, transport)
                        incrementEmailCountForThread(threadName)
                    } catch (ex: Exception) {
                        println("Error sending email #$currentGlobalCount from thread $threadName: ${ex.message}")
                    }
                }
            }
        } else {
            repeat(config.msgPerThread) {
                val currentGlobalCount = globalEmailCounter.incrementAndGet()
                if (currentEmailNumberExceedsGlobalTotal(currentGlobalCount)) return@repeat

                try {
                    sendSingleEmail(currentGlobalCount, null)
                } catch (e: Exception) {
                    println("Error sending email #$currentGlobalCount from thread $threadName: ${e.message}")
                }
            }
        }
    }

    private fun currentGlobalCount() = globalEmailCounter.get()

    private fun currentEmailNumberExceedsGlobalTotal(currentGlobalCount: Int): Boolean =
        currentGlobalCount > totalEmailsToSend

    private fun sendSingleEmail(emailNumber: Int, transport: Transport?) {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.smtpFromAddress))
            config.recipientsTo.forEach { addRecipient(Message.RecipientType.TO, InternetAddress(it.trim())) }
            config.recipientsCc?.forEach { addRecipient(Message.RecipientType.CC, InternetAddress(it.trim())) }
            config.recipientsBcc?.forEach { addRecipient(Message.RecipientType.BCC, InternetAddress(it.trim())) }
            setSubject("${config.emailSubject} (#$emailNumber)")

            if (config.attachmentEnabled && config.attachmentPath != null) {
                val multipart = MimeMultipart().apply {
                    addBodyPart(MimeBodyPart().apply {
                        setContent(
                            config.emailInlineContent,
                            if (config.contentType == "html") "text/html" else "text/plain"
                        )
                    })
                    addBodyPart(MimeBodyPart().apply {
                        dataHandler = DataHandler(FileDataSource(config.attachmentPath))
                        fileName = FileDataSource(config.attachmentPath).name
                    })
                }
                setContent(multipart)
            } else {
                setContent(config.emailInlineContent, if (config.contentType == "html") "text/html" else "text/plain")
            }
        }

        transport?.sendMessage(message, message.allRecipients) ?: Transport.send(message)

        println("Successfully sent email #$emailNumber from thread ${Thread.currentThread().name}")
    }

    private fun incrementEmailCountForThread(threadName: String) {
        threadEmailCountHolder(threadName).incrementAndGet()
    }

    private fun threadEmailCountHolder(threadName: String): AtomicInteger =
        emailsSentByThread.computeIfAbsent(threadName) { AtomicInteger(0) }
}