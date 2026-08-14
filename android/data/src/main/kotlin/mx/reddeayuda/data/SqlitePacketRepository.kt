package mx.reddeayuda.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import mx.reddeayuda.core.PacketRepository
import mx.reddeayuda.core.QueuedPacket
import mx.reddeayuda.protocol.EmergencyPacket
import mx.reddeayuda.protocol.EmergencyPacketCodec
import mx.reddeayuda.protocol.Hex

class SqlitePacketRepository(context: Context) : PacketRepository {
    private val db = Helper(context).writableDatabase

    override fun insert(packet: EmergencyPacket, firstSeenAt: Long) {
        val bytes = EmergencyPacketCodec.encode(packet)
        db.execSQL(
            "INSERT OR IGNORE INTO packets(message_id, blob, first_seen) VALUES(?,?,?)",
            arrayOf(packet.messageIdHex(), bytes, firstSeenAt)
        )
    }

    override fun getAll(): List<QueuedPacket> {
        val out = mutableListOf<QueuedPacket>()
        db.rawQuery("SELECT blob, first_seen FROM packets ORDER BY first_seen ASC", null).use { c ->
            while (c.moveToNext()) {
                val blob = c.getBlob(0)
                val seen = c.getLong(1)
                out += QueuedPacket(EmergencyPacketCodec.decode(blob), seen)
            }
        }
        return out
    }

    override fun delete(messageId: ByteArray) {
        db.execSQL("DELETE FROM packets WHERE message_id=?", arrayOf(Hex.encode(messageId)))
    }

    override fun size(): Int {
        db.rawQuery("SELECT COUNT(*) FROM packets", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    override fun findByMessageId(messageId: ByteArray): QueuedPacket? {
        db.rawQuery(
            "SELECT blob, first_seen FROM packets WHERE message_id=?",
            arrayOf(Hex.encode(messageId))
        ).use { c ->
            if (!c.moveToFirst()) return null
            return QueuedPacket(EmergencyPacketCodec.decode(c.getBlob(0)), c.getLong(1))
        }
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "rda_packets.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE packets(
                    message_id TEXT PRIMARY KEY,
                    blob BLOB NOT NULL,
                    first_seen INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                """CREATE TABLE seen(
                    message_id TEXT PRIMARY KEY,
                    seen_at INTEGER NOT NULL
                )"""
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS packets")
            db.execSQL("DROP TABLE IF EXISTS seen")
            onCreate(db)
        }
    }
}
