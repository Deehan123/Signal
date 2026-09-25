package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioDao {
    // Packets/Messages Log Queries
    @Query("SELECT * FROM radio_packets ORDER BY timestamp DESC")
    fun getAllPackets(): Flow<List<RadioPacket>>

    @Query("SELECT * FROM radio_packets WHERE channel = :channel AND passphrase = :passphrase ORDER BY timestamp DESC")
    fun getPacketsByChannel(channel: String, passphrase: String): Flow<List<RadioPacket>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: RadioPacket): Long

    @Query("DELETE FROM radio_packets WHERE id = :id")
    suspend fun deletePacket(id: Int)

    @Query("DELETE FROM radio_packets")
    suspend fun clearAllPackets()

    // Discovered Peers Queries
    @Query("SELECT * FROM peer_nodes WHERE lastSeen > :activeThreshold ORDER BY lastSeen DESC")
    fun getActivePeers(activeThreshold: Long): Flow<List<PeerNode>>

    @Query("SELECT * FROM peer_nodes ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<PeerNode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePeer(peer: PeerNode)

    @Query("DELETE FROM peer_nodes WHERE macAddress = :macAddress")
    suspend fun deletePeer(macAddress: String)

    @Query("DELETE FROM peer_nodes WHERE lastSeen <= :inactiveTime")
    suspend fun clearInactivePeers(inactiveTime: Long)

    @Query("DELETE FROM peer_nodes")
    suspend fun clearAllPeers()
}
