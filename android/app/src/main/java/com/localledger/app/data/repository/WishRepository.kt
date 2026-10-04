package com.localledger.app.data.repository

import androidx.room.withTransaction
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.toDomain
import com.localledger.app.data.db.toEntity
import com.localledger.app.domain.Wish
import com.localledger.app.domain.validateWish
import com.localledger.app.domain.wishTotals
import java.util.UUID
import kotlinx.coroutines.flow.map

class WishRepository(private val db: LedgerDatabase) {
    private val dao = db.wishDao()
    val wishes = dao.observeWishes().map { rows -> rows.map { it.toDomain() } }
    suspend fun wish(id: String): Wish? = dao.wish(id)?.takeUnless { it.isDeleted }?.toDomain()

    suspend fun save(id: String?, name: String, target: Long, saved: Long, note: String?) = db.withTransaction {
        val existing = id?.let { requireNotNull(wish(it)) { "心愿已不存在。" } }
        val now = System.currentTimeMillis()
        val item = Wish(existing?.id ?: UUID.randomUUID().toString(), name.trim(), target, saved,
            note?.trim()?.takeIf { it.isNotEmpty() }, existing?.isPurchased ?: false,
            existing?.createdAt ?: now, maxOf(now, existing?.updatedAt ?: now))
        saveValidated(item)
    }

    suspend fun setSaved(id: String, saved: Long) = change(id) { it.copy(savedMinor = saved) }
    suspend fun setPurchased(id: String, purchased: Boolean) = change(id) { it.copy(isPurchased = purchased) }
    suspend fun delete(id: String) = change(id) { it.copy(isDeleted = true) }
    private suspend fun change(id: String, block: (Wish) -> Wish) = db.withTransaction {
        val existing = requireNotNull(wish(id)) { "心愿已不存在。" }
        saveValidated(block(existing).copy(updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt)))
    }
    private suspend fun saveValidated(item: Wish) {
        validateWish(item)
        wishTotals(dao.allWishes().filter { it.id != item.id }.map { it.toDomain() } + item)
        dao.save(item.toEntity())
    }
}
