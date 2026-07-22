package com.sherlock.bot

import android.app.Application
import com.sherlock.bot.data.CatalogRepository
import com.sherlock.bot.data.DefDirectory
import com.sherlock.bot.data.OsintCatalog
import com.sherlock.bot.data.SherlockAppAssets

class SherlockApp : Application() {
    lateinit var catalogRepository: CatalogRepository
        private set

    override fun onCreate() {
        super.onCreate()
        catalogRepository = CatalogRepository(this)
        catalogRepository.loadIntoMemory()
        loadDefDirectory()
    }

    private fun loadDefDirectory() {
        runCatching {
            assets.open(SherlockAppAssets.DEF).bufferedReader().use { reader ->
                DefDirectory.loadFromJson(reader.readText())
            }
        }.onFailure {
            DefDirectory.loadFromJson(DefDirectory.FALLBACK_JSON)
        }
    }

    companion object {
        const val CATALOG_ASSET = SherlockAppAssets.CATALOG
        const val DEF_ASSET = SherlockAppAssets.DEF
    }
}
