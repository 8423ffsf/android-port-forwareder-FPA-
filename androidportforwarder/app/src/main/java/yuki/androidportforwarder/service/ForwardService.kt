package yuki.androidportforwarder.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
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
import java.io.IOException

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
        try {
            val serverSocket = ServerSocketChannel.open().apply {
                bind(InetSocketAddress("0.0.0.0", rule.localPort))
                configureBlocking(true)
            }

            try {
                while (true) {
                    val clientSocket = serverSocket.accept().apply {
                        configureBlocking(false)
                    }

                    serviceScope.launch(Dispatchers.IO) {
                        var targetSocket: SocketChannel? = null
                        try {
                            targetSocket = SocketChannel.open().apply {
                                connect(InetSocketAddress(
                                    InetAddress.getByName(rule.targetAddress),
                                    rule.targetPort
                                ))
                                configureBlocking(false)
                                while (!finishConnect()) {
                                    delay(50)
                                }
                            }

                            // 双向数据管道
                            listOf(
                                launch { forwardTcpData(clientSocket, targetSocket!!, "Inbound") },
                                launch { forwardTcpData(targetSocket!!, clientSocket, "Outbound") }
                            ).forEach { it.join() }

                        } catch (e: Exception) {
                            Log.e("TCP", "Forwarding error: ${e.stackTraceToString()}")
                        } finally {
                            clientSocket.close()
                            targetSocket?.close()
                        }
                    }
                }
            } finally {
                serverSocket.close()
            }
        } catch (e: IOException) {
            Log.e("TCP", "Port binding failed: ${e.message}")
        }
    }

    private suspend fun handleUdpForwarding(rule: ForwardRule) {
        try {
            val channel = DatagramChannel.open().apply {
                bind(InetSocketAddress("0.0.0.0", rule.localPort)) // 明确绑定到所有接口
                configureBlocking(true)
            }
            val buffer = ByteBuffer.allocate(4096)

            try {
                while (true) {  // 使用协程状态检查
                    buffer.clear()
                    val senderAddress = channel.receive(buffer) as? InetSocketAddress
                    senderAddress?.let {
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
        } catch (e: IOException) {
            Log.e("UDP", "Failed to bind port ${rule.localPort}: ${e.message}")
        }
    }

    private fun forwardTcpData(
        source: SocketChannel,
        dest: SocketChannel,
        direction: String
    ) {
        val buffer = ByteBuffer.allocate(8192)
        try {
            while (source.isOpen && dest.isOpen) {
                buffer.clear()
                val read = source.read(buffer)
                if (read == -1) break

                buffer.flip()
                while (buffer.hasRemaining()) {
                    dest.write(buffer)
                }
                Log.d("Forward", "$direction: ${buffer.position()} bytes")
            }
        } catch (e: Exception) {
            Log.e("Forward", "$direction error: ${e.message}")
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