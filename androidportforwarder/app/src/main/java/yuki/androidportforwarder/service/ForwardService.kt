package yuki.androidportforwarder.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import yuki.androidportforwarder.R
import yuki.androidportforwarder.data.model.ForwardRule
import yuki.androidportforwarder.data.repository.RuleRepository

class PortForwardService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val activeWorkers = mutableMapOf<String, Job>()
    private lateinit var ruleRepository: RuleRepository

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        ruleRepository = RuleRepository(this)
        createNotificationChannel()
        startForeground(1, createNotification())
        startAllRules()
    }

    private fun startAllRules() {
        ruleRepository.loadRules().filter { it.isActive }.forEach { startForwarding(it) }
    }

    private fun startForwarding(rule: ForwardRule) {
        val worker = serviceScope.launch(Dispatchers.IO) {
            try {
                if (rule.isUDP) handleUdpForwarding(rule) else handleTcpForwarding(rule)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeWorkers[rule.ruleName] = worker
    }

    private suspend fun handleTcpForwarding(rule: ForwardRule) {
        val serverSocket = ServerSocketChannel.open().apply {
            bind(InetSocketAddress(rule.localPort))
            configureBlocking(true)
        }

        try {
            while (true) {
                val clientSocket = serverSocket.accept()
                serviceScope.launch(Dispatchers.IO) {
                    var targetSocket: SocketChannel? = null
                    try {
                        targetSocket = SocketChannel.open(
                            InetSocketAddress(
                                InetAddress.getByName(rule.targetAddress),
                                rule.targetPort
                            )
                        )
                        forwardTcpData(clientSocket, targetSocket)
                    } catch (e: Exception) {
                        // 处理连接异常
                    } finally {
                        clientSocket.close()
                        targetSocket?.close()
                    }
                }
            }
        } finally {
            serverSocket.close()
        }
    }

    private suspend fun handleUdpForwarding(rule: ForwardRule) {
        val channel = DatagramChannel.open().apply {
            bind(InetSocketAddress(rule.localPort))
            configureBlocking(true)
        }
        val buffer = ByteBuffer.allocate(4096)

        try {
            while (true) {
                buffer.clear()
                val clientAddress = channel.receive(buffer) as? InetSocketAddress
                clientAddress?.let {
                    buffer.flip()
                    channel.send(buffer, InetSocketAddress(
                        InetAddress.getByName(rule.targetAddress),
                        rule.targetPort
                    ))
                }
            }
        } finally {
            channel.close()
        }
    }

    private fun forwardTcpData(source: SocketChannel, dest: SocketChannel) {
        val buffer = ByteBuffer.allocate(4096)
        try {
            while (source.isOpen && dest.isOpen) {
                buffer.clear()
                val bytesRead = source.read(buffer)
                if (bytesRead == -1) break

                buffer.flip()
                while (buffer.hasRemaining()) {
                    dest.write(buffer)
                }
            }
        } finally {
            source.close()
            dest.close()
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Port Forwarding Active")
            .setSmallIcon(R.drawable.ic_forward)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Forward Service",
                NotificationManager.IMPORTANCE_LOW
            ).also {
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(it)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ForwardServiceChannel"
    }
}