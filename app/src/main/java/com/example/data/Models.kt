package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_packets")
data class RadioPacket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderName: String,
    val messageType: String, // "VOICE", "SOS", "TEXT", "PING"
    val content: String, // String message, or absolute audio file path
    val timestamp: Long = System.currentTimeMillis(),
    val channel: String, // Frequency string (e.g., "144.200")
    val passphrase: String = "", // Empty means open public/emergency channel
    val rssi: Int = -70, // -30 is strong, -100 is very weak
    val relativeX: Float = 0f, // Radar horizontal coordinate offset
    val relativeY: Float = 0f, // Radar vertical coordinate offset
    val isRelayed: Boolean = false,
    val relayChain: String = "" // Relayed peer sequence (e.g. "Alpha -> Beta -> Me")
)

@Entity(tableName = "peer_nodes")
data class PeerNode(
    @PrimaryKey val macAddress: String,
    val displayName: String,
    val relativeX: Float, // current visual X on radar
    val relativeY: Float, // current visual Y on radar
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val isEmergency: Boolean = false,
    val batteryLevel: Int = 100
)
