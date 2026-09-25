package com.example.data

import kotlinx.coroutines.flow.Flow

class RadioRepository(private val radioDao: RadioDao) {

    val allPackets: Flow<List<RadioPacket>> = radioDao.getAllPackets()

    fun getPacketsByChannel(channel: String, passphrase: String): Flow<List<RadioPacket>> {
        return radioDao.getPacketsByChannel(channel, passphrase)
    }

    fun getActivePeers(thresholdMs: Long = 30000): Flow<List<PeerNode>> {
        val cutoff = System.currentTimeMillis() - thresholdMs
        return radioDao.getActivePeers(cutoff)
    }

    val allPeers: Flow<List<PeerNode>> = radioDao.getAllPeers()

    suspend fun insertPacket(packet: RadioPacket): Long {
        return radioDao.insertPacket(packet)
    }

    suspend fun insertOrUpdatePeer(peer: PeerNode) {
        radioDao.insertOrUpdatePeer(peer)
    }

    suspend fun deletePeer(mac: String) {
        radioDao.deletePeer(mac)
    }

    suspend fun cleanInactivePeers(thresholdMs: Long = 30000) {
        val cutoff = System.currentTimeMillis() - thresholdMs
        val dummy = radioDao.clearInactivePeers(cutoff)
    }

    suspend fun clearPackets() {
        radioDao.clearAllPackets()
    }

    suspend fun clearPeers() {
        radioDao.clearAllPeers()
    }
}
